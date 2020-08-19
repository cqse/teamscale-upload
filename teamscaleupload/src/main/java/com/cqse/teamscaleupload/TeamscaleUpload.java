package com.cqse.teamscaleupload;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TeamscaleUpload {

    private static class Input {
        public final String project;
        public final String username;
        public final String accessKey;
        public final String partition;
        public final String format;
        public final String commit;
        public final String timestamp;
        public final List<String> files;
        public final String inputFile;
        public final String url;

        private Input(Namespace namespace) {
            this.project = namespace.getString("project");
            this.username = namespace.getString("user");
            this.accessKey = namespace.getString("accesskey");
            this.partition = namespace.getString("partition");
            this.format = namespace.getString("format").toUpperCase();
            this.commit = namespace.getString("commit");
            this.timestamp = namespace.getString("branch_and_timestamp");
            this.files = namespace.getList("files");
            this.inputFile = namespace.getString("input");
            this.url = namespace.getString("domain");
        }

        public void validate(ArgumentParser parser) throws ArgumentParserException {
            if (commit != null && timestamp != null) {
                throw new ArgumentParserException("You may provide either a commit or a timestamp, not both", parser);
            }

            if (files== null && inputFile == null) {
                throw new ArgumentParserException("You must either specify the paths of the coverage files as plain " +
                        "arguments or provide them in an input file, see help for more information", parser);
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
        parser.addArgument("-d", "--domain").type(String.class).metavar("DOMAIN").required(true)
                .help("The url of the teamscale instance, e.g. demo.teamscale.com");
        parser.addArgument("-i", "--input").type(String.class).metavar("INPUT").required(false)
                .help("A file which contains the coverage file paths or patterns to be added. The entries are separated " +
                        "by line breaks. If files are specified as plain arguments, they are added to the files which " +
                        "are given in this file.");
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

        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        for (File file : fileList) {
            multipartBodyBuilder.addFormDataPart("report", file.getName(),
                    RequestBody.create(MediaType.get("application/octet-stream"), file));
        }
        RequestBody requestBody = multipartBodyBuilder.build();

        HttpUrl.Builder builder = new HttpUrl.Builder().scheme("https").host(input.url)
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
        if (!response.isSuccessful()) {
            String message;
            switch(response.code()) {
                case 401: message = "The provided credentials were invalid. Use the access key for your user " +
                        "which can be found under '" + input.url + "/user.html#access-key'. " +
                        "The access key is not the same as the password.";
                        break;
                case 403: message = "The authentication was successful, but the user does not have the necessary " +
                        "permission to upload coverage for this project.";
                case 404: message = "The project '" + input.project + "' does not exist.";
                    break;
                default:
                    message = "Unexpected code";

            }
            throw new IOException(message + " " + response);
        }

        System.out.println(response.body().string());
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