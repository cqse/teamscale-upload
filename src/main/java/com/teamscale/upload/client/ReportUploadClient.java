package com.teamscale.upload.client;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.teamscale.upload.ReportCommandLineOptions;
import com.teamscale.upload.autodetect_revision.AutodetectCommitUtils;
import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.utils.MessageUtils;
import com.teamscale.upload.utils.OkHttpUtils;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Client to upload external analysis reports to Teamscale.
 * <p>
 * The reports of one upload are inserted into Teamscale as a single commit. To
 * achieve that, they are not uploaded on their own but within an
 * external-analysis session, which is opened before and closed after them.
 */
public class ReportUploadClient {

	/** The version against which the API requests are performed. */
	private static final String MINIMUM_REQUIRED_API_VERSION = "v8.2";

	/** Performs the upload of the files. */
	public static void performUpload(ReportCommandLineOptions commandLine, Map<String, Set<File>> filesByFormat)
			throws IOException {
		TeamscaleRequestExecutor.performUpload(commandLine, client -> {
			if (filesByFormat.isEmpty()) {
				LogUtils.warn("There are no files to upload. Skipping upload.");
				return;
			}
			String sessionId = openSession(client, commandLine, filesByFormat.keySet());
			for (String format : filesByFormat.keySet()) {
				Set<File> filesForFormat = filesByFormat.get(format);
				sendRequestForFormat(client, commandLine, format, filesForFormat, sessionId);
			}
			closeSession(client, commandLine, sessionId);
		});
	}

	private static String openSession(OkHttpClient client, ReportCommandLineOptions commandLine,
			Collection<String> formats) throws IOException {
		HttpUrl.Builder builder = commandLine.url.newBuilder().addPathSegments("api")
				.addPathSegments(MINIMUM_REQUIRED_API_VERSION).addPathSegments("projects")
				.addPathSegment(commandLine.project).addPathSegments("external-analysis/session")
				.addQueryParameter("partition", commandLine.partition);

		String revision = handleRevisionAndBranchTimestamp(commandLine, builder);

		String message = commandLine.message;
		if (message == null) {
			message = MessageUtils.createDefaultMessage(revision, commandLine.partition, formats);

		}
		for (String additionalLine : commandLine.additionalMessageLines) {
			// noinspection StringConcatenationInLoop
			message += "\n" + additionalLine.trim();
		}
		builder.addQueryParameter("message", message);

		HttpUrl url = builder.build();

		Request request = new Request.Builder()
				.header("Authorization", Credentials.basic(commandLine.username, commandLine.accessKey)).url(url)
				.post(OkHttpUtils.EMPTY_BODY).build();

		LogUtils.debug("Opening upload session");
		String sessionId = TeamscaleRequestExecutor.sendRequest(client, commandLine, url, request);
		if (sessionId == null) {
			LogUtils.fail("Could not open session.");
		}
		LogUtils.debug("Session ID: " + sessionId);
		return sessionId;
	}

	/**
	 * Adds either a revision or t parameter to the given builder, based on the
	 * input.
	 * <p>
	 * We track revision or branch:timestamp for the session as it should be the
	 * same for all uploads.
	 *
	 * @return the revision or branch:timestamp coordinate used.
	 */
	private static String handleRevisionAndBranchTimestamp(ReportCommandLineOptions commandLine,
			HttpUrl.Builder builder) {
		if (commandLine.commit != null) {
			builder.addQueryParameter("revision", commandLine.commit);
			if (commandLine.repository != null) {
				// repository can be specified optionally when specifying a commit/revision
				builder.addQueryParameter("repository", commandLine.repository);
			}
			return commandLine.commit;
		} else if (commandLine.timestamp != null) {
			builder.addQueryParameter("t", commandLine.timestamp);
			return commandLine.timestamp;
		} else {
			// auto-detect if neither option is given
			String commit = AutodetectCommitUtils.detectCommit();
			if (commit == null) {
				LogUtils.fail(
						"Failed to automatically detect the commit. Please specify it manually via --commit or --branch-and-timestamp");
			}
			builder.addQueryParameter("revision", commit);
			return commit;
		}
	}

	private static void closeSession(OkHttpClient client, ReportCommandLineOptions commandLine, String sessionId)
			throws IOException {
		HttpUrl.Builder builder = commandLine.url.newBuilder().addPathSegments("api")
				.addPathSegments(MINIMUM_REQUIRED_API_VERSION).addPathSegments("projects")
				.addPathSegment(commandLine.project).addPathSegments("external-analysis/session")
				.addPathSegment(sessionId);

		HttpUrl url = builder.build();

		Request request = new Request.Builder()
				.header("Authorization", Credentials.basic(commandLine.username, commandLine.accessKey)).url(url)
				.post(OkHttpUtils.EMPTY_BODY).build();
		LogUtils.debug("Closing upload session");
		TeamscaleRequestExecutor.sendRequest(client, commandLine, url, request);
	}

	private static void sendRequestForFormat(OkHttpClient client, ReportCommandLineOptions commandLine, String format,
			Set<File> fileList, String sessionId) throws IOException {
		MultipartBody.Builder multipartBodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);

		for (File file : fileList) {
			multipartBodyBuilder.addFormDataPart("report", file.getName(),
					RequestBody.create(file, MediaType.get("application/octet-stream")));
		}

		RequestBody requestBody = multipartBodyBuilder.build();

		HttpUrl.Builder builder = commandLine.url.newBuilder().addPathSegments("api")
				.addPathSegments(MINIMUM_REQUIRED_API_VERSION).addPathSegments("projects")
				.addPathSegment(commandLine.project).addPathSegments("external-analysis/session")
				.addPathSegment(sessionId).addPathSegment("report").addQueryParameter("format", format);

		if (commandLine.pathPrefix != null) {
			builder.addQueryParameter("path-prefix", commandLine.pathPrefix);
		}

		HttpUrl url = builder.build();

		Request request = new Request.Builder()
				.header("Authorization", Credentials.basic(commandLine.username, commandLine.accessKey)).url(url)
				.post(requestBody).build();

		LogUtils.info("Uploading reports for format " + format);
		TeamscaleRequestExecutor.sendRequest(client, commandLine, url, request);
	}
}
