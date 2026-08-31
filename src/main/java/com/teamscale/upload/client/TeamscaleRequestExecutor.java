package com.teamscale.upload.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

import com.teamscale.upload.CommonCommandLineOptions;
import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.utils.OkHttpUtils;
import com.teamscale.upload.utils.TeamscaleUrlUtils;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Performs the requests that an upload to Teamscale requires and takes care of
 * retrying after failed attempts plus handling error responses.
 */
public class TeamscaleRequestExecutor {

	/**
	 * An upload to Teamscale, which may consist of more than one request.
	 */
	@FunctionalInterface
	public interface Upload {

		/** Performs the upload with the given client. */
		void perform(OkHttpClient client) throws IOException;
	}

	/**
	 * Performs an upload to Teamscale using a {@link OkHttpClient}. The upload is retried if it fails
	 * due to a transient network error.
	 */
	public static void performUpload(CommonCommandLineOptions commandLine, Upload upload) throws IOException {
		OkHttpClient client = OkHttpUtils.createClient(commandLine.validateSsl, commandLine.proxy,
				commandLine.getKeyStorePath(), commandLine.getKeyStorePassword(), commandLine.getTimeoutInSeconds());
		try {
			performWithRetry(client, upload, commandLine.maxAttempts);
		} catch (SSLHandshakeException e) {
			handleSslConnectionFailure(commandLine, e);
		} finally {
			// we must shut down OkHttp as otherwise it will leave threads running and
			// prevent JVM shutdown
			client.dispatcher().executorService().shutdownNow();
			client.connectionPool().evictAll();
		}
	}

