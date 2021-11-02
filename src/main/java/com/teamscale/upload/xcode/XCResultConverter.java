package com.teamscale.upload.xcode;

import com.google.gson.Gson;
import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.report.testwise_coverage.TestInfo;
import com.teamscale.upload.report.testwise_coverage.TestwiseCoverageReport;
import com.teamscale.upload.report.xcode.ActionRecord;
import com.teamscale.upload.report.xcode.ActionTest;
import com.teamscale.upload.report.xcode.ActionTestPlanRunSummaries;
import com.teamscale.upload.report.xcode.ActionTestPlanRunSummary;
import com.teamscale.upload.report.xcode.ActionTestableSummary;
import com.teamscale.upload.report.xcode.ActionsInvocationRecord;
import com.teamscale.upload.report.xcode.Reference;
import com.teamscale.upload.utils.FileSystemUtils;
import com.teamscale.upload.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

/**
 * Converts XCResult bundles to a human readable report format that can be uploaded to Teamscale.
 */
public class XCResultConverter {

    /**
     * The enum name of the XCode report format.
     */
    public static final String XCODE_REPORT_FORMAT = "XCODE";

    /**
     * The enum name of the Testwise Coverage report format.
     */
    private static final String TESTWISE_COVERAGE_REPORT_FORMAT = "TESTWISE_COVERAGE";

    /**
     * The number of conversion threads to run in parallel for faster conversion. By default, we use
     * the number of available processors to distribute work since this setting was most performant
     * when testing locally.
     */
    public static final int CONVERSION_THREAD_COUNT =
            Integer.getInteger("com.teamscale.upload.xcode.conversion-thread-count",
                    Runtime.getRuntime().availableProcessors());

    /**
     * File extension used for converted XCResult bundles.
     */
    public static final String XCCOV_REPORT_FILE_EXTENSION = ".xccov";

    /**
     * File extension used for converted XCResult bundles.
     */
    public static final String TESTWISE_COVERAGE_REPORT_FILE_EXTENSION = ".testwisecoverage.json";

    private final File workingDirectory;

    private ExecutorService executorService;

