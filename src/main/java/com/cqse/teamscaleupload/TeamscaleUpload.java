package com.cqse.teamscaleupload;

import com.cqse.teamscaleupload.autodetect_revision.EnvironmentVariableChecker;
import com.cqse.teamscaleupload.autodetect_revision.GitChecker;
import com.cqse.teamscaleupload.autodetect_revision.SvnChecker;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Main class of the teamscale-upload project.
 */
public class TeamscaleUpload {

    private static final RequestBody EMPTY_BODY = RequestBody.create(null, new byte[0]);

    private static class Input {
        private final String project;
        private final String username;
        private final String accessKey;
        private final String partition;
        private final String format;
        private final String commit;
        private final boolean autoDetectCommit;
        private final String timestamp;
        private final HttpUrl url;
        private final List<String> files;
        private final Path inputFile;
        private final Boolean validateSsl;
        private final String message;
        private final List<String> additionalMessageLines;

        private Input(Namespace namespace) {
            this.project = namespace.getString("project");
            this.username = namespace.getString("user");
            this.accessKey = namespace.getString("accesskey");
            this.partition = namespace.getString("partition");
            this.commit = namespace.getString("commit");
            this.autoDetectCommit = namespace.getBoolean("detect_commit");
            this.timestamp = namespace.getString("branch_and_timestamp");
            this.files = getListSafe(namespace, "files");
            this.url = HttpUrl.parse(namespace.getString("server"));
            this.validateSsl = namespace.getBoolean("validate_ssl");
            this.message = namespace.getString("message");
            this.additionalMessageLines = getListSafe(namespace, "append_to_message");

            String inputFilePath = namespace.getString("input");
            if (inputFilePath != null) {
                this.inputFile = Paths.get(inputFilePath);
            } else {
                this.inputFile = null;
            }

            String formatRaw = namespace.getString("format");
            if (formatRaw != null) {
                this.format = formatRaw.toUpperCase();
            } else {
                this.format = null;
            }
        }

        private static List<String> getListSafe(Namespace namespace, String key) {
            List<String> list = namespace.getList(key);
            if (list == null) {
                return Collections.emptyList();
            }
            return list;
        }

        /**
         * Checks the validity of the command line arguments and throws an exception if any
         * invalid configuration is detected.
         */
        public void validate(ArgumentParser parser) throws ArgumentParserException {
            if (url == null) {
                throw new ArgumentParserException("You provided an invalid URL in the --server option", parser);
            }

            if (hasMoreThanOneCommitOptionSet()) {
                throw new ArgumentParserException("You used more than one of --detect-commit, --commit and --timestamp." +
                        " You must choose one of these three options to specify the commit for which you would like to" +
                        " upload data to Teamscale", parser);
            }

            if (files.isEmpty() && inputFile == null) {
                throw new ArgumentParserException("You did not provide any report files to upload." +
                        " You must either specify the paths of the report files as command line" +
                        " arguments or provide them in an input file via --input", parser);
            }

            if (!files.isEmpty() && format == null) {
                throw new ArgumentParserException("Please specify a report format with --format " +
                        "if you pass report patterns as command line arguments", parser);
            }
        }

        private boolean hasMoreThanOneCommitOptionSet() {
            if (commit != null && timestamp != null) {
                return true;
            }
            if (commit != null && autoDetectCommit) {
                return true;
            }
            return timestamp != null && autoDetectCommit;
        }
    }

    private static String detectCommit() {
        String commit = EnvironmentVariableChecker.findCommit();
        if (commit != null) {
            return commit;
        }

        commit = GitChecker.findCommit();
        if (commit != null) {
            return commit;
        }

        return SvnChecker.findRevision();
    }

