package com.teamscale.upload.client;

import java.io.File;
import java.io.IOException;

import javax.net.ssl.SSLHandshakeException;

import com.teamscale.upload.SbomCommandLine;
import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.utils.OkHttpUtils;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Client to upload a Software Bill of Materials (SBOM) to Teamscale.
 * <p>
 * Unlike the upload of external analysis reports (see {@link TeamscaleClient}),
 * this uses a dedicated endpoint and does not open an external-analysis session:
 * a single request stores the SBOM.
 */
public class SbomUploadClient {

	/**
	 * The name of the multipart form part that holds the SBOM file.
	 */
	private static final String FILE_PARAMETER_NAME = "file";

	/** Performs the upload of the given SBOM file. */
	public static void performUpload(SbomCommandLine commandLine, File sbomFile) throws IOException {
		OkHttpClient client = OkHttpUtils.createClient(commandLine.validateSsl, commandLine.proxy,
				commandLine.getKeyStorePath(), commandLine.getKeyStorePassword(), commandLine.getTimeoutInSeconds());
		try {
			RetryUtils.performWithRetry(commandLine.maxAttempts, () -> sendUploadRequest(client, commandLine, sbomFile));
		} catch (SSLHandshakeException e) {
			TeamscaleClient.handleSslConnectionFailure(commandLine, e);
		} finally {
			// we must shut down OkHttp as otherwise it will leave threads running and
			// prevent JVM shutdown
			client.dispatcher().executorService().shutdownNow();
			client.connectionPool().evictAll();
		}
	}

	private static void sendUploadRequest(OkHttpClient client, SbomCommandLine commandLine, File sbomFile)
			throws IOException {
		HttpUrl url = buildUploadUrl(commandLine);

		RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
				.addFormDataPart(FILE_PARAMETER_NAME, sbomFile.getName(),
						RequestBody.create(sbomFile, MediaType.get("application/octet-stream")))
				.build();

		Request request = new Request.Builder()
				.header("Authorization", Credentials.basic(commandLine.username, commandLine.accessKey)).url(url)
				.post(requestBody).build();

		LogUtils.info("Uploading SBOM " + sbomFile.getName() + " for build " + commandLine.buildName + " version "
				+ commandLine.buildVersion);
		TeamscaleClient.sendRequest(client, commandLine, url, request);
	}

	/**
	 * Builds the URL of the SBOM upload endpoint.
	 * <p>
	 * Note that this endpoint is not part of Teamscale's versioned public API, so
	 * the URL must not contain an API version segment (as opposed to the report
	 * upload performed by {@link TeamscaleClient}).
	 */
	private static HttpUrl buildUploadUrl(SbomCommandLine commandLine) {
		return commandLine.url.newBuilder().addPathSegments("api/projects").addPathSegment(commandLine.project)
				.addPathSegment("vulnerability-report").addQueryParameter("build-name", commandLine.buildName)
				.addQueryParameter("version", commandLine.buildVersion)
				.addQueryParameter("revision", commandLine.getRevision()).build();
	}
}
