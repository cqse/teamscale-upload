package com.cqse.teamscaleupload;

import org.conqat.lib.commons.collections.CollectionUtils;
import org.conqat.lib.commons.filesystem.AntPatternUtils;
import org.conqat.lib.commons.filesystem.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

/**
 * Helper class to support resolving file paths which may contain Ant patterns.
 */
public class FilePatternResolver {

    /**
     * Stand-in for the question mark operator.
     */
    private static final String QUESTION_REPLACEMENT = "!@";

    /**
     * Stand-in for the asterisk operator.
     */
    private static final String ASTERISK_REPLACEMENT = "#@";

    /**
     * Interprets the given pattern as an Ant pattern and resolves it to one or multiple existing {@link File}s. If the
     * given path is relative, it is resolved relative to the current working directory.
     */
    public List<File> resolveToMultipleFiles(String optionName, String pattern) throws AgentOptionParseException {
        return resolveToMultipleFiles(optionName, pattern, new File("."));
    }

    /**
     * Interprets the given pattern as an Ant pattern and resolves it to one or multiple existing {@link File}s. If the
     * given path is relative, it is resolved relative to the current working directory.
     * <p>
     * Visible for testing only.
     */
    /* package */ List<File> resolveToMultipleFiles(String optionName, String pattern,
                                                    File workingDirectory) throws AgentOptionParseException {
        if (isPathWithPattern(pattern)) {
            return CollectionUtils
                    .map(parseFileFromPattern(optionName, pattern, workingDirectory).getAllMatchingPaths(),
                            Path::toFile);
        }
        try {
            return Collections.singletonList(workingDirectory.toPath().resolve(Paths.get(pattern)).toFile());
        } catch (InvalidPathException e) {
            throw new AgentOptionParseException("Invalid path given for option " + optionName + ": " + pattern, e);
        }
    }

    /**
     * Parses the pattern as a Ant pattern to one or multiple files or directories.
     */
    private FilePatternResolverRun parseFileFromPattern(String optionName,
                                                        String pattern,
                                                        File workingDirectory) throws AgentOptionParseException {
        return new FilePatternResolverRun(optionName, pattern, workingDirectory).resolve();
    }

    /**
     * Returns whether the given path contains Ant pattern characters (?,*).
     */
    private static boolean isPathWithPattern(String path) {
        return path.contains("?") || path.contains("*");
    }

    /**
     * Returns whether the given path contains artificial pattern characters ({@link #QUESTION_REPLACEMENT}, {@link
     * #ASTERISK_REPLACEMENT}).
     */
    private static boolean isPathWithArtificialPattern(String path) {
        return path.contains(QUESTION_REPLACEMENT) || path.contains(ASTERISK_REPLACEMENT);
    }

    private static class FilePatternResolverRun {
        private final File workingDirectory;
        private final String optionName;
        private final String pattern;
        private String suffixPattern;
        private Path basePath;
        private List<Path> matchingPaths;

        private FilePatternResolverRun(String optionName, String pattern, File workingDirectory) {
            this.optionName = optionName;
            this.pattern = pattern;
            this.workingDirectory = workingDirectory.getAbsoluteFile();
            splitIntoBasePathAndPattern(pattern);
        }

        /**
         * Resolves the pattern. The results can be retrieved via {@link #getAllMatchingPaths()}.
         */
        private FilePatternResolverRun resolve() throws AgentOptionParseException {
            Pattern pathRegex = AntPatternUtils.convertPattern(suffixPattern, false);
            Predicate<Path> filter = path -> pathRegex
                    .matcher(FileSystemUtils.normalizeSeparators(basePath.relativize(path).toString())).matches();

            try {
                matchingPaths = Files.walk(basePath).filter(filter).sorted().collect(toList());
            } catch (IOException e) {
                throw new AgentOptionParseException(
                        "Could not recursively list files in directory " + basePath + " in order to resolve pattern " + suffixPattern + " given for option " + optionName,
                        e);
            }
            return this;
        }

        /**
         * Splits the path into a base dir, i.e. the directory-prefix of the path that does not contain any ? or *
         * placeholders, and a pattern suffix. We need to replace the pattern characters with stand-ins, because ? and *
         * are not allowed as path characters on windows.
         */
        private void splitIntoBasePathAndPattern(String value) {
            String pathWithArtificialPattern = value.replace("?", QUESTION_REPLACEMENT)
                    .replace("*", ASTERISK_REPLACEMENT);
            Path pathWithPattern = Paths.get(pathWithArtificialPattern);
            Path baseDir = pathWithPattern;
            while (isPathWithArtificialPattern(baseDir.toString())) {
                baseDir = baseDir.getParent();
                if (baseDir == null) {
                    suffixPattern = value;
                    basePath = workingDirectory.toPath().resolve("").normalize().toAbsolutePath();
                    return;
                }
            }
            suffixPattern = baseDir.relativize(pathWithPattern).toString().replace(QUESTION_REPLACEMENT, "?")
                    .replace(ASTERISK_REPLACEMENT, "*");
            basePath = workingDirectory.toPath().resolve(baseDir).normalize().toAbsolutePath();
        }

        /**
         * Returns all matched paths after the resolution.
         */
        private List<Path> getAllMatchingPaths() {
            if (this.matchingPaths.isEmpty()) {
                System.err.println(
                        "The pattern " + this.suffixPattern + " in " + this.basePath
                                .toString() + " for option " + optionName + " did not match any file!");
            }
            System.out.println("Resolved " + pattern + " to " + this.matchingPaths.size() + " files");
            return this.matchingPaths;
        }
    }
}