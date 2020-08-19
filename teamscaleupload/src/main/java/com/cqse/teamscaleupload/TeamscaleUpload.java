package com.cqse.teamscaleupload;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        public final String timestamp;

        private Input(Namespace namespace) {
            this.project = namespace.getString("project");
            this.username = namespace.getString("user");
            this.accessKey = namespace.getString("accesskey");
            this.partition = namespace.getString("partition");
            this.format = namespace.getString("format").toUpperCase();
            this.commit = namespace.getString("commit");
            this.timestamp = namespace.getString("branch_and_timestamp");
        }

        public void validate(ArgumentParser parser) throws ArgumentParserException {
            if (commit != null && timestamp != null) {
                throw new ArgumentParserException("You may provide either a commit or a timestamp, not both", parser);
            }
        }
    }

    private static Input parseArguments(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload").build()
                .defaultHelp(true)
                .description("Upload coverage, findings, ... to Teamscale.");

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

    public static void main(String[] args) throws IOException {
        Input input = parseArguments(args);

        Path coverageFile = Paths.get("/home/k/proj/teamscale-upload/teamscaleupload/test.simple");

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("report", coverageFile.getFileName().toString(),
                        RequestBody.create(MediaType.get("application/octet-stream"), coverageFile.toFile()))
                .build();

        HttpUrl.Builder builder = new HttpUrl.Builder().scheme("https").host("demo.teamscale.com")
                .addPathSegments("api/projects").addPathSegment(input.project).addPathSegments("external-analysis/session/auto-create/report")
                .addQueryParameter("t", "master:HEAD")
                .addQueryParameter("partition", input.partition)
                .addQueryParameter("format", input.format);

        if (input.commit != null) {
            builder.addQueryParameter("revision", input.commit);
        }

        if (input.timestamp != null) {
            builder.addQueryParameter("t", input.timestamp);
        }

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(input.username, input.accessKey))
                .url(url)
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient.Builder().build();

        Response response = client.newCall(request).execute();

        handleCommonErrors(response, input);

        if (!response.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        System.out.println("Upload to Teamscale successful");
    }

    private static void handleCommonErrors(Response response, Input input) {
        if (response.code() == 401) {
            fail("You provided incorrect credentials." +
                            " Either the user '" + input.username + "' does not exist in Teamscale" +
                            " or the access key you provided is incorrect." +
                            " Please check both the username and access key in Teamscale under Admin > Users.",
                    response);
        }
    }

    private static void fail(String message, Response response) {
        System.err.println("Upload to Teamscale failed:\n\n" + message);
        System.err.println("\nTeamscale's response:\n" + response.toString() + "\n" + readBodySafe(response));
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