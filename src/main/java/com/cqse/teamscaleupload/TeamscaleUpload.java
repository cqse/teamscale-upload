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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class TeamscaleUpload {

    private static class Input {
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
        public final String inputFile;
        public final Boolean validateSsl;

        private Input(Namespace namespace) {
            this.project = namespace.getString("project");
            this.username = namespace.getString("user");
            this.accessKey = namespace.getString("accesskey");
            this.partition = namespace.getString("partition");
            this.format = namespace.getString("format").toUpperCase();
            this.commit = namespace.getString("commit");
            this.autoDetectedCommit = namespace.getBoolean("detect_commit");
            this.timestamp = namespace.getString("branch_and_timestamp");
            this.files = namespace.getList("files");
            this.url = HttpUrl.parse(namespace.getString("server"));
            this.inputFile = namespace.getString("input");
            this.validateSsl = namespace.getBoolean("validate_ssl");
        }

        public void validate(ArgumentParser parser) throws ArgumentParserException {
            if (url == null) {
                throw new ArgumentParserException("You provided an invalid URL in the --server option", parser);
            }

            if (hasMoreThanOneCommitOptionSet()) {
                throw new ArgumentParserException("You used more than one of --detect-commit, --commit and --timestamp." +
                        " You must choose one of these three options to specify the commit for which you would like to" +
                        " upload data to Teamscale", parser);
            }

            if (files == null && inputFile == null) {
                throw new ArgumentParserException("You must either specify the paths of the coverage files as plain " +
                        "arguments or provide them in an input file, see help for more information", parser);
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
        parser.addArgument("-f", "--format").type(String.class).metavar("FORMAT").required(true)
                .help("The file format of the uploaded report files." +
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
                .help("A file which contains the coverage file paths or patterns to be added. The entries are separated " +
                        "by line breaks. If files are specified as plain arguments, they are added to the files which " +
                        "are given in this file.");
        parser.addArgument("--detect-commit").action(Arguments.storeTrue()).required(false)
                .help("Tries to automatically detect the code commit to which to upload from environment variables or" +
                        " a Git or SVN checkout in the current working directory. If guessing fails, the upload will fail." +
                        " This feature supports many common CI tools like Jenkins, GitLab, GitHub Actions, Travis CI etc.");
        parser.addArgument("--validate-ssl").action(Arguments.storeTrue()).required(false)
                .help("By default, SSL certificates are accepted without validation, which makes using this tool with self-signed" +
                        " certificates easier. This flag enables validation.");
        parser.addArgument("files").metavar("FILES").type(String.class).nargs("*").
                help("Path(s) or pattern(s) of the report files to upload. Alternatively, you may provide input files via -i or --input");

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

    public static void main(String[] args) throws Exception {
        Input input = parseArguments(args);

        FilePatternResolver resolver = new FilePatternResolver();

        List<String> fileNames = new ArrayList<>();

        if (input.files != null) {
            fileNames.addAll(input.files);
        }

        if (input.inputFile != null) {
            fileNames.addAll(readFileNamesFromInputFile(input.inputFile));
        }

        List<File> fileList = new ArrayList<>();
        for (String file : fileNames) {
            fileList.addAll(resolver.resolveToMultipleFiles("files", file));
        }

        if (fileList.isEmpty()) {
            fail("Could not find any files to upload. You must provide patterns that match at least one file.");
        }

        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        for (File file : fileList) {
            multipartBodyBuilder.addFormDataPart("report", file.getName(),
                    RequestBody.create(MediaType.get("application/octet-stream"), file));
        }
        RequestBody requestBody = multipartBodyBuilder.build();

        HttpUrl.Builder builder = input.url.newBuilder()
                .addPathSegments("api/projects").addPathSegment(input.project)
                .addPathSegments("external-analysis/session/auto-create/report")
                .addQueryParameter("partition", input.partition)
                .addQueryParameter("format", input.format);

        if (input.commit != null) {
            builder.addQueryParameter("revision", input.commit);
        } else if (input.timestamp != null) {
            builder.addQueryParameter("t", input.timestamp);
        } else if (input.autoDetectedCommit) {
            String commit = detectCommit();
            if (commit == null) {
                fail("Failed to automatically detect the commit. Please specify it manually via --commit or --timestamp");
            }
            builder.addQueryParameter("revision", commit);
        } else {
            builder.addQueryParameter("t", "master:HEAD");
        }

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(input.username, input.accessKey))
                .url(url)
                .post(requestBody)
                .build();

        OkHttpClient client = OkHttpClientUtils.createClient(input.validateSsl);

        Response response = client.newCall(request).execute();

        handleCommonErrors(response, input);

        System.out.println("Upload to Teamscale successful");
        System.exit(0);
    }

    private static void handleCommonErrors(Response response, Input input) {
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

    private static void fail(String message, Response response) {
        fail("Upload to Teamscale failed:\n\n" + message + "\n\nTeamscale's response:\n" +
                response.toString() + "\n" + readBodySafe(response));
    }

    private static void fail(String message) {
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

    private static List<String> readFileNamesFromInputFile(String inputFilePath) {
        try {
            return Files.readAllLines(Paths.get(inputFilePath));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }
}
