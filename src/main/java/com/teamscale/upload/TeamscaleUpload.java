package com.teamscale.upload;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.teamscale.upload.client.TeamscaleClient;
import com.teamscale.upload.resolve.FilePatternResolutionException;
import com.teamscale.upload.resolve.ReportPatternUtils;
import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.xcode.ConvertedReport;
import com.teamscale.upload.xcode.XCResultConverter;
import com.teamscale.upload.xcode.XCResultConverter.ConversionException;

/**
 * Main class of the teamscale-upload project.
 */
public class TeamscaleUpload {

	/**
	 * This method serves as entry point to the teamscale-upload application.
	 */
	public static void main(String[] args) throws FilePatternResolutionException, IOException {
		CommandLine commandLine = CommandLine.parseArguments(args);

		if (commandLine.debugLogEnabled) {
			LogUtils.enableDebugLogging();
		}
		if (commandLine.printStackTrace) {
			LogUtils.enableStackTracePrintingForKnownErrors();
		}

		Map<String, Set<File>> filesByFormat = resolveAndConvertFiles(commandLine);
		TeamscaleClient.performUpload(commandLine, filesByFormat);
	}

	/**
	 * Resolves the files that should be uploaded to Teamscale and converts them to
	 * the expected formated if needed (e.g., XCode reports).
	 */
	private static Map<String, Set<File>> resolveAndConvertFiles(CommandLine commandLine)
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
	 * {@linkplain XCResultConverter#XCODE_REPORT_FORMAT XCode report format}.
	 */
	private static boolean containsAnyXCodeReports(Set<String> fileFormats) {
		return fileFormats.contains(XCResultConverter.XCODE_REPORT_FORMAT);
	}

	/**
	 * Converts the reports from the internal binary XCode format to a readable
	 * report that can be uploaded to Teamscale.
	 */
	private static void convertXCodeReports(Map<String, Set<File>> formatToFiles) {
		try {
			Set<File> xcresultBundles = formatToFiles.remove(XCResultConverter.XCODE_REPORT_FORMAT);
			for (File xcodeReport : xcresultBundles) {
				List<ConvertedReport> convertedReports = XCResultConverter.convert(formatToFiles, xcodeReport);
				// TODO: Check if we can just use XCODE_REPORT_FORMAT instead of
				// convertedReport.reportFormat here
				convertedReports.forEach(convertedReport -> formatToFiles
						.computeIfAbsent(convertedReport.reportFormat, x -> new HashSet<>())
						.add(convertedReport.report));
			}
		} catch (ConversionException e) {
			LogUtils.failWithoutStackTrace(e.getMessage(), e);
		}
	}

}
