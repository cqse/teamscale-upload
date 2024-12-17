package com.teamscale.upload.xcode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.FileSystemUtils;

/**
 * Converts an Xcode report (see {@link #supportsConversion(File)} for supported
 * formats) into the {@value ConversionUtils#XCCOV_REPORT_FILE_EXTENSION}
 * format.
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
		Path workingDirectory;
		try {
			workingDirectory = Files.createTempDirectory("teamscale-upload");
		} catch (IOException e) {
			throw new ConversionException("Could not create a temporary directory", e);
		}

		XcodeVersion xcodeVersion = XcodeVersion.determine();
		List<File> convertedReports = new ArrayList<>();
		for (File xcodeReport : xcodeReports) {
			try {
				List<File> filesContainingConvertedReports = new XcodeReportConverter(xcodeVersion, workingDirectory)
						.convert(xcodeReport);
				convertedReports.addAll(filesContainingConvertedReports);
			} catch (IOException e) {
				throw new ConversionException("I/O error while converting this report: " + xcodeReport, e);
			}
		}
		return convertedReports;
	}

	@Override
	public List<File> convert(File xcodeReport) throws ConversionException, IOException {
		if (!supportsConversion(xcodeReport)) {
			return Collections.singletonList(xcodeReport);
		}
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
	 * Returns true if XCode report requires conversion.
	 */
	public static boolean supportsConversion(File report) {
		return FileSystemUtils.isTarFile(report) || ConversionUtils.isXcresultBundle(report)
				|| ConversionUtils.isXccovArchive(report);
	}

	private static void validateCommandLineTools() throws ConversionException {
		if (!ProcessUtils.run("xcrun", "--version").wasSuccessful()) {
			throw new ConversionException(
					"XCode command line tools not installed. Install command line tools on MacOS by installing XCode "
							+ "from the store and running 'xcode-select --install'.");
		}
	}
}
