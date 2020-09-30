package com.cqse.teamscaleupload;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import okhttp3.HttpUrl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * Manages (i.e. parses and holds) the command line argument for this application.
 */
class InputManager {
    public final String project;
    public final String username;
    public final String accessKey;
    public final String partition;
    public final String format;
    public final String commit;
    public final boolean autoDetectedCommit;
    public final String timestamp;
    public final HttpUrl url;
    public final List<String> files;
    public final Path input;
    public final Boolean validateSsl;

    private InputManager(Namespace namespace) {
        this.project = namespace.getString("project");
        this.username = namespace.getString("user");
        this.accessKey = namespace.getString("accesskey");
        this.partition = namespace.getString("partition");
        this.commit = namespace.getString("commit");
        this.autoDetectedCommit = namespace.getBoolean("detect_commit");
        this.timestamp = namespace.getString("branch_and_timestamp");
        this.files = namespace.getList("files");
        this.url = HttpUrl.parse(namespace.getString("server"));
        this.validateSsl = namespace.getBoolean("validate_ssl");

        String inputFilePath = namespace.getString("input");
        if (inputFilePath != null) {
            this.input = Paths.get(inputFilePath);
        } else {
            this.input = null;
        }

        String formatRaw = namespace.getString("format");
        if (formatRaw != null) {
            this.format = formatRaw.toUpperCase();
        } else {
            this.format = null;
        }
    }
    public static InputManager parseArguments(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload").build()
                .defaultHelp(true)
                .description("Upload coverage, findings, ... to Teamscale.");

        parser.addArgument("-s", "--server").type(String.class).metavar("URL").required(true)
                .help("The url under which the Teamscale server can be reached.");
        parser.addArgument("-p", "--project").type(String.class).metavar("PROJECT").required(true)
                .help("The project ID or alias (NOT the project name!) to which to upload the data.");
        parser.addArgument("-u", "--user").type(String.class).metavar("USER").required(true)
                .help("The username used to perform the upload. Must have the 'Perform External Uploads' permission for the given Teamscale project.");
        parser.addArgument("-a", "--accesskey").type(String.class).metavar("ACCESSKEY").required(true)
                .help("The IDE access key of the given user. Can be retrieved in Teamscale under Admin > Users.");
        parser.addArgument("-t", "--partition").type(String.class).metavar("PARTITION").required(true)
                .help("The partition into which the data is inserted in Teamscale." +
                        " Successive uploads into the same partition will overwrite the data" +
                        " previously inserted there, so use different partitions if you'd instead" +
                        " like to merge data from different sources (e.g. one for Findbugs findings" +
                        " and one for JaCoCo coverage).");
        parser.addArgument("-f", "--format").type(String.class).metavar("FORMAT").required(false)
                .help("The default file format of the uploaded report files. This is applied for all files given as" +
                        " command line arguments or via -i/--input where no format is specified." +
                        " See https://docs.teamscale.com/reference/upload-formats-and-samples/#supported-formats-for-upload" +
                        " for a full list of supported file formats.");
        parser.addArgument("-c", "--commit").type(String.class).metavar("REVISION").required(false)
                .help("The version control commit for which you obtained the report files." +
                        " E.g. if you obtained a test coverage report in your CI pipeline, then this" +
                        " is the commit the CI pipeline built before running the tests." +
                        " Can be either a Git SHA1, a SVN revision number or an Team Foundation changeset ID.");
        parser.addArgument("-b", "--branch-and-timestamp").type(String.class).metavar("BRANCH_AND_TIMESTAMP").required(false)
                .help("The branch and Unix Epoch timestamp for which you obtained the report files." +
                        " E.g. if you obtained a test coverage report in your CI pipeline, then this" +
                        " is the branch and the commit timestamp of the commit that the CI pipeline built before running the tests." +
                        " The timestamp must be milliseconds since 00:00:00 UTC Thursday, 1 January 1970." +
                        "\nFormat: BRANCH:TIMESTAMP" +
                        "\nExample: master:1597845930000");
        parser.addArgument("-i", "--input").type(String.class).metavar("INPUT").required(false)
                .help("A file which contains additional report file patterns. See INPUTFILE for a detailed description of the file format.\n");
        parser.addArgument("--detect-commit").action(Arguments.storeTrue()).required(false)
                .help("Tries to automatically detect the code commit to which to upload from environment variables or" +
                        " a Git or SVN checkout in the current working directory. If guessing fails, the upload will fail." +
                        " This feature supports many common CI tools like Jenkins, GitLab, GitHub Actions, Travis CI etc.");
        parser.addArgument("--validate-ssl").action(Arguments.storeTrue()).required(false)
                .help("By default, SSL certificates are accepted without validation, which makes using this tool with self-signed" +
                        " certificates easier. This flag enables validation.");
        parser.addArgument("files").metavar("FILES").type(String.class).nargs("*").
                help("Path(s) or pattern(s) of the report files to upload. Alternatively, you may provide input files via -i or --input");

        parser.epilog("INPUTFILE" +
                "\n" +
                "\n" +
                "The file you provide via --input consists of file patterns in the same format as used on the command line" +
                " and optionally sections that specify additional report file formats." +
                " The entries in the file are separated by line breaks. Uses the format specified with --format," +
                " unless you overwrite the format explicitly for a set of patterns:\n\n" +
                "pattern1/**.simple\n" +
                "[jacoco]\n" +
                "pattern1/**.xml\n" +
                "pattern2/**.xml\n" +
                "[findbugs]\n" +
                "pattern1/**.findbugs.xml\n" +
                "pattern2/**.findbugs.xml");

        try {
            Namespace namespace = parser.parseArgs(args);
            InputManager inputManager = new InputManager(namespace);
            inputManager.validate(parser);
            return inputManager;
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return null;
        }
    }

    private void validate(ArgumentParser parser) throws ArgumentParserException {
        if (url == null) {
            throw new ArgumentParserException("You provided an invalid URL in the --server option", parser);
        }

        if (hasMoreThanOneCommitOptionSet()) {
            throw new ArgumentParserException("You used more than one of --detect-commit, --commit and --timestamp." +
                    " You must choose one of these three options to specify the commit for which you would like to" +
                    " upload data to Teamscale", parser);
        }

        if (files.isEmpty() && input == null) {
            throw new ArgumentParserException("No report files provided." +
                    " You must either specify the paths of the coverage files as command line" +
                    " arguments or provide them in an input file via --input", parser);
        }

        if (format == null) {
            throw new ArgumentParserException("Please provide the default report format with --format", parser);
        }
    }

    private boolean hasMoreThanOneCommitOptionSet() {
        if (commit != null && timestamp != null) {
            return true;
        }
        if (commit != null && autoDetectedCommit) {
            return true;
        }
        return timestamp != null && autoDetectedCommit;
    }
}
