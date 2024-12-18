package com.teamscale.upload.xcode;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
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
 * <p>
 * This is the main entry point into the "converting pipeline" as this converter
 * performs setup steps and delegates the respective. The converting pipeline
 * looks as follows: <code>tar -> xcresult -> xccovarchive -> xccov</code>.
 * Depending on which file type is passed to this converter, the conversion will
 * start in the respective step. Each of these steps has its own converter (see
 * {@link TarArchiveConverter}, {@link XcresultConverter}, and
 * {@link XccovArchiveConverter}). The intermediate results will be stored in a
 * temporary working directory that will be deleted after the conversion is done
 * (see {@link #convert(Collection)}). In the end the final results will be
 * copied to the same folder as the report (see
 * {@link #copyResultsFromWorkingDirectory(File, List)}).
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
		} catch (FileAlreadyExistsException e) {
			throw new ConversionException("Could not write to file because it already exists: " + e.getFile(), e);
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
			filesContainingConvertedReports = copyResultsFromWorkingDirectory(xcodeReport,
					filesContainingConvertedReports);
			convertedReports.addAll(filesContainingConvertedReports);
		}
		return convertedReports;
	}

	private static List<File> copyResultsFromWorkingDirectory(File xcodeReport, List<File> results) throws IOException {
		Path destinationDirectory = xcodeReport.toPath().getParent();
		if (results.size() == 1) {
			// Optimize file naming when only one result is present because during the
			// xcresult conversion,numbers are appended to the original report file name
			File result = results.get(0);
			Path resultDestination = destinationDirectory
					.resolve(xcodeReport.getName() + ConversionUtils.XCCOV_REPORT_FILE_EXTENSION);
			Files.copy(result.toPath(), resultDestination);
			return Collections.singletonList(resultDestination.toFile());
		}

		List<File> copiedResults = new ArrayList<>();
		for (File result : results) {
			Path resultDestination = destinationDirectory.resolve(result.getName());
			Files.copy(result.toPath(), resultDestination);
			copiedResults.add(resultDestination.toFile());
		}
		return copiedResults;
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
