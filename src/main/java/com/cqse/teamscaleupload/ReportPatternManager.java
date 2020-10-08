package com.cqse.teamscaleupload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class manages the given file formats and
 * corresponding report file patterns.
 */
public class ReportPatternManager {


    /** A map from file formats to corresponding report file patterns. */
    private final Map<String, Set<String>> formatToFileNames = new HashMap<>();

    /** Expected pattern for formats in the input file, matches e.g. "[vs_coverage]" */
    private static final Pattern FORMAT_PATTERN = Pattern.compile("\\[(\\w+)\\]");

    /**
     * Reads all file patterns which are specified in the given input file
     * and adds them to {@link #formatToFileNames} for the respective format.
     */
    public void addReportFilePatternsFromInputFile(Path input) throws IOException {

        List<String> nonEmptyLines = Files.readAllLines(input).stream().filter(line -> !line.trim().isEmpty()).
                collect(Collectors.toList());

        if (!nonEmptyLines.isEmpty()) {
            String line = nonEmptyLines.get(0);
            Matcher formatPatternMatcher = FORMAT_PATTERN.matcher(line);
            if (!formatPatternMatcher.matches()) {
                TeamscaleUpload.fail("The first line in the input file '" + line + "' must specify a report format," +
                        " but does not match the expected format. See help for more information.");
            }
        }

        String currentFormat = null;
        for (String line : nonEmptyLines) {
            Matcher formatPatternMatcher = FORMAT_PATTERN.matcher(line);
            if (formatPatternMatcher.matches()) {
                currentFormat = formatPatternMatcher.group(1).toUpperCase();
                formatToFileNames.computeIfAbsent(currentFormat, k -> new HashSet<>());
            } else {
                formatToFileNames.get(currentFormat).add(line);
            }
        }
    }

    /**
     * Return a set of all file patterns for the given format.
     * Returns null if the given format does not exist.
     */
    public Set<String> getPatternsForFormat(String format) {
        return formatToFileNames.get(format);
    }

    /**
     * Returns all formats which are used in this session.
     */
    public Set<String> getAllUsedFormats() {
        return formatToFileNames.keySet();
    }

    /**
     * Adds the file patters for the given format.
     * This is used for file patterns which are directly specified via the command line.
     */
    public void addFilePatternsForFormat(List<String> filePatterns, String format) {
        if (!filePatterns.isEmpty()) {
            formatToFileNames.computeIfAbsent(format, k -> new HashSet<>()).addAll(filePatterns);
        }
    }
}
