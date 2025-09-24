package com.teamscale.upload.resolve;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.teamscale.upload.utils.LogUtils;

/**
 * This class provides the functionality to resolve the report file patterns and
 * formats from the command line to a map from report formats to actual files
 * which can then be used for the upload to Teamscale.
 */
public class ReportPatternUtils {

	/**
	 * Expected pattern for formats in the input file, matches e.g. "[vs_coverage]"
	 */
	private static final Pattern FORMAT_PATTERN = Pattern.compile("\\[(\\w+)]");

	/**
	 * Returns a map from file formats to corresponding report files.
	 *
	 * <ol>
	 * <li>Reads file patterns and formats from the input file</li>
	 * <li>Reads file patterns for the format specified on the command line</li>
	 * <li>Resolves all file patterns from the previous steps to actual files.</li>
	 * </ol>
	 */
	public static Map<String, Set<File>> resolveInputFilePatterns(Path inputFile, List<String> commandLineFilePatterns,
			String commandLineFormat) throws IOException, FilePatternResolutionException {
		Map<String, Set<String>> formatToFilePatterns = new HashMap<>();

		if (inputFile != null) {
			// Check if the specified input file via --input exists.
			if (!inputFile.toFile().exists()) {
				LogUtils.fail("Could not find the specified input file: '" + inputFile
						+ "'. Please ensure that you have no typo in the file path.");
			}
			formatToFilePatterns = parsePatternsFromInputFile(inputFile);
		}
		parseFilePatternsForFormatOnCommandLine(formatToFilePatterns, commandLineFilePatterns, commandLineFormat);

		return resolveFilePatternsToFiles(formatToFilePatterns);
	}

	/** Resolve all file patterns to the actual files for all given formats. */
	private static Map<String, Set<File>> resolveFilePatternsToFiles(Map<String, Set<String>> formatToFilePatterns)
			throws FilePatternResolutionException {
		Map<String, Set<File>> formatToFiles = new HashMap<>();
		for (String format : formatToFilePatterns.keySet()) {
			Set<String> patternsForFormat = formatToFilePatterns.get(format);
			formatToFiles.put(format, resolveFilesForPatterns(patternsForFormat));
		}
		return formatToFiles;
	}

	/**
	 * Returns a map from report format to file patterns which are parsed from the
	 * given input file. In the end, it is verified that no format has an empty set
	 * of patterns ({@link #validatePatternsForFormats(Map)})
	 */
	private static Map<String, Set<String>> parsePatternsFromInputFile(Path inputFile) throws IOException {
		Map<String, Set<String>> formatToFilePatterns = new HashMap<>();

		List<String> nonEmptyLines = Files.readAllLines(inputFile).stream().filter(line -> !line.trim().isEmpty())
				.collect(Collectors.toList());

		if (!nonEmptyLines.isEmpty()) {
			String line = nonEmptyLines.get(0);
			Matcher formatPatternMatcher = FORMAT_PATTERN.matcher(line);
			if (!formatPatternMatcher.matches()) {
				LogUtils.fail("The first line in the input file '" + line + "' must specify a report format,"
						+ " but does not match the expected format. See help for more information.");
			}
		}

		String currentFormat = null;
		for (String line : nonEmptyLines) {
			Matcher formatPatternMatcher = FORMAT_PATTERN.matcher(line);
			if (formatPatternMatcher.matches()) {
				currentFormat = formatPatternMatcher.group(1).toUpperCase();
				formatToFilePatterns.computeIfAbsent(currentFormat, k -> new HashSet<>());
			} else {
				formatToFilePatterns.get(currentFormat).add(normalizeFilePattern(line));
			}
		}

		validatePatternsForFormats(formatToFilePatterns);
		return formatToFilePatterns;
	}

	/**
	 * This method verifies that no report format contains an empty set of patterns.
	 * This could indicate that the user forgot to specify some patterns. In this
	 * case, we terminate the program with an error message.
	 */
	private static void validatePatternsForFormats(Map<String, Set<String>> formatToFilePatterns) {
		for (String format : formatToFilePatterns.keySet()) {
			Set<String> patternsForFormat = formatToFilePatterns.get(format);
			if (patternsForFormat.isEmpty()) {
				LogUtils.fail("The input file contains no patterns for [" + format + "]."
						+ " Did you forget to specify file patterns for that format?");
			}
		}
	}

	/**
	 * Normalizes the given file patterns, @see
	 * {@link #normalizeFilePattern(String)}, and adds them for the given format.
	 */
	private static void parseFilePatternsForFormatOnCommandLine(Map<String, Set<String>> formatToFilePatterns,
			List<String> filePatterns, String format) {
		if (!filePatterns.isEmpty()) {
			List<String> normalizedFilePatters = filePatterns.stream().map(ReportPatternUtils::normalizeFilePattern)
					.toList();
			formatToFilePatterns.computeIfAbsent(format, k -> new HashSet<>()).addAll(normalizedFilePatters);
		}
	}

	/**
	 * Resolves the given patterns to actual files. The program is terminated with
	 * an error message if a pattern cannot be resolved to any actual files.
	 */
	private static Set<File> resolveFilesForPatterns(Set<String> patterns) throws FilePatternResolutionException {
		FilePatternResolver resolver = new FilePatternResolver();

		Set<File> fileList = new HashSet<>();
		for (String pattern : patterns) {
			List<File> resolvedFiles = resolver.resolveToMultipleFiles("files", pattern);
			resolvedFiles.removeIf(Predicate.not(File::exists));

			if (resolvedFiles.isEmpty()) {
				LogUtils.fail("The pattern '" + pattern + "' could not be resolved to any files."
						+ " Please check the pattern for correctness or remove it if you do not need it.");
			}

			fileList.addAll(resolvedFiles);
		}
		return fileList;
	}

	/**
	 * Replace backslashes by forward slashes in the pattern. The linux file system
	 * usually cannot handle backward slashes as path separator, Windows can usually
	 * handle both. So, we normalize all paths to use only forward slashes as they
	 * are expected to work for all operating systems.
	 */
	private static String normalizeFilePattern(String pattern) {
		return pattern.replaceAll("\\\\", "/");
	}
}
