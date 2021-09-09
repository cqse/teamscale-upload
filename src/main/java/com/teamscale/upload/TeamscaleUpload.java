package com.teamscale.upload;

import com.teamscale.upload.autodetect_revision.AutodetectCommitUtils;
import com.teamscale.upload.resolve.ReportPatternUtils;
import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.utils.MessageUtils;
import com.teamscale.upload.utils.OkHttpUtils;
import com.teamscale.upload.utils.SafeResponse;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLHandshakeException;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Main class of the teamscale-upload project.
 */
public class TeamscaleUpload {

    /**
     * This method serves as entry point to the teamscale-upload application.
     */
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = CommandLine.parseArguments(args);

        if (commandLine.printStackTrace) {
            LogUtils.enableStackTracePrintingForKnownErrors();
        }

        Map<String, Set<File>> formatToFiles =
                ReportPatternUtils.resolveInputFilePatterns(commandLine.inputFile, commandLine.files, commandLine.format);

        OkHttpClient client = OkHttpUtils.createClient(commandLine.validateSsl, commandLine.getKeyStorePath(), commandLine.getKeyStorePassword());
        try {
            performUpload(client, formatToFiles, commandLine);
        } catch (SSLHandshakeException e) {
            handleSslConnectionFailure(commandLine, e);
        } finally {
            // we must shut down OkHttp as otherwise it will leave threads running and
            // prevent JVM shutdown
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
    }

    private static void handleSslConnectionFailure(CommandLine commandLine, SSLHandshakeException e) {
        if (commandLine.getKeyStorePath() != null) {
            LogUtils.failWithoutStackTrace("Failed to connect via HTTPS to " + commandLine.url +
                    "\nYou enabled certificate validation and provided a keystore with certificates" +
                    " that should be considered valid. Still, the connection failed." +
                    " Most likely, you did not provide the correct certificates in the keystore" +
                    " or some certificates are missing from it." +
                    "\nPlease also ensure that your Teamscale instance is reachable under " + commandLine.url +
                    " and that it is configured for HTTPS, not HTTP. E.g. open that URL in your" +
                    " browser and verify that you can connect successfully." +
                    "\n\nIf you want to accept self-signed or broken certificates without an error" +
                    " you can use --insecure.", e);
        } else if (commandLine.validateSsl) {
            LogUtils.failWithoutStackTrace("Failed to connect via HTTPS to " + commandLine.url +
                    "\nYou enabled certificate validation. Most likely, your certificate" +
                    " is either self-signed or your root CA's certificate is not known to" +
                    " teamscale-upload. Please provide the path to a keystore that contains" +
                    " the necessary public certificates that should be trusted by" +
                    " teamscale-upload via --trusted-keystore. You can create a Java keystore" +
                    " with your certificates as described here:" +
                    " https://docs.teamscale.com/howto/connecting-via-https/#using-self-signed-certificates" +
                    "\nPlease also ensure that your Teamscale instance is reachable under " + commandLine.url +
                    " and that it is configured for HTTPS, not HTTP. E.g. open that URL in your" +
                    " browser and verify that you can connect successfully." +
                    "\n\nIf you want to accept self-signed or broken certificates without an error" +
                    " you can use --insecure.", e);
        } else {
            LogUtils.failWithoutStackTrace("Failed to connect via HTTPS to " + commandLine.url +
                    "\nPlease ensure that your Teamscale instance is reachable under " + commandLine.url +
                    " and that it is configured for HTTPS, not HTTP. E.g. open that URL in your" +
                    " browser and verify that you can connect successfully." +
                    "\n\nIf you want to accept self-signed or broken certificates without an error" +
                    " you can use --insecure.", e);
        }
    }

