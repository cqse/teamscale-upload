package com.teamscale.upload.xcode;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.autodetect_revision.ProcessUtils.ProcessResult;
import com.teamscale.upload.report.xcode.ActionRecord;
import com.teamscale.upload.report.xcode.ActionsInvocationRecord;
import com.teamscale.upload.utils.FileSystemUtils;
import com.teamscale.upload.utils.LogUtils;

/**
 * Converts XCResult bundles to a human readable report format that can be
 * uploaded to Teamscale.
 */
public class XCResultConverter {

	/**
	 * The enum name of the XCode report format.
	 */
	public static final String XCODE_REPORT_FORMAT = "XCODE";

	/**
	 * The number of conversion threads to run in parallel for faster conversion. By
	 * default, we use the number of available processors to distribute work since
	 * this setting was most performant when testing locally.
	 */
	private static final int CONVERSION_THREAD_COUNT = Integer.getInteger(
			"com.teamscale.upload.xcode.conversion-thread-count", Runtime.getRuntime().availableProcessors());

	/**
	 * File extension used for converted XCResult bundles.
	 */
	private static final String XCCOV_REPORT_FILE_EXTENSION = ".xccov";

	/**
	 * File extension used for xccov archives
	 *
	 * @see #isXccovArchive(File)
	 */
	private static final String XCCOV_ARCHIVE_FILE_EXTENSION = ".xccovarchive";

	private final File workingDirectory;

	private ExecutorService executorService;

