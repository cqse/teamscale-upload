package com.teamscale.upload;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.teamscale.upload.client.ReportUploadClient;
import com.teamscale.upload.client.VulnerabilityReportUploadClient;
import com.teamscale.upload.resolve.FilePatternResolutionException;
import com.teamscale.upload.resolve.FilePatternResolver;
import com.teamscale.upload.resolve.ReportPatternUtils;
import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.xcode.ConversionException;
import com.teamscale.upload.xcode.XcodeReportConverter;

/**
 * Main class of the teamscale-upload project.
 */
public class TeamscaleUpload {

	/**
	 * This method serves as the entry point to the teamscale-upload application.
	 */
	public static void main(String[] args) throws FilePatternResolutionException, IOException {
		if (args.length > 0 && VulnerabilityReportCommandLineOptions.COMMAND_NAME.equals(args[0])) {
			uploadVulnerabilityReport(Arrays.copyOfRange(args, 1, args.length));
			return;
		}

		ReportCommandLineOptions commandLine = ReportCommandLineOptions.parseArguments(args);
		configureLogging(commandLine);

		Map<String, Set<File>> filesByFormat = resolveAndConvertFiles(commandLine);
		ReportUploadClient.performUpload(commandLine, filesByFormat);
	}

	/**
	 * Uploads a vulnerability report. The given arguments must not include the
	 * {@link VulnerabilityReportCommandLineOptions#COMMAND_NAME} command itself.
	 */
	private static void uploadVulnerabilityReport(String[] args) throws FilePatternResolutionException, IOException {
		VulnerabilityReportCommandLineOptions commandLine = VulnerabilityReportCommandLineOptions.parseArguments(args);
		configureLogging(commandLine);

		File reportFile = resolveVulnerabilityReportFile(commandLine.filePathOrPattern);
		VulnerabilityReportUploadClient.performUpload(commandLine, reportFile);
	}

	/**
	 * Resolves the path or pattern given for a vulnerability report upload to the
	 * single file to upload.
	 * <p>
	 * Teamscale stores one report per build name and version, so the program is
	 * terminated with an error message if it resolves to anything other than
	 * exactly one file.
	 */
	private static File resolveVulnerabilityReportFile(String filePathOrPattern) throws FilePatternResolutionException {
		String pattern = ReportPatternUtils.normalizeFilePattern(filePathOrPattern);
		List<File> resolvedFiles = new FilePatternResolver().resolveToMultipleFiles("REPORT", pattern).stream()
				.filter(File::isFile)
				// two matches are all we need: one to upload, a second to report that the
				// pattern is ambiguous. Stopping there saves a stat call per further match.
				.limit(2).toList();

		if (resolvedFiles.isEmpty()) {
			LogUtils.fail("The vulnerability report path '" + pattern + "' could not be resolved to any files."
					+ " Please check the path for correctness and ensure that the report exists"
					+ " and is a file, not a directory.");
		}

		if (resolvedFiles.size() > 1) {
			String matchedFiles = resolvedFiles.stream().map(File::getPath).collect(Collectors.joining("\n"));
			LogUtils.fail("The pattern '" + pattern + "' matches more than one file, but Teamscale"
					+ " stores exactly one vulnerability report per --build-name and --build-version."
					+ " Uploading all of them would make them overwrite each other."
					+ "\nAmong the matched files are:\n" + matchedFiles
					+ "\nPlease narrow the pattern down to a single file, or use a different --build-name"
					+ " or --build-version for each of them.");
		}

		return resolvedFiles.get(0);
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
