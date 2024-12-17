package com.teamscale.upload.xcode;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.LogUtils;

/**
 * Converts an {@value ConversionUtils#XCCOV_ARCHIVE_FILE_EXTENSION} file into
 * the {@value ConversionUtils#XCCOV_REPORT_FILE_EXTENSION} format.
 */
/* package */ class XccovArchiveConverter extends ConverterBase<File> {

	public XccovArchiveConverter(XCodeVersion xcodeVersion, Path workingDirectory) {
		super(xcodeVersion, workingDirectory);
	}

	@Override
	public File convert(File xccovArchive) throws ConversionException, IOException {
		List<String> sourceFiles = getSourceFiles(xccovArchive);
		long startTime = System.currentTimeMillis();

		LogUtils.info(String.format("Converting XCResult bundle %s containing %d source files.",
				xccovArchive.getAbsolutePath(), sourceFiles.size()));
		ProcessUtils.ProcessResult result = ProcessUtils.run("xcrun", "xccov", "view", "--archive",
				xccovArchive.getAbsolutePath());

		/*
		 * With XCode 13.3 and newer the coverage of all source files can be exported
		 * with the command above which offers the best performance. We try this first
		 * and if it doesn't work we fall back to the slower legacy mechanism that
		 * iterates over each source file.
		 */
		File outputFile;
		if (result.wasSuccessful()) {
			// TODO: Bug occurs here
			outputFile = createOutputFile(xccovArchive.getName() + ConversionUtils.XCCOV_REPORT_FILE_EXTENSION);
			Files.writeString(outputFile.toPath(), result.output, StandardOpenOption.WRITE);
		} else {
			outputFile = new LegacyConverter(getXcodeVersion(), getWorkingDirectory(), sourceFiles)
					.convert(xccovArchive);
		}

		LogUtils.info(String.format("Coverage extraction finished after %d seconds.",
				(System.currentTimeMillis() - startTime) / 1000));

		return outputFile;
	}

	/**
	 * Returns a sorted list of source files contained in the XCResult bundle
	 * directory.
	 */
	private static List<String> getSourceFiles(File reportDirectory) throws ConversionException {
		ProcessUtils.ProcessResult result = ProcessUtils.run("xcrun", "xccov", "view", "--archive", "--file-list",
				reportDirectory.getAbsolutePath());
		if (!result.wasSuccessful()) {
			throw ConversionException.withProcessResult(
					"Error while obtaining file list from XCResult archive " + reportDirectory.getAbsolutePath(),
					result);
		}
		return result.output.lines().sorted().collect(toList());
	}

	/**
	 * Legacy way of converting
	 * {@linkplain ConversionUtils#XCCOV_ARCHIVE_FILE_EXTENSION xccov archives}.
	 */
	private static class LegacyConverter extends ConverterBase<File> {
		/**
		 * The number of conversion threads to run in parallel for faster conversion. By
		 * default, we use the number of available processors to distribute work since
		 * this setting was most performant when testing locally.
		 */
		private static final int CONVERSION_THREAD_COUNT = Integer.getInteger(
				"com.teamscale.upload.xcode.conversion-thread-count", Runtime.getRuntime().availableProcessors());
		private ExecutorService executorService;

		// TODO: This is bad
		private final List<String> sourceFiles;

		private LegacyConverter(XCodeVersion xcodeVersion, Path workingDirectory, List<String> sourceFiles) {
			super(xcodeVersion, workingDirectory);
			this.sourceFiles = sourceFiles;
		}

		@Override
		public File convert(File file) throws ConversionException, IOException {
			executorService = Executors.newFixedThreadPool(CONVERSION_THREAD_COUNT);
			Thread cleanupShutdownHook = new Thread(this::teardown);
			try {
				Runtime.getRuntime().addShutdownHook(cleanupShutdownHook);

				return doConvert(file);
			} finally {
				Runtime.getRuntime().removeShutdownHook(cleanupShutdownHook);
				teardown();
			}
		}

		private File doConvert(File xccovArchive) throws ConversionException, IOException {
			LogUtils.info(String.format("Using legacy conversion with %d threads.", CONVERSION_THREAD_COUNT));
			Queue<Future<ConversionResult>> conversionResults = submitConversionTasks(xccovArchive, sourceFiles);

			File outputFile = createOutputFile(xccovArchive.getName() + ConversionUtils.XCCOV_REPORT_FILE_EXTENSION);
			writeResultsToFile(conversionResults, outputFile);
			waitForExecutorServiceTermination();
			return outputFile;
		}

		private Queue<Future<ConversionResult>> submitConversionTasks(File reportDirectory, List<String> sourceFiles) {
			Queue<Future<ConversionResult>> conversionResults = new LinkedList<>();
			for (String sourceFile : sourceFiles) {
				conversionResults.add(this.executorService.submit(new ConversionTask(reportDirectory, sourceFile)));
			}
			// No further tasks to be queued
			executorService.shutdown();

			return conversionResults;
		}

		/**
		 * Empties the queue to free memory and writes the {@link ConversionResult} to
		 * the converted report file.
		 */
		private void writeResultsToFile(Queue<Future<ConversionResult>> conversionResults, File outputFile)
				throws ConversionException, IOException {
			while (!conversionResults.isEmpty()) {
				try {
					ConversionResult conversionResult = conversionResults.remove().get();

					if (conversionResult == null) {
						// Can happen when the application is forcefully quit or a timeout occurs
						continue;
					}

					String sourceFileHeader = conversionResult.sourceFile + System.lineSeparator();

					Files.writeString(outputFile.toPath(), sourceFileHeader, StandardOpenOption.APPEND);
					Files.writeString(outputFile.toPath(), conversionResult.result, StandardOpenOption.APPEND);
				} catch (InterruptedException | ExecutionException e) {
					throw new ConversionException("Exception occurred whilst waiting for conversions tasks to finish",
							e);
				}
			}
		}

		private void teardown() {
			if (executorService == null || executorService.isTerminated()) {
				return;
			}

			executorService.shutdownNow();
			waitForExecutorServiceTermination();
		}

		private void waitForExecutorServiceTermination() {
			if (executorService == null || executorService.isTerminated()) {
				return;
			}

			try {
				if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
					LogUtils.warn("Processes took too long to terminate. Forcing shutdown.");
					executorService.shutdownNow();
				}
			} catch (InterruptedException e) {
				LogUtils.warn(
						"Error occurred while waiting for conversion processes to be terminated: " + e.getMessage(), e);
			}
		}
	}
}