    private static void performUpload(OkHttpClient client, Map<String, Set<File>> formatToFiles, CommandLine commandLine)
            throws IOException {
        String sessionId = openSession(client, commandLine, formatToFiles.keySet());
        for (String format : formatToFiles.keySet()) {
            Set<File> filesFormFormat = formatToFiles.get(format);
            sendRequestForFormat(client, commandLine, format, filesFormFormat, sessionId);
        }
        closeSession(client, commandLine, sessionId);
    }

    private static String openSession(OkHttpClient client, CommandLine commandLine, Collection<String> formats) throws IOException {
        HttpUrl.Builder builder = commandLine.url.newBuilder()
                .addPathSegments("api/projects").addPathSegment(commandLine.project)
                .addPathSegments("external-analysis/session")
                .addQueryParameter("partition", commandLine.partition);

        String revision = handleRevisionAndBranchTimestamp(commandLine, builder);

        String message = commandLine.message;
        if (message == null) {
            message = MessageUtils.createDefaultMessage(revision, commandLine.partition, formats);

        }
        for (String additionalLine : commandLine.additionalMessageLines) {
            //noinspection StringConcatenationInLoop
            message += "\n" + additionalLine.trim();
        }
        builder.addQueryParameter("message", message);

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(commandLine.username, commandLine.accessKey))
                .url(url)
                .post(OkHttpUtils.EMPTY_BODY)
                .build();

        System.out.println("Opening upload session");
        String sessionId = sendRequest(client, commandLine, url, request);
        if (sessionId == null) {
            LogUtils.fail("Could not open session.");
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
    private static String handleRevisionAndBranchTimestamp(CommandLine commandLine, HttpUrl.Builder builder) {
        if (commandLine.commit != null) {
            builder.addQueryParameter("revision", commandLine.commit);
            return commandLine.commit;
        } else if (commandLine.timestamp != null) {
            builder.addQueryParameter("t", commandLine.timestamp);
            return commandLine.timestamp;
        } else {
            // auto-detect if neither option is given
            String commit = AutodetectCommitUtils.detectCommit();
            if (commit == null) {
                LogUtils.fail("Failed to automatically detect the commit. Please specify it manually via --commit or --branch-and-timestamp");
            }
            builder.addQueryParameter("revision", commit);
            return commit;
        }
    }

    private static void closeSession(OkHttpClient client, CommandLine commandLine, String sessionId) throws IOException {
        HttpUrl.Builder builder = commandLine.url.newBuilder()
                .addPathSegments("api/projects").addPathSegment(commandLine.project)
                .addPathSegments("external-analysis/session")
                .addPathSegment(sessionId);

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(commandLine.username, commandLine.accessKey))
                .url(url)
                .post(OkHttpUtils.EMPTY_BODY)
                .build();
        System.out.println("Closing upload session");
        sendRequest(client, commandLine, url, request);
    }


    private static void sendRequestForFormat(OkHttpClient client, CommandLine commandLine, String format,
                                             Set<File> fileList, String sessionId)
            throws IOException {
        MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        for (File file : fileList) {
            multipartBodyBuilder.addFormDataPart("report", file.getName(),
                    RequestBody.create(MediaType.get("application/octet-stream"), file));
        }

        RequestBody requestBody = multipartBodyBuilder.build();

        HttpUrl.Builder builder = commandLine.url.newBuilder()
                .addPathSegments("api/projects")
                .addPathSegment(commandLine.project)
                .addPathSegments("external-analysis/session")
                .addPathSegment(sessionId)
                .addPathSegment("report")
                .addQueryParameter("format", format);

        HttpUrl url = builder.build();

        Request request = new Request.Builder()
                .header("Authorization", Credentials.basic(commandLine.username, commandLine.accessKey))
                .url(url)
                .post(requestBody)
                .build();

        System.out.println("Uploading reports for format " + format);
        sendRequest(client, commandLine, url, request);
    }