    private static Input parseArguments(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload").build()
                .defaultHelp(true)
                .description("Upload coverage, findings, ... to Teamscale.");

        parser.addArgument("-s", "--server").metavar("URL").required(true)
                .help("The url under which the Teamscale server can be reached.");
        parser.addArgument("-p", "--project").metavar("PROJECT").required(true)
                .help("The project ID or alias (NOT the project name!) to which to upload the data.");
        parser.addArgument("-u", "--user").metavar("USER").required(true)
                .help("The username used to perform the upload. Must have the" +
                        " 'Perform External Uploads' permission for the given Teamscale project.");
        parser.addArgument("-a", "--accesskey").metavar("ACCESSKEY").required(true)
                .help("The IDE access key of the given user. Can be retrieved in Teamscale under Admin > Users.");
        parser.addArgument("-t", "--partition").metavar("PARTITION").required(true)
                .help("The partition into which the data is inserted in Teamscale." +
                        " Successive uploads into the same partition will overwrite the data" +
                        " previously inserted there, so use different partitions if you'd instead" +
                        " like to merge data from different sources (e.g. one for Findbugs findings" +
                        " and one for JaCoCo coverage).");
        parser.addArgument("-f", "--format").metavar("FORMAT").required(false)
                .help("The file format of the reports which are specified as command line arguments." +
                        "\nSee http://cqse.eu/upload-formats for a full list of supported file formats.");
        parser.addArgument("-c", "--commit").metavar("REVISION").required(false)
                .help("The version control commit for which you obtained the report files." +
                        " E.g. if you obtained a test coverage report in your CI pipeline, then this" +
                        " is the commit the CI pipeline built before running the tests." +
                        " Can be either a Git SHA1, a SVN revision number or an Team Foundation changeset ID.");
        parser.addArgument("-b", "--branch-and-timestamp").metavar("BRANCH_AND_TIMESTAMP").required(false)
                .help("The branch and Unix Epoch timestamp for which you obtained the report files." +
                        " E.g. if you obtained a test coverage report in your CI pipeline, then this" +
                        " is the branch and the commit timestamp of the commit that the CI pipeline" +
                        " built before running the tests. The timestamp must be milliseconds since" +
                        " 00:00:00 UTC Thursday, 1 January 1970 or the string 'HEAD' to upload to" +
                        " the latest revision on that branch." +
                        "\nFormat: BRANCH:TIMESTAMP" +
                        "\nExample: master:1597845930000" +
                        "\nExample: develop:HEAD");
        parser.addArgument("--message").metavar("MESSAGE").required(false)
                .help("The message for the commit created in Teamscale for this upload. Will be" +
                        " visible in the Activity perspective. Defaults to a message containing" +
                        " useful meta-information about the upload and the machine performing it.");
        parser.addArgument("-i", "--input").metavar("INPUT").required(false)
                .help("A file which contains additional report file patterns. See INPUTFILE for a" +
                        " detailed description of the file format.");
        parser.addArgument("--detect-commit").action(Arguments.storeTrue()).required(false)
                .help("Tries to automatically detect the code commit to which to upload from" +
                        " environment variables or a Git or SVN checkout in the current working" +
                        " directory. If guessing fails, the upload will fail. This feature supports" +
                        " many common CI tools like Jenkins, GitLab, GitHub Actions, Travis CI etc.");
        parser.addArgument("--validate-ssl").action(Arguments.storeTrue()).required(false)
                .help("By default, SSL certificates are accepted without validation, which makes" +
                        " using this tool with self-signed certificates easier. This flag enables" +
                        " validation.");
        parser.addArgument("--append-to-message").metavar("LINE")
                .action(Arguments.append()).required(false)
                .help("Appends the given line to the message. Use this to augment the autogenerated" +
                        " message instead of replacing it. You may specify this parameter multiple" +
                        " times to append several lines to the message.");
        parser.addArgument("files").metavar("FILES").nargs("*")
                .help("Path(s) or pattern(s) of the report files to upload. Alternatively, you may" +
                        " provide input files via -i or --input");

        parser.epilog("For general usage help and alternative upload methods, please check our online" +
                " documentation at:" +
                "\nhttp://cqse.eu/tsu-docs" +
                "\n\nINPUTFILE" +
                "\n\nThe input file allows to upload multiple report files for different formats in one" +
                " upload session. Each section of reports must start with a specification of the" +
                " report format. The report file patterns have the same format as used on the command" +
                " line. The entries in the file are separated by line breaks. Blank lines are ignored." +
                "\n\nExample:" +
                "\n\n[jacoco]" +
                "\npattern1/**.xml" +
                "\npattern2/**.xml" +
                "\n[findbugs]" +
                "\npattern1/**.findbugs.xml" +
                "\npattern2/**.findbugs.xml");

        try {
            Namespace namespace = parser.parseArgs(args);
            Input input = new Input(namespace);
            input.validate(parser);
            return input;
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
            return null;
        }

    }