    public XCResultConverter(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Converts the report and writes the conversion result to a file with the same path and the
     * {@link #XCCOV_REPORT_FILE_EXTENSION} or {@link #TESTWISE_COVERAGE_REPORT_FILE_EXTENSION}
     * as an added file extension.
     */
    public List<ConvertedReport> convert(File report) throws ConversionException {
        try {
            validateCommandLineTools();

            File reportDirectory = getReportDirectory(report);
            List<ConvertedReport> convertedReports = new ArrayList<>();

            if (isXccovArchive(reportDirectory)) {
                convertedReports.add(extractCoverageData(report, reportDirectory));
            } else {
                ActionsInvocationRecord actionsInvocationRecord = getActionsInvocationRecord(reportDirectory);
                convertedReports.add(extractTestResults(report, reportDirectory, actionsInvocationRecord));

                if (actionsInvocationRecord.hasCoverageData()) {
                    for (File convertToXccovArchive : convertToXccovArchives(reportDirectory,
                            actionsInvocationRecord)) {
                        convertedReports.add(extractCoverageData(report, convertToXccovArchive));
                    }
                }
            }

            return convertedReports;
        } catch (IOException e) {
            LogUtils.warn(
                    String.format("Error while converting report %s: %s", report.getAbsolutePath(), e.getMessage()));
            return Collections.emptyList();
        } catch (InterruptedException | ExecutionException e) {
            throw new ConversionException(
                    String.format("Error while converting report %s: %s", report.getAbsolutePath(), e.getMessage()), e);
        }
    }

    private List<File> convertToXccovArchives(File reportDirectory, ActionsInvocationRecord actionsInvocationRecord)
            throws IOException, InterruptedException {
        List<File> xccovArchives = new ArrayList<>(actionsInvocationRecord.actions.length);

        for (int i = 0; i < actionsInvocationRecord.actions.length; i++) {
            ActionRecord action = actionsInvocationRecord.actions[i];
            File tempDirectory = Files.createTempDirectory(workingDirectory.toPath(), null).toFile();
            File xccovArchive = new File(tempDirectory, reportDirectory.getName() + "." + i + ".xccovarchive");
            String archiveRef = action.actionResult.coverage.archiveRef.id;

            FileSystemUtils.mkdirs(xccovArchive.getParentFile());
            ProcessUtils.executeProcess("xcrun", "xcresulttool", "export", "--type", "directory", "--path",
                    reportDirectory.getAbsolutePath(), "--id", archiveRef, "--output-path",
                    xccovArchive.getAbsolutePath());

            xccovArchives.add(xccovArchive);
        }

        return xccovArchives;
    }

    /**
     * Returns true if the file is a regular XCResult bundle directory indicated by the ".xcresult"
     * ending in the directory name.
     */
    private static boolean isXcresultBundle(File file) {
        return file.isDirectory() && file.getName().endsWith(".xcresult");
    }

    /**
     * Returns true if the file is an xccov archive which is more compact than a regular XCResult bundle.
     * An xccov archive can only be generated by XCode internal tooling but provides much better performance
     * when extracting coverage. Note that xccov archives don't contain test results.
     */
    private static boolean isXccovArchive(File file) {
        return file.isDirectory() && file.getName().endsWith(".xccovarchive");
    }

    /**
     * Returns true if XCode report requires conversion.
     */
    public static boolean needsConversion(File report) {
        return FileSystemUtils.isTarFile(report) || isXcresultBundle(report) || isXccovArchive(report);
    }

    /**
     * Returns the bundle directory for the report. In case the report is a Tar archive the archive is
     * extracted to a temporary bundle directory that is returned.
     */
    private File getReportDirectory(File report) throws IOException, ConversionException {
        File reportDirectory = report;
        String reportDirectoryName = FileSystemUtils.stripTarExtension(report.getName());

        if (FileSystemUtils.isTarFile(report)) {
            reportDirectory = new File(workingDirectory, reportDirectoryName);
            FileSystemUtils.extractTarArchive(report, reportDirectory);
        }
        if (isXccovArchive(reportDirectory) || isXcresultBundle(reportDirectory)) {
            return reportDirectory;
        }

        throw new ConversionException(
                "Report location must be an existing directory with a name that ends with '.xcresult' or " +
                        "'.xccovarchive'. The directory may be contained in a tar archive indicated by the file " +
                        "extensions '.tar', '.tar.gz' or '.tgz'."
                        + report);
    }

    private ActionsInvocationRecord getActionsInvocationRecord(File reportDirectory)
            throws IOException, InterruptedException {
        String actionsInvocationRecordJson = ProcessUtils.executeProcess(
                "xcrun", "xcresulttool", "get", "--path", reportDirectory.getAbsolutePath(), "--format", "json");
        return new Gson().fromJson(actionsInvocationRecordJson, ActionsInvocationRecord.class);
    }

    private ConvertedReport extractTestResults(File report, File reportDirectory, ActionsInvocationRecord actionsInvocationRecord)
            throws IOException, InterruptedException {
        List<TestInfo> tests = new ArrayList<>();

        for (ActionRecord action : actionsInvocationRecord.actions) {
            Reference testsRef = action.actionResult.testsRef;
            if (testsRef == null) {
                continue;
            }

            String json = ProcessUtils.executeProcess("xcrun", "xcresulttool", "get", "--path",
                    reportDirectory.getAbsolutePath(), "--format", "json", "--id", testsRef.id);
            ActionTestPlanRunSummaries actionTestPlanRunSummaries =
                    new Gson().fromJson(json, ActionTestPlanRunSummaries.class);

            for (ActionTestPlanRunSummary summary : actionTestPlanRunSummaries.summaries) {
                for (ActionTestableSummary testableSummary : summary.testableSummaries) {
                    for (ActionTest test : testableSummary.tests) {
                        extractTests(test, tests);
                    }
                }
            }
        }

        File testwiseCoverageReportFile = new File(report.getAbsolutePath() + TESTWISE_COVERAGE_REPORT_FILE_EXTENSION);

        tests.sort(Comparator.comparing(testInfo -> testInfo.uniformPath));
        TestwiseCoverageReport testwiseCoverageReport = new TestwiseCoverageReport(tests);

        FileSystemUtils.ensureEmptyFile(testwiseCoverageReportFile);
        Files.writeString(testwiseCoverageReportFile.toPath(), new Gson().toJson(testwiseCoverageReport));

        return new ConvertedReport(TESTWISE_COVERAGE_REPORT_FORMAT, testwiseCoverageReportFile);
    }

    private void extractTests(ActionTest actionTest, List<TestInfo> tests) {
        if (actionTest.subTests == null) {
            tests.add(new TestInfo(actionTest));
            return;
        }

        for (ActionTest subTest : actionTest.subTests) {
            extractTests(subTest, tests);
        }
    }

    private ConvertedReport extractCoverageData(File report, File reportDirectory)
            throws IOException, InterruptedException, ExecutionException {
        List<String> sourceFiles = getSourceFiles(reportDirectory);

        LogUtils.info(
                String.format("Extracting coverage for %d source files using %d threads from XCResult bundle %s.",
                        sourceFiles.size(),
                        CONVERSION_THREAD_COUNT, report.getAbsolutePath()));

        long startTime = System.currentTimeMillis();

        Queue<Future<ConversionResult>> conversionResults = submitConversionTasks(reportDirectory, sourceFiles);
        File convertedCoverageReport = new File(report.getAbsolutePath() + XCCOV_REPORT_FILE_EXTENSION);

        writeConversionResults(conversionResults, convertedCoverageReport);
        waitForRunningProcessesToFinish();

        LogUtils.info(String.format("Coverage extraction finished after %d seconds.",
                (System.currentTimeMillis() - startTime) / 1000));

        return new ConvertedReport(XCODE_REPORT_FORMAT, convertedCoverageReport);
    }

    private Queue<Future<ConversionResult>> submitConversionTasks(File reportDirectory, List<String> sourceFiles) {
        executorService = Executors.newFixedThreadPool(CONVERSION_THREAD_COUNT);

        Queue<Future<ConversionResult>> conversionResults = new LinkedList<>();
        for (String sourceFile : sourceFiles) {
            conversionResults.add(this.executorService.submit(new ConversionTask(reportDirectory, sourceFile)));
        }

        executorService.shutdown();

        return conversionResults;
    }

    private void waitForRunningProcessesToFinish() throws InterruptedException {
        if (executorService != null && !executorService.isTerminated()) {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                LogUtils.warn("Processes took too long to terminate. Forcing shutdown.");
                executorService.shutdownNow();
            }
        }
    }

    /**
     * Stops the conversion processes and ensures that all processes have finished.
     */
    public void stopProcesses() throws InterruptedException, IOException {
        if (executorService != null) {
            if (!executorService.isTerminated()) {
                executorService.shutdownNow();
                waitForRunningProcessesToFinish();
            }
        }
    }

    /**
     * Empties the queue to free memory and writes the {@link ConversionResult} to the converted report file.
     */
    private static void writeConversionResults(Queue<Future<ConversionResult>> conversionResults, File convertedReport)
            throws InterruptedException, ExecutionException, IOException {
        FileSystemUtils.ensureEmptyFile(convertedReport);

        while (!conversionResults.isEmpty()) {
            ConversionResult conversionResult = conversionResults.remove().get();
            if (conversionResult == null) {
                // Can happen when the application is forcefully quit or a timeout occurs
                continue;
            }

            String sourceFileHeader = conversionResult.sourceFile + System.lineSeparator();

            Files.writeString(convertedReport.toPath(), sourceFileHeader, StandardOpenOption.APPEND);
            Files.writeString(convertedReport.toPath(), conversionResult.result, StandardOpenOption.APPEND);
        }
    }

    /**
     * Returns a sorted list of source files contained in the XCResult bundle directory.
     */
    private static List<String> getSourceFiles(File reportDirectory) throws IOException, InterruptedException {
        String output = ProcessUtils.executeProcess(
                "xcrun", "xccov", "view", "--archive", "--file-list", reportDirectory.getAbsolutePath());
        return output.lines().sorted().collect(toList());
    }

    private static void validateCommandLineTools() throws IOException, InterruptedException, ConversionException {
        if (ProcessUtils.startProcess("xcrun", "--version").waitFor() != 0) {
            throw new ConversionException(
                    "XCode command line tools not installed. Install command line tools on MacOS by installing XCode " +
                            "from the store and running 'xcode-select --install'.");
        }
    }

    /**
     * Custom exception used to indicate errors during conversion.
     */
    public static class ConversionException extends Exception {

        private ConversionException(String message) {
            super(message);
        }

        private ConversionException(String message, Exception e) {
            super(message, e);
        }
    }
}