	public XCResultConverter(File workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	/**
	 * Converts a single XCode report from the internal binary XCode format to a
	 * readable report that can be uploaded to Teamscale.
	 */
	public static List<ConvertedReport> convertReport(File xcodeReport) throws ConversionException {
		if (!XCResultConverter.needsConversion(xcodeReport)) {
			return Collections.singletonList(new ConvertedReport(XCODE_REPORT_FORMAT, xcodeReport));
		}

		File workingDirectory = createTemporaryWorkingDirectory();
		XCResultConverter converter = new XCResultConverter(workingDirectory);
		Thread cleanupShutdownHook = createCleanupShutdownHook(workingDirectory, converter);
		try {
			Runtime.getRuntime().addShutdownHook(cleanupShutdownHook);

			return converter.doConvert(xcodeReport);
		} finally {
			// TODO: This does not make sense to me. how are we supposed to access the
			// reports afterwards?
			deleteWorkingDirectory(workingDirectory);
			Runtime.getRuntime().removeShutdownHook(cleanupShutdownHook);
		}
	}

	/**
	 * Returns true if XCode report requires conversion.
	 */
	public static boolean needsConversion(File report) {
		return FileSystemUtils.isTarFile(report) || isXcresultBundle(report) || isXccovArchive(report);
	}

	/**
	 * Returns true if the file is a regular XCResult bundle directory indicated by
	 * the ".xcresult" ending in the directory name.
	 */
	private static boolean isXcresultBundle(File file) {
		return file.isDirectory() && file.getName().endsWith(".xcresult");
	}

	/**
	 * Returns true if the file is an xccov archive which is more compact than a
	 * regular XCResult bundle. An xccov archive can only be generated by XCode
	 * internal tooling but provides much better performance when extracting
	 * coverage. Note that xccov archives don't contain test results.
	 */
	private static boolean isXccovArchive(File file) {
		return file.isDirectory() && file.getName().endsWith(XCCOV_ARCHIVE_FILE_EXTENSION);
	}

	/**
	 * Creates a {@linkplain Runtime#addShutdownHook(Thread) shutdown hook} that
	 * ensures that temporarily created files and directories are deleted if the
	 * user terminates the application forcefully (e.g. via Ctrl+C).
	 */
	private static Thread createCleanupShutdownHook(File workingDirectory, XCResultConverter converter) {
		return new Thread(() -> {
			try {
				converter.stopProcesses();
			} catch (ConversionException e) {
				LogUtils.warn(e.getMessage());
			}
			deleteWorkingDirectory(workingDirectory);
		});
	}

	private static void deleteWorkingDirectory(File workingDirectory) {
		try {
			FileUtils.deleteDirectory(workingDirectory);
		} catch (IOException e) {
			LogUtils.warn("Unable to delete temporary working directory " + workingDirectory.getAbsolutePath() + ": "
					+ e.getMessage());
		}
	}

	private static File createTemporaryWorkingDirectory() throws ConversionException {
		try {
			return Files.createTempDirectory(null).toFile();
		} catch (IOException e) {
			throw new ConversionException(
					"Error occurred when trying to create temporary working directory:" + e.getMessage(), e);
		}
	}

	/**
	 * Empties the queue to free memory and writes the {@link ConversionResult} to
	 * the converted report file.
	 */
	private static void writeConversionResults(Queue<Future<ConversionResult>> conversionResults, File convertedReport)
			throws InterruptedException, ExecutionException, IOException {
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
	 * Returns a sorted list of source files contained in the XCResult bundle
	 * directory.
	 */
	private static List<String> getSourceFiles(File reportDirectory) throws ConversionException {
		ProcessResult result = ProcessUtils.run("xcrun", "xccov", "view", "--archive", "--file-list",
				reportDirectory.getAbsolutePath());
		if (!result.wasSuccessful()) {
			throw ConversionException.withProcessResult(
					"Error while obtaining file list from XCResult archive " + reportDirectory.getAbsolutePath(),
					result);
		}
		return result.output.lines().sorted().collect(toList());
	}

	private static void validateCommandLineTools() throws IOException, InterruptedException, ConversionException {
		if (!ProcessUtils.run("xcrun", "--version").wasSuccessful()) {
			throw new ConversionException(
					"XCode command line tools not installed. Install command line tools on MacOS by installing XCode "
							+ "from the store and running 'xcode-select --install'.");
		}
	}

	/**
	 * Converts the report and writes the conversion result to a file with the same
	 * path and the {@link #XCCOV_REPORT_FILE_EXTENSION} as an added file extension.
	 */
	private List<ConvertedReport> doConvert(File report) throws ConversionException {
		try {
			validateCommandLineTools();

			File reportDirectory = getReportDirectory(report);
			List<ConvertedReport> convertedReports = new ArrayList<>();

			if (isXccovArchive(reportDirectory)) {
				convertedReports.add(extractCoverageData(report, reportDirectory));
				return convertedReports;
			}

			ActionsInvocationRecord actionsInvocationRecord = getActionsInvocationRecord(reportDirectory);

			if (!actionsInvocationRecord.hasCoverageData()) {
				LogUtils.warn("XCResult bundle doesn't contain any coverage data.");
				return convertedReports;
			}

			for (File convertToXccovArchive : convertToXccovArchives(reportDirectory, actionsInvocationRecord)) {
				convertedReports.add(extractCoverageData(report, convertToXccovArchive));
			}

			return convertedReports;
		} catch (IOException | InterruptedException | ExecutionException e) {
			throw new ConversionException(
					String.format("Error while converting report %s: %s", report.getAbsolutePath(), e.getMessage()), e);
		}
	}

	private List<File> convertToXccovArchives(File reportDirectory, ActionsInvocationRecord actionsInvocationRecord)
			throws IOException, ConversionException {
		List<File> xccovArchives = new ArrayList<>(actionsInvocationRecord.actions.length);

		for (int i = 0; i < actionsInvocationRecord.actions.length; i++) {
			ActionRecord action = actionsInvocationRecord.actions[i];
			if (action == null || action.actionResult == null || action.actionResult.coverage == null
					|| action.actionResult.coverage.archiveRef == null
					|| action.actionResult.coverage.archiveRef.id == null) {
				continue;
			}
			File tempDirectory = Files.createTempDirectory(workingDirectory.toPath(), null).toFile();
			File xccovArchive = new File(tempDirectory,
					reportDirectory.getName() + "." + i + XCCOV_ARCHIVE_FILE_EXTENSION);
			String archiveRef = action.actionResult.coverage.archiveRef.id;

			FileSystemUtils.mkdirs(xccovArchive.getParentFile());
			ProcessResult result = ProcessUtils.run("xcrun", "xcresulttool", "export", "--type", "directory", "--path",
					reportDirectory.getAbsolutePath(), "--id", archiveRef, "--output-path",
					xccovArchive.getAbsolutePath());

			// TODO: Check if this command may have failed
			if (!result.wasSuccessful()) {
				throw ConversionException
						.withProcessResult("Could not convert report to " + XCCOV_ARCHIVE_FILE_EXTENSION, result);
			}
			xccovArchives.add(xccovArchive);
		}

		return xccovArchives;
	}

	/**
	 * Returns the bundle directory for the report. In case the report is a Tar
	 * archive the archive is extracted to a temporary bundle directory that is
	 * returned.
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
				"Report location must be an existing directory with a name that ends with '.xcresult' or "
						+ "'.xccovarchive'. The directory may be contained in a tar archive indicated by the file "
						+ "extensions '.tar', '.tar.gz' or '.tgz'." + report);
	}

	private ActionsInvocationRecord getActionsInvocationRecord(File reportDirectory)
			throws InterruptedException, ConversionException {
		List<String> command = new ArrayList<>();
		Collections.addAll(command, "xcrun", "xcresulttool", "get", "--path", reportDirectory.getAbsolutePath(),
				"--format", "json");
		if (XcodeVersion.determine().major >= 16) {
			// Starting with Xcode 16 this command is marked as deprecated and will fail if
			// ran without the legacy flag
			// see TS-40724 for more information
			command.add("--legacy");
		}

		ProcessResult result = ProcessUtils.run(command.toArray(new String[0]));
		if (!result.wasSuccessful()) {
			throw ConversionException
					.withProcessResult("Error while obtaining ActionInvocationsRecord from XCResult archive "
							+ reportDirectory.getAbsolutePath(), result);
		}
		String actionsInvocationRecordJson = result.output;
		return new Gson().fromJson(actionsInvocationRecordJson, ActionsInvocationRecord.class);
	}

	private ConvertedReport extractCoverageData(File report, File reportDirectory)
			throws IOException, InterruptedException, ExecutionException, ConversionException {
		List<String> sourceFiles = getSourceFiles(reportDirectory);
		File convertedCoverageReport = new File(report.getAbsolutePath() + XCCOV_REPORT_FILE_EXTENSION);
		long startTime = System.currentTimeMillis();

		LogUtils.info(String.format("Converting XCResult bundle %s containing %d source files.",
				report.getAbsolutePath(), sourceFiles.size()));
		ProcessResult result = ProcessUtils.run("xcrun", "xccov", "view", "--archive",
				reportDirectory.getAbsolutePath());
		FileSystemUtils.ensureEmptyFile(convertedCoverageReport);

		/*
		 * With XCode 13.3 and newer the coverage of all source files can be exported
		 * with the command above which offers the best performance. We try this first
		 * and if it doesn't work we fall back to the slower legacy mechanism that
		 * iterates over each source file.
		 */
		if (result.wasSuccessful()) {
			Files.writeString(convertedCoverageReport.toPath(), result.output, StandardOpenOption.WRITE);
		} else {
			LogUtils.info(String.format("Using legacy conversion with %d threads.", CONVERSION_THREAD_COUNT));
			Queue<Future<ConversionResult>> conversionResults = submitConversionTasks(reportDirectory, sourceFiles);

			writeConversionResults(conversionResults, convertedCoverageReport);
			waitForRunningProcessesToFinish();
		}

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

	private void waitForRunningProcessesToFinish() throws ConversionException {
		if (executorService == null || executorService.isTerminated()) {
			return;
		}
		try {
			if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
				LogUtils.warn("Processes took too long to terminate. Forcing shutdown.");
				executorService.shutdownNow();
			}
		} catch (InterruptedException e) {
			throw new ConversionException(
					"Error occurred while waiting for conversion processes to finish: " + e.getMessage(), e);
		}
	}

	/**
	 * Stops the conversion processes and ensures that all processes have finished.
	 */
	public void stopProcesses() throws ConversionException {
		if (executorService == null || executorService.isTerminated()) {
			return;
		}

		executorService.shutdownNow();
		waitForRunningProcessesToFinish();
	}

	/**
	 * Custom exception used to indicate errors during conversion.
	 */
	public static class ConversionException extends Exception {

		public ConversionException(String message) {
			super(message);
		}

		public ConversionException(String message, Exception e) {
			super(message, e);
		}

		/**
		 * Creates a {@link ConversionException} with the given message and the
		 * {@linkplain ProcessResult#errorOutput error output of the command}.
		 */
		public static ConversionException withProcessResult(String message, ProcessResult processResult) {
			String messageIncludingErrorOutput = message;
			if (processResult.errorOutput != null) {
				messageIncludingErrorOutput += " (command output: " + processResult.errorOutput + ")";
			}
			return new ConversionException(messageIncludingErrorOutput, processResult.exception);
		}
	}
}