    /**
     * This method serves as entry point to the teamscale-upload application.
     */
    public static void main(String[] args) throws Exception {
        Input input = parseArguments(args);

        Map<String, Set<File>> formatToFiles =
                ReportPatternUtils.resolveInputFilePatterns(input.inputFile, input.files, input.format);

        OkHttpClient client = OkHttpClientUtils.createClient(input.validateSsl);
        try {
            performUpload(client, formatToFiles, input);
        } finally {
            // we must shut down OkHttp as otherwise it will leave threads running and
            // prevent JVM shutdown
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
    }

    private static void performUpload(OkHttpClient client, Map<String, Set<File>> formatToFiles, Input input)
            throws IOException {
        String sessionId = openSession(client, input, formatToFiles.keySet());
        for (String format : formatToFiles.keySet()) {
            Set<File> filesFormFormat = formatToFiles.get(format);
            sendRequestForFormat(client, input, format, filesFormFormat, sessionId);
        }
        closeSession(client, input, sessionId);
    }

    private static String openSession(OkHttpClient client, Input input, Collection<String> formats) throws IOException {
        HttpUrl.Builder builder = input.url.newBuilder()
                .addPathSegments("api/projects").addPathSegment(input.project)
                .addPathSegments("external-analysis/session")
                .addQueryParameter("partition", input.partition);

        String revision = handleRevisionAndBranchTimestamp(input, builder);

        String message = input.message;
        if (message == null) {
            message = MessageUtils.createDefaultMessage(revision, input.partition, formats);

        }
        for (String additionalLine : input.additionalMessageLines) {
            //noinspection StringConcatenationInLoop
            message += "\n" + additionalLine.trim();
        }
        builder.addQueryParameter("message", message);

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(input.username, input.accessKey))
                .url(url)
                .post(EMPTY_BODY)
                .build();

        System.out.println("Opening upload session");
        String sessionId = sendRequest(client, input, url, request);
        if (sessionId == null) {
            fail("Could not open session.");
        }
        System.out.println("Session ID: " + sessionId);
        return sessionId;
    }

    /**
     * Adds either a revision or t parameter to the given builder, based on the input.
     * <p>
     * We track revision or branch:timestamp for the session as it should be the same for all uploads.
     *
     * @return the revision or branch:timestamp coordinate used.
     */
    private static String handleRevisionAndBranchTimestamp(Input input, HttpUrl.Builder builder) {
        if (input.commit != null) {
            builder.addQueryParameter("revision", input.commit);
            return input.commit;
        } else if (input.timestamp != null) {
            builder.addQueryParameter("t", input.timestamp);
            return input.timestamp;
        } else if (input.autoDetectCommit) {
            String commit = detectCommit();
            if (commit == null) {
                fail("Failed to automatically detect the commit. Please specify it manually via --commit or --branch-and-timestamp");
            }
            builder.addQueryParameter("revision", commit);
            return commit;
        } else {
            builder.addQueryParameter("t", "HEAD");
            return "HEAD";
        }
    }

