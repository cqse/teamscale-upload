package com.teamscale.upload;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.teamscale.upload.client.ReportUploadClient;
import com.teamscale.upload.client.SbomUploadClient;
import com.teamscale.upload.resolve.FilePatternResolutionException;
import com.teamscale.upload.resolve.ReportPatternUtils;
import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.xcode.ConversionException;
import com.teamscale.upload.xcode.XcodeReportConverter;

/**
 * Main class of the teamscale-upload project.
 */
public class TeamscaleUpload {

	/**
	 * This method serves as entry point to the teamscale-upload application.
	 */
	public static void main(String[] args) throws FilePatternResolutionException, IOException {
		if (args.length > 0 && SbomCommandLineOptions.COMMAND_NAME.equals(args[0])) {
			uploadSbom(Arrays.copyOfRange(args, 1, args.length));
			return;
		}

		ReportCommandLineOptions commandLine = ReportCommandLineOptions.parseArguments(args);
		configureLogging(commandLine);

		Map<String, Set<File>> filesByFormat = resolveAndConvertFiles(commandLine);
		ReportUploadClient.performUpload(commandLine, filesByFormat);
	}

	/**
	 * Uploads a Software Bill of Materials. The given arguments must not include
	 * the {@link SbomCommandLineOptions#COMMAND_NAME} command itself.
	 */
	private static void uploadSbom(String[] args) throws FilePatternResolutionException, IOException {
		SbomCommandLineOptions commandLine = SbomCommandLineOptions.parseArguments(args);
		configureLogging(commandLine);

		File sbomFile = commandLine.resolveSbomFile();
		SbomUploadClient.performUpload(commandLine, sbomFile);
	}

	private static void configureLogging(CommonCommandLineOptions commandLine) {
		if (commandLine.debugLogEnabled) {
			LogUtils.enableDebugLogging();
		}
		if (commandLine.printStackTrace) {
			LogUtils.enableStackTracePrintingForKnownErrors();
		}
	}

	/**
	 * Resolves the files that should be uploaded to Teamscale and converts them to
	 * the expected formated if needed (e.g., XCode reports).
	 */
	private static Map<String, Set<File>> resolveAndConvertFiles(ReportCommandLineOptions commandLine)
			throws FilePatternResolutionException, IOException {
		Map<String, Set<File>> filesByFormat = ReportPatternUtils.resolveInputFilePatterns(commandLine.inputFile,
				commandLine.files, commandLine.format);
		if (containsAnyXCodeReports(filesByFormat.keySet())) {
			// XCode reports need to be converted before they can be uploaded to Teamscale
			convertXCodeReports(filesByFormat);
		}
		return filesByFormat;
	}

	/**
	 * Returns whether the given set of file formats contains the
	 * {@linkplain XcodeReportConverter#XCODE_REPORT_FORMAT XCode report format}.
	 */
	private static boolean containsAnyXCodeReports(Set<String> fileFormats) {
		return fileFormats.contains(XcodeReportConverter.XCODE_REPORT_FORMAT);
	}

	/**
	 * Converts the reports from the internal binary XCode format to a readable
	 * report that can be uploaded to Teamscale.
	 */
	private static void convertXCodeReports(Map<String, Set<File>> filesByFormat) {
		try {
			Set<File> xcresultBundles = filesByFormat.remove(XcodeReportConverter.XCODE_REPORT_FORMAT);
			List<File> convertedReports = XcodeReportConverter.convert(xcresultBundles);

			// Add the converted reports back to filesByFormat
			filesByFormat.computeIfAbsent(XcodeReportConverter.XCODE_REPORT_FORMAT, format -> new HashSet<>())
					.addAll(convertedReports);
		} catch (ConversionException e) {
			LogUtils.failWithoutStackTrace(e.getMessage(), e);
		}
	}
}