    private static String sendRequest(OkHttpClient client, CommandLine commandLine, HttpUrl url, Request request) throws IOException {

        try (Response response = client.newCall(request).execute()) {
            SafeResponse safeResponse = new SafeResponse(response);
            handleErrors(safeResponse, commandLine);
            System.out.println("Successful");
            return safeResponse.body;
        } catch (UnknownHostException e) {
            LogUtils.failWithoutStackTrace("The host " + url + " could not be resolved. Please ensure you have no typo and that" +
                    " this host is reachable from this server.", e);
        } catch (ConnectException e) {
            LogUtils.failWithoutStackTrace("The URL " + url + " refused a connection. Please ensure that you have no typo and that" +
                    " this endpoint is reachable and not blocked by firewalls.", e);
        }

        return null;
    }

    private static void handleErrors(SafeResponse response, CommandLine commandLine) {
        if (response.unsafeResponse.isRedirect()) {
            String location = response.unsafeResponse.header("Location");
            if (location == null) {
                location = "<server did not provide a location header>";
            }
            LogUtils.fail("You provided an incorrect URL. The server responded with a redirect to " +
                            "'" + location + "'." +
                            " This may e.g. happen if you used HTTP instead of HTTPS." +
                            " Please use the correct URL for Teamscale instead.",
                    response);
        }

        if (response.unsafeResponse.code() == 401) {
            HttpUrl editUserUrl = commandLine.url.newBuilder().addPathSegments("admin.html#users").addQueryParameter("action", "edit")
                    .addQueryParameter("username", commandLine.username).build();
            LogUtils.fail("You provided incorrect credentials." +
                            " Either the user '" + commandLine.username + "' does not exist in Teamscale" +
                            " or the access key you provided is incorrect." +
                            " Please check both the username and access key in Teamscale under Admin > Users:" +
                            " " + editUserUrl +
                            "\nPlease use the user's access key, not their password.",
                    response);
        }

        if (response.unsafeResponse.code() == 403) {
            // can't include a URL to the corresponding Teamscale screen since that page does not support aliases
            // and the user may have provided an alias, so we'd be directing them to a red error page in that case
            LogUtils.fail("The user user '" + commandLine.username + "' is not allowed to upload data to the Teamscale project '" + commandLine.project + "'." +
                            " Please grant this user the 'Perform External Uploads' permission in Teamscale" +
                            " under Project Configuration > Projects by clicking on the button showing three" +
                            " persons next to project '" + commandLine.project + "'.",
                    response);
        }

        if (response.unsafeResponse.code() == 404) {
            handleError404(response, commandLine);
        }

        if (!response.unsafeResponse.isSuccessful()) {
            LogUtils.fail("Unexpected response from Teamscale", response);
        }
    }

    private static void handleError404(SafeResponse response, CommandLine commandLine) {
        if (responseBodyIndicatesInvalidRevision(response)) {
            LogUtils.fail("The revision '" + commandLine.commit + "' is not known to Teamscale or the version" +
                            " control system(s) you configured in the Teamscale project '" + commandLine.project + "'." +
                            " Please ensure that you used a valid version control revision:" +
                            " (e.g. a Git SHA1, SVN revision number or TFS changeset ID) and" +
                            " that the checked out revision is also present in your central" +
                            " version control system and not just locally on this computer" +
                            " (e.g. your Git commit has been pushed).",
                    response);
        }

        HttpUrl projectPerspectiveUrl = commandLine.url.newBuilder().addPathSegments("project.html").build();
        LogUtils.fail("The project with ID or alias '" + commandLine.project + "' does not seem to exist in Teamscale." +
                        " Please ensure that you used the project ID or the project alias, NOT the project name." +
                        " You can see the IDs of all projects at " + projectPerspectiveUrl +
                        "\nPlease also ensure that the Teamscale URL is correct and no proxy is required to access it.",
                response);
    }

    private static boolean responseBodyIndicatesInvalidRevision(SafeResponse response) {
        return response.body.contains("Revision")
                && response.body.contains("available VCS repositories");
    }

}
