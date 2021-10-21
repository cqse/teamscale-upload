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
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.TimeoutException;

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
     * The maximum number of seconds to wait for a process or thread to terminate.
     */
    public static final int TIMEOUT_SECONDS = 120;

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
    public static final String CONVERTED_REPORT_FILE_EXTENSION = ".xccov";

    private final File workingDirectory;

    private ExecutorService executorService;

    public XCResultConverter(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Converts the report and writes the conversion result to a file with the same path and the
     * {@link #CONVERTED_REPORT_FILE_EXTENSION} as an added file extension.
     */
    public List<ConvertedReport> convert(File report) throws ConversionException {
        try {
            validateCommandLineTools();

            File reportDirectory = getReportDirectory(report);
            ActionsInvocationRecord actionsInvocationRecord = getActionsInvocationRecord(reportDirectory);
            List<ConvertedReport> convertedReports = new ArrayList<>();

            convertedReports.add(new ConvertedReport(TESTWISE_COVERAGE_REPORT_FORMAT,
                    extractTestResults(report, reportDirectory, actionsInvocationRecord)));

            if (actionsInvocationRecord.hasCoverageData()) {
                convertedReports.add(new ConvertedReport(XCODE_REPORT_FORMAT,
                        extractCoverageData(report, reportDirectory)));
            }

            return convertedReports;
        } catch (IOException e) {
            LogUtils.warn(
                    String.format("Error while converting report %s: %s", report.getAbsolutePath(), e.getMessage()));
            return Collections.emptyList();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ConversionException(
                    String.format("Error while converting report %s: %s", report.getAbsolutePath(), e.getMessage()), e);
        }
    }

    private ActionsInvocationRecord getActionsInvocationRecord(File reportDirectory)
            throws IOException, InterruptedException {
        String actionsInvocationRecordJson = ProcessUtils.executeProcessAndGetOutput(TIMEOUT_SECONDS,
                "xcrun", "xcresulttool", "get", "--path", reportDirectory.getAbsolutePath(), "--format", "json");
        return new Gson().fromJson(actionsInvocationRecordJson, ActionsInvocationRecord.class);
    }

    private File extractTestResults(File report, File reportDirectory, ActionsInvocationRecord actionsInvocationRecord)
            throws IOException, InterruptedException {
        List<TestInfo> tests = new ArrayList<>();

        for (ActionRecord action : actionsInvocationRecord.actions) {
            Reference testsRef = action.actionResult.testsRef;
            if (testsRef == null) {
                continue;
            }

            String json = ProcessUtils
                    .executeProcessAndGetOutput(TIMEOUT_SECONDS, "xcrun", "xcresulttool", "get", "--path",
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

        File testwiseCoverageReportFile = new File(report.getAbsolutePath() + ".testwisecoverage.json");

        tests.sort(Comparator.comparing(testInfo -> testInfo.uniformPath));
        TestwiseCoverageReport testwiseCoverageReport = new TestwiseCoverageReport(tests);

        FileUtils.write(testwiseCoverageReportFile, new Gson().toJson(testwiseCoverageReport), StandardCharsets.UTF_8);

        return testwiseCoverageReportFile;
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

    private File extractCoverageData(File report, File reportDirectory)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        List<String> sourceFiles = getSourceFiles(reportDirectory);

        LogUtils.info(
                String.format("Converting %d source files using %d threads in XCResult bundle %s.", sourceFiles.size(),
                        CONVERSION_THREAD_COUNT, report.getAbsolutePath()));

        Queue<Future<ConversionResult>> conversionResults = submitConversionTasks(reportDirectory, sourceFiles);
        File convertedCoverageReport = new File(report.getAbsolutePath() + CONVERTED_REPORT_FILE_EXTENSION);

        writeConversionResults(conversionResults, convertedCoverageReport);
        waitForRunningProcessesToFinish();

        return convertedCoverageReport;
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
            if (!executorService.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
     * Returns the XCResult bundle directory for the report. In case the report is a Tar archive the archive is
     * extracted to a temporary XCResult bundle directory that is returned.
     */
    private File getReportDirectory(File report) throws IOException, ConversionException {
        String reportFileName = report.getName();

        if (report.isDirectory() && reportFileName.endsWith(".xcresult")) {
            return report;
        }
        if (report.isFile() &&
                (reportFileName.endsWith(".xcresult.tar") || reportFileName.endsWith(".xcresult.tar.gz")) ||
                reportFileName.endsWith(".xcresult.tgz")) {
            String reportDirectoryName = reportFileName + "_extracted.xcresult";
            File temporaryReportDirectory = new File(workingDirectory, reportDirectoryName);

            FileSystemUtils.extractTarArchive(report, temporaryReportDirectory);

            return temporaryReportDirectory;
        }

        throw new ConversionException(
                "Report location must be an existing directory with a name that ends with '.xcresult' or a file " +
                        "that ends with '.xcresult.tar', '.xcresult.tar.gz' or '.xcresult.tgz': "
                        + report.getAbsolutePath());
    }

    /**
     * Empties the queue to free memory and writes the {@link ConversionResult} to the converted report file.
     */
    private static void writeConversionResults(Queue<Future<ConversionResult>> conversionResults, File convertedReport)
            throws InterruptedException, ExecutionException, TimeoutException, IOException {
        ConversionProgressTracker conversionProgressTracker =
                new ConversionProgressTracker(System.currentTimeMillis(), conversionResults.size());

        if (convertedReport.exists() && !convertedReport.delete()) {
            LogUtils.fail("Unable to delete existing converted report: " + convertedReport);
        }

        while (!conversionResults.isEmpty()) {
            ConversionResult conversionResult = conversionResults.remove().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (conversionResult == null) {
                // Can happen when the application is forcefully quit or a timeout occurs
                continue;
            }

            String sourceFileHeader = conversionResult.sourceFile + System.lineSeparator();

            Files.writeString(convertedReport.toPath(), sourceFileHeader, StandardOpenOption.APPEND,
                    StandardOpenOption.CREATE);
            Files.write(convertedReport.toPath(), conversionResult.result, StandardOpenOption.APPEND,
                    StandardOpenOption.CREATE);

            conversionProgressTracker.reportProgress();
        }
    }

    /**
     * Returns a sorted list of source files contained in the XCResult bundle directory.
     */
    private static List<String> getSourceFiles(File reportDirectory) throws IOException, InterruptedException {
        String output = ProcessUtils.executeProcessAndGetOutput(TIMEOUT_SECONDS,
                "xcrun", "xccov", "view", "--archive", "--file-list", reportDirectory.getAbsolutePath());
        return output.lines().sorted().collect(toList());
    }

    private static void validateCommandLineTools() throws IOException, InterruptedException, ConversionException {
        if (ProcessUtils.executeProcess("xcrun", "--version").waitFor() != 0) {
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