	/**
	 * Performs the given upload, retrying it up to {@code maxAttempts} times if it
	 * fails with an {@link IOException}.
	 * <p>
	 * An {@link SSLHandshakeException} is not retried but rethrown, as retrying will
	 * not make a broken certificate setup work. If all attempts fail, the program is
	 * terminated with an error message.
	 */
	private static void performWithRetry(OkHttpClient client, Upload upload, int maxAttempts) throws IOException {
		for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
			try {
				upload.perform(client);
				return;
			} catch (SSLHandshakeException e) {
				throw e;
			} catch (IOException e) {
				if (attemptNumber < maxAttempts) {
					LogUtils.warn("Failed attempt " + attemptNumber + " / " + maxAttempts + ": " + e.getMessage());
				} else {
					LogUtils.failWithoutStackTrace(
							"Upload failed after " + maxAttempts + " attempt(s): " + e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * Sends the given request, handles the common error cases and returns the
	 * response body.
	 */
	static String sendRequest(OkHttpClient client, CommonCommandLineOptions commandLine, HttpUrl url, Request request)
			throws IOException {

		HttpUrl host = new HttpUrl.Builder().scheme(url.scheme()).host(url.host()).port(url.port()).build();

		try (Response response = client.newCall(request).execute()) {
			SafeResponse safeResponse = new SafeResponse(response);
			handleErrors(safeResponse, commandLine);
			LogUtils.debug("Request successful: %s %s (HTTP %d)", request.method(), url,
					safeResponse.unsafeResponse.code());
			return safeResponse.body;
		} catch (UnknownHostException e) {
			LogUtils.failWithoutStackTrace(
					"The host " + host + " could not be resolved. Please ensure you have no typo and that"
							+ " this host is reachable from this server.",
					e);
		} catch (FileNotFoundException e) {
			LogUtils.failWithoutStackTrace(
					"Could not find the specified report file for uploading. Please ensure that you have no typo"
							+ " in the file path and that the specified report file is readable.",
					e);
		} catch (ConnectException e) {
			throw new IOException(
					"The host " + host + " refused a connection. Please ensure that you have no typo and that"
							+ " this endpoint is reachable and not blocked by firewalls.",
					e);
		} catch (SocketTimeoutException e) {
			throw new IOException(
					"Request timeout reached. Consider setting a higher timeout value using the '--timeout' option.",
					e);
		}

		return null;
	}

	/**
	 * Terminates the program with an error message explaining why the SSL
	 * connection to Teamscale could not be established.
	 */
	private static void handleSslConnectionFailure(CommonCommandLineOptions commandLine, SSLHandshakeException e) {
		if (commandLine.getKeyStorePath() != null) {
			LogUtils.failWithoutStackTrace("Failed to connect via HTTPS to " + commandLine.url
					+ "\nYou enabled certificate validation and provided a keystore with certificates"
					+ " that should be considered valid. Still, the connection failed."
					+ " Most likely, you did not provide the correct certificates in the keystore"
					+ " or some certificates are missing from it."
					+ "\nPlease also ensure that your Teamscale instance is reachable under " + commandLine.url
					+ " and that it is configured for HTTPS, not HTTP. E.g. open that URL in your"
					+ " browser and verify that you can connect successfully."
					+ "\n\nIf you want to accept self-signed or broken certificates without an error"
					+ " you can use --insecure.", e);
		} else if (commandLine.validateSsl) {
			LogUtils.failWithoutStackTrace("Failed to connect via HTTPS to " + commandLine.url
					+ "\nYou enabled certificate validation. Most likely, your certificate"
					+ " is either self-signed or your root CA's certificate is not known to"
					+ " teamscale-upload. Please provide the path to a keystore that contains"
					+ " the necessary public certificates that should be trusted by"
					+ " teamscale-upload via --trusted-keystore. You can create a Java keystore"
					+ " with your certificates as described here:"
					+ " https://docs.teamscale.com/howto/connecting-via-https/#using-self-signed-certificates"
					+ "\nPlease also ensure that your Teamscale instance is reachable under " + commandLine.url
					+ " and that it is configured for HTTPS, not HTTP. E.g. open that URL in your"
					+ " browser and verify that you can connect successfully."
					+ "\n\nIf you want to accept self-signed or broken certificates without an error"
					+ " you can use --insecure.", e);
		} else {
			LogUtils.failWithoutStackTrace("Failed to connect via HTTPS to " + commandLine.url
					+ "\nPlease ensure that your Teamscale instance is reachable under " + commandLine.url
					+ " and that it is configured for HTTPS, not HTTP. E.g. open that URL in your"
					+ " browser and verify that you can connect successfully."
					+ "\n\nIf you want to accept self-signed or broken certificates without an error"
					+ " you can use --insecure.", e);
		}
	}

	private static void handleErrors(SafeResponse response, CommonCommandLineOptions commandLine) throws IOException {
		if (response.unsafeResponse.isRedirect()) {
			String location = response.unsafeResponse.header("Location");
			if (location == null) {
				location = "<server did not provide a location header>";
			}
			LogUtils.fail("You provided an incorrect URL. The server responded with a redirect to " + "'" + location
					+ "'." + " This may e.g. happen if you used HTTP instead of HTTPS."
					+ " Please use the correct URL for Teamscale instead.", response);
		}

		if (response.unsafeResponse.code() == 401) {
			String editUserUrl = TeamscaleUrlUtils.getEditUserUrl(commandLine.url, commandLine.username);
			LogUtils.fail("You provided incorrect credentials." + " Either the user '" + commandLine.username
					+ "' does not exist in Teamscale" + " or the access key you provided is incorrect."
					+ " Please check both the username and access key in Teamscale under Admin > Users: " + editUserUrl
					+ "\nPlease use the user's access key, not their password.", response);
		}

		if (response.unsafeResponse.code() == 403) {
			String projectPermissionUrl = TeamscaleUrlUtils.getProjectPermissionUrl(commandLine.url,
					commandLine.project);
			LogUtils.fail("The user user '" + commandLine.username
					+ "' is not allowed to upload data to the Teamscale project '" + commandLine.project + "'."
					+ " Please grant this user the 'Perform External Uploads' permission in Teamscale"
					+ " under Project Configuration > Projects: " + projectPermissionUrl
					+ "\nE.g. by assigning them the 'Build' role for that project.", response);
		}

		if (response.unsafeResponse.code() == 404) {
			handleError404(response, commandLine);
		}

		if (response.unsafeResponse.code() == 400) {
			LogUtils.fail("Teamscale rejected the upload request as invalid.", response);
		}

		if (!response.unsafeResponse.isSuccessful()) {
			int code = response.unsafeResponse.code();
			if (code >= 500) {
				String url = response.unsafeResponse.request().url().toString();
				throw new IOException("Server error (HTTP " + code + ") from " + url + ": " + response.body);
			}
			LogUtils.fail("Unexpected response from Teamscale", response);
		}
	}

	private static void handleError404(SafeResponse response, CommonCommandLineOptions commandLine) {
		if (responseBodyIndicatesInvalidRevision(response)) {
			LogUtils.fail("The revision '" + commandLine.getRevision() + "' is not known to Teamscale or the version"
					+ " control system(s) you configured in the Teamscale project '" + commandLine.project + "'."
					+ " Please ensure that you used a valid version control revision:"
					+ " (e.g. a Git SHA1, SVN revision number or TFS changeset ID) and"
					+ " that the checked out revision is also present in your central"
					+ " version control system and not just locally on this computer"
					+ " (e.g. your Git commit has been pushed).", response);
		}

		String message = "The project with ID '" + commandLine.project + "' does not seem to exist in Teamscale."
				+ " Please ensure that you used one of the project IDs, NOT the project name."
				+ " You can see the IDs of all projects at "
				+ TeamscaleUrlUtils.getProjectPerspectiveUrl(commandLine.url)
				+ "\nPlease also ensure that the Teamscale URL is correct and no proxy is required to access it.";
		String notFoundHint = commandLine.getAdditionalHttp404Hint();
		if (notFoundHint != null) {
			message += "\n" + notFoundHint;
		}
		LogUtils.fail(message, response);
	}

	private static boolean responseBodyIndicatesInvalidRevision(SafeResponse response) {
		return response.body.contains("Revision") && response.body.contains("available VCS repositories");
	}
}
