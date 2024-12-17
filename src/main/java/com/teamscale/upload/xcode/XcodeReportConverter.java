package com.teamscale.upload.xcode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.FileSystemUtils;
import com.teamscale.upload.utils.LogUtils;

/**
 * Converts an Xcode report into the
 * {@value ConversionUtils#XCCOV_REPORT_FILE_EXTENSION} format.
 */
public class XcodeReportConverter extends ConverterBase<List<File>> {

	/**
	 * The enum name of the XCode report format.
	 */
	public static final String XCODE_REPORT_FORMAT = "XCODE";

	private XcodeReportConverter(XcodeVersion xcodeVersion, Path workingDirectory) {
		super(xcodeVersion, workingDirectory);
	}

	/**
	 * Converts XCResult bundles to a human-readable report format that can be
	 * uploaded to Teamscale.
	 */
	public static List<File> convert(Collection<File> xcodeReports) throws ConversionException {
		try {
			// Create a temporary directory that may be used by any other converter
			Path workingDirectory = Files.createTempDirectory("teamscale-upload");
			return ConversionUtils.runWithTeardown(() -> convert(xcodeReports, workingDirectory),
					() -> deleteWorkingDirectory(workingDirectory));
		} catch (IOException e) {
			throw new ConversionException("I/O error occurred while converting the reports", e);
		}
	}

	/** @see #convert(Collection) */
	private static List<File> convert(Collection<File> xcodeReports, Path workingDirectory)
			throws ConversionException, IOException {
		XcodeVersion xcodeVersion = XcodeVersion.determine();
		List<File> convertedReports = new ArrayList<>();
		for (File xcodeReport : xcodeReports) {
			List<File> filesContainingConvertedReports = new XcodeReportConverter(xcodeVersion, workingDirectory)
					.convert(xcodeReport);
			convertedReports.addAll(filesContainingConvertedReports);
			// TODO: Write the results back to the respective folders
		}
		return convertedReports;
	}

	@Override
	public List<File> convert(File xcodeReport) throws ConversionException, IOException {
		validateCommandLineTools();

		if (ConversionUtils.isXccovArchive(xcodeReport)) {
			File convertedReport = new XccovArchiveConverter(getXcodeVersion(), getWorkingDirectory())
					.convert(xcodeReport);
			return Collections.singletonList(convertedReport);
		} else if (ConversionUtils.isXcresultBundle(xcodeReport)) {
			return new XcresultConverter(getXcodeVersion(), getWorkingDirectory()).convert(xcodeReport);
		} else if (FileSystemUtils.isTarFile(xcodeReport)) {
			return new TarArchiveConverter(getXcodeVersion(), getWorkingDirectory()).convert(xcodeReport);
		}

		return Collections.singletonList(xcodeReport);
	}

	/**
	 * Deletes the given working directory. Should always be executed once the
	 * converter finished the conversion.
	 */
	private static void deleteWorkingDirectory(Path workingDirectory) {
		try {
			FileUtils.deleteDirectory(workingDirectory.toFile());
		} catch (IOException e) {
			LogUtils.warn("Unable to delete temporary working directory " + workingDirectory.toAbsolutePath(), e);
		}
	}

	private static void validateCommandLineTools() throws ConversionException {
		if (!ProcessUtils.run("xcrun", "--version").wasSuccessful()) {
			throw new ConversionException(
					"XCode command line tools not installed. Install command line tools on MacOS by installing XCode "
							+ "from the store and running 'xcode-select --install'.");
		}
	}
}
