package com.cqse.teamscaleupload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class manages the given file formats and
 * corresponding report file patterns.
 */
public class ReportPatternManager {


    /** A map from file formats to corresponding report file patterns. */
    private final Map<String, List<String>> formatToFileNames = new HashMap<>();

    /** Expected pattern for formats in the input file, matches e.g. "[vs_coverage]" */
    private static final Pattern formatPattern = Pattern.compile("\\[(\\w+)\\]");

    /**
     * Reads all file patterns which are specified in the given input file
     * and adds them to {@link #formatToFileNames} for the respective format.
     *
     * TODO: If the input file does not specify a report format, the given default format is used.
     */
    public void addReportFilePatternsFromInputFile(Path input, String defaultFormat) throws IOException {
        String currentFormat = defaultFormat;
        List<String> filesForCurrentFormat = new ArrayList<>();
        for (String line : Files.readAllLines(input)) {
            Matcher formatPatternMatcher = formatPattern.matcher(line);
            if (formatPatternMatcher.matches()) {
                if (!filesForCurrentFormat.isEmpty()) {
                    formatToFileNames.put(currentFormat, filesForCurrentFormat);
                    filesForCurrentFormat = new ArrayList<>();
                } else {
                    System.err.println("The input file contains no patterns for format [" + currentFormat + "]. " +
                            "Did you forget to specify files to upload for that format?");
                }
                currentFormat = formatPatternMatcher.group(1);
            } else {
                filesForCurrentFormat.add(line);
            }
        }
        formatToFileNames.put(currentFormat.toUpperCase(), filesForCurrentFormat);
    }

    /**
     * Return a list of all file patterns for the given format.
     * Returns null if the given format does not exist.
     */
    public List<String> getPatternsForFormat(String format) {
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
            formatToFileNames.computeIfAbsent(format, k -> new ArrayList<>()).addAll(filePatterns);
        }
    }
}