    private static void closeSession(OkHttpClient client, Input input, String sessionId) throws IOException {
        HttpUrl.Builder builder = input.url.newBuilder()
                .addPathSegments("api/projects").addPathSegment(input.project)
                .addPathSegments("external-analysis/session")
                .addPathSegment(sessionId);

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(input.username, input.accessKey))
                .url(url)
                .post(EMPTY_BODY)
                .build();
        System.out.println("Closing upload session");
        sendRequest(client, input, url, request);
    }


    private static void sendRequestForFormat(OkHttpClient client, Input input, String format,
                                             Set<File> fileList, String sessionId)
            throws IOException {
        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        for (File file : fileList) {
            multipartBodyBuilder.addFormDataPart("report", file.getName(),
                    RequestBody.create(MediaType.get("application/octet-stream"), file));
        }

        RequestBody requestBody = multipartBodyBuilder.build();

        HttpUrl.Builder builder = input.url.newBuilder()
                .addPathSegments("api/projects")
                .addPathSegment(input.project)
                .addPathSegments("external-analysis/session")
                .addPathSegment(sessionId)
                .addPathSegment("report")
                .addQueryParameter("format", format);

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(input.username, input.accessKey))
                .url(url)
                .post(requestBody)
                .build();

        System.out.println("Uploading reports for format " + format);
        sendRequest(client, input, url, request);
    }

    private static String sendRequest(OkHttpClient client, Input input, HttpUrl url, Request request) throws IOException {

        try (Response response = client.newCall(request).execute()) {
            handleErrors(response, input);
            System.out.println("Successful");
            return readBodySafe(response);
        } catch (UnknownHostException e) {
            fail("The host " + url + " could not be resolved. Please ensure you have no typo and that" +
                    " this host is reachable from this server. " + e.getMessage());
        } catch (ConnectException e) {
            fail("The URL " + url + " refused a connection. Please ensure that you have no typo and that" +
                    " this endpoint is reachable and not blocked by firewalls. " + e.getMessage());
        }

        return null;
    }

    private static void handleErrors(Response response, Input input) {
        if (response.isRedirect()) {
            String location = response.header("Location");
            if (location == null) {
                location = "<server did not provide a location header>";
            }
            fail("You provided an incorrect URL. The server responded with a redirect to " +
                            "'" + location + "'." +
                            " This may e.g. happen if you used HTTP instead of HTTPS." +
                            " Please use the correct URL for Teamscale instead.",
                    response);
        }

        if (response.code() == 401) {
            HttpUrl editUserUrl = input.url.newBuilder().addPathSegments("admin.html#users").addQueryParameter("action", "edit")
                    .addQueryParameter("username", input.username).build();
            fail("You provided incorrect credentials." +
                            " Either the user '" + input.username + "' does not exist in Teamscale" +
                            " or the access key you provided is incorrect." +
                            " Please check both the username and access key in Teamscale under Admin > Users:" +
                            " " + editUserUrl +
                            "\nPlease use the user's access key, not their password.",
                    response);
        }

        if (response.code() == 403) {
            // can't include a URL to the corresponding Teamscale screen since that page does not support aliases
            // and the user may have provided an alias, so we'd be directing them to a red error page in that case
            fail("The user user '" + input.username + "' is not allowed to upload data to the Teamscale project '" + input.project + "'." +
                            " Please grant this user the 'Perform External Uploads' permission in Teamscale" +
                            " under Project Configuration > Projects by clicking on the button showing three" +
                            " persons next to project '" + input.project + "'.",
                    response);
        }

        if (response.code() == 404) {
            HttpUrl projectPerspectiveUrl = input.url.newBuilder().addPathSegments("project.html").build();
            fail("The project with ID or alias '" + input.project + "' does not seem to exist in Teamscale." +
                            " Please ensure that you used the project ID or the project alias, NOT the project name." +
                            " You can see the IDs of all projects at " + projectPerspectiveUrl +
                            "\nPlease also ensure that the Teamscale URL is correct and no proxy is required to access it.",
                    response);
        }

        if (!response.isSuccessful()) {
            fail("Unexpected response from Teamscale", response);
        }
    }

    /**
     * Print error message and server response, then exit program
     */
    public static void fail(String message, Response response) {
        fail("Upload to Teamscale failed:\n\n" + message + "\n\nTeamscale's response:\n" +
                response.toString() + "\n" + readBodySafe(response));
    }

    /**
     * Print error message and exit the program
     */
    public static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    private static String readBodySafe(Response response) {
        try {
            ResponseBody body = response.body();
            if (body == null) {
                return "<no response body>";
            }
            return body.string();
        } catch (IOException e) {
            return "Failed to read response body: " + e.getMessage();
        }
    }
}
