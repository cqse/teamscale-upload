package com.teamscale.upload.test_utils;

import org.assertj.core.api.Assertions;
import spark.Request;
import spark.Response;
import spark.Service;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

/**
 * Mocks a Teamscale server: stores all report upload sessions and all uploaded
 * vulnerability reports.
 */
public class TeamscaleMockServer implements AutoCloseable {

	/**
	 * Trust store pre-filled with the self-signed certificate used by the
	 * {@link TeamscaleMockServer}.
	 */
	public static final File TRUSTSTORE;
	private static final File KEYSTORE;

	static {
		try {
			URL keystoreResource = TeamscaleMockServer.class.getResource("keystore.jks");
			if (keystoreResource != null) {
				KEYSTORE = new File(keystoreResource.toURI());
			} else {
				// will fail in constructor, a failure there is not handled properly by the test
				// framework
				KEYSTORE = null;
			}
			URL truststoreResource = TeamscaleMockServer.class.getResource("truststore.jks");
			if (truststoreResource != null) {
				TRUSTSTORE = new File(truststoreResource.toURI());
			} else {
				// will fail in constructor, a failure there is not handled properly by the test
				// framework
				TRUSTSTORE = null;
			}
		} catch (URISyntaxException e) {
			throw new AssertionError("Failed to get keystore from resources", e);
		}
	}

	/**
	 * All {@link Session}s opened on this Teamscale instance.
	 */
	public final List<Session> sessions = new ArrayList<>();
	/**
	 * The raw report by the filename of the uploaded report.
	 */
	public final Map<String, byte[]> uploadedReportsByName = new HashMap<>();

	/**
	 * All vulnerability reports uploaded to this Teamscale instance.
	 */
	public final List<VulnerabilityReportUpload> vulnerabilityReportUploads = new ArrayList<>();

	/**
	 * The IDs of all sessions deleted on this Teamscale instance.
	 */
	public final List<String> deletedSessionIds = new ArrayList<>();

	private final Service spark;

	/**
	 * Time in seconds to wait in the {@link #openSession(Request, Response)}
	 * handler to simulate slow Teamscale request processing.
	 */
	private final long openSessionRequestTimeInSeconds;

	/**
	 * Number of initial requests that should fail with HTTP 500 to simulate
	 * intermittent server errors.
	 * <p>
	 * Counted per endpoint rather than over all requests: the session endpoint and
	 * the vulnerability report endpoint each answer this many requests with an
	 * error before they start processing them.
	 */
	private final int countOfInitialFailedRequestsPerEndpoint;

	/**
	 * HTTP status code that all report upload requests fail with, or {@code null}
	 * if report uploads succeed.
	 */
	private final Integer reportUploadStatusCode;

	/**
	 * Whether a failed report upload deletes its session, as Teamscale does when it
	 * rejects or fails to store a report. A later request to delete that session is
	 * then answered with 404.
	 */
	private final boolean deletesSessionOnFailedReportUpload;

	/**
	 * HTTP status code that all upload session commit requests fail with, or
	 * {@code null} if upload session commits succeed.
	 */
	private final Integer uploadSessionCommitStatusCode;

	/**
	 * The IDs of all sessions this server deleted on its own after a failed report
	 * upload.
	 */
	private final Set<String> sessionIdsDeletedByServer = new HashSet<>();

	private final AtomicInteger sessionRequestCounter = new AtomicInteger(0);

	private final AtomicInteger vulnerabilityReportRequestCounter = new AtomicInteger(0);

	public TeamscaleMockServer(int port) {
		this(port, false);
	}

	public TeamscaleMockServer(int port, boolean useSelfSignedCertificate) {
		this(port, useSelfSignedCertificate, 0L, 0);
	}

	public TeamscaleMockServer(int port, boolean useSelfSignedCertificate, long openSessionRequestTimeInSeconds) {
		this(port, useSelfSignedCertificate, openSessionRequestTimeInSeconds, 0);
	}

	public TeamscaleMockServer(int port, boolean useSelfSignedCertificate, long openSessionRequestTimeInSeconds,
			int countOfInitialFailedRequestsPerEndpoint) {
		this(port, useSelfSignedCertificate, openSessionRequestTimeInSeconds, countOfInitialFailedRequestsPerEndpoint,
				null, null, null, false, null);
	}

	/**
	 * Creates a server that answers every request with the given status code and
	 * body instead of processing it. Use this to simulate the error responses
	 * Teamscale sends, e.g. a 400 for an upload it rejects or a 404 for a project
	 * that does not exist.
	 */
	public static TeamscaleMockServer respondingWith(int port, int statusCode, String body) {
		return new TeamscaleMockServer(port, false, 0L, 0, statusCode, body, null, false, null);
	}

	/**
	 * Creates a server that opens sessions normally but answers every report
	 * upload request with the given status code. Use this to simulate an upload
	 * attempt that fails while its session is open.
	 *
	 * @param deletesSessionOnFailure
	 *            whether the server deletes the session when the report upload
	 *            fails, as Teamscale does when it rejects or fails to store a
	 *            report. Pass {@code false} to simulate a failure outside
	 *            Teamscale, e.g. in a reverse proxy.
	 */
	public static TeamscaleMockServer failingReportUploadsWith(int port, int statusCode,
			boolean deletesSessionOnFailure) {
		return new TeamscaleMockServer(port, false, 0L, 0, null, null, statusCode, deletesSessionOnFailure, null);
	}

	/**
	 * Creates a server that opens sessions and receives reports normally but
	 * answers every upload session commit request with the given status code.
	 */
	public static TeamscaleMockServer failingUploadSessionCommitsWith(int port, int statusCode) {
		return new TeamscaleMockServer(port, false, 0L, 0, null, null, null, false, statusCode);
	}

	private TeamscaleMockServer(int port, boolean useSelfSignedCertificate, long openSessionRequestTimeInSeconds,
			int countOfInitialFailedRequestsPerEndpoint, Integer forcedStatusCode, String forcedResponseBody,
			Integer reportUploadStatusCode, boolean deletesSessionOnFailedReportUpload, Integer uploadSessionCommitStatusCode) {
		if (KEYSTORE == null || TRUSTSTORE == null) {
			Assertions.fail(
					"Could not initialize TeamscaleMockServer: Could not find keystore.jks or truststore.jks test resources");
		}
		this.spark = Service.ignite();
		this.openSessionRequestTimeInSeconds = openSessionRequestTimeInSeconds;
		this.countOfInitialFailedRequestsPerEndpoint = countOfInitialFailedRequestsPerEndpoint;
		this.reportUploadStatusCode = reportUploadStatusCode;
		this.deletesSessionOnFailedReportUpload = deletesSessionOnFailedReportUpload;
		this.uploadSessionCommitStatusCode = uploadSessionCommitStatusCode;

		if (useSelfSignedCertificate) {
			spark.secure(KEYSTORE.getAbsolutePath(), "password", null, null);
		}
		spark.port(port);
		spark.before((request, response) -> {
			if (forcedStatusCode != null) {
				spark.halt(forcedStatusCode, forcedResponseBody);
			}
		});
		spark.post("/api/v8.2/projects/:projectName/external-analysis/session", this::openSession);
		spark.post("/api/v8.2/projects/:projectName/external-analysis/session/:session", this::commitUploadSessionHandler);
		spark.delete("/api/v8.2/projects/:projectName/external-analysis/session/:session",
				this::deleteSessionHandler);
		spark.post("/api/v8.2/projects/:projectName/external-analysis/session/:session/report",
				this::receiveReportHandler);
		spark.post("/api/v2026.7.0/projects/:projectName/vulnerability-report",
				this::receiveVulnerabilityReportHandler);
		spark.exception(Exception.class, (Exception exception, Request request, Response response) -> {
			response.status(SC_INTERNAL_SERVER_ERROR);
			response.body("Exception: " + exception.getMessage());
		});
		spark.awaitInitialization();
	}

	private void simulateRequestTime() {
		if (openSessionRequestTimeInSeconds > 0) {
			try {
				Thread.sleep(openSessionRequestTimeInSeconds * 1000);
			} catch (InterruptedException e) {
				throw new RuntimeException("Unable to simulate request time: " + e.getMessage(), e);
			}
		}
	}

	private String openSession(Request request, Response response) {
		simulateRequestTime();
		int requestNumber = sessionRequestCounter.incrementAndGet();
		if (requestNumber <= countOfInitialFailedRequestsPerEndpoint) {
			response.status(SC_INTERNAL_SERVER_ERROR);
			return "Simulated intermittent server error";
		}
		String message = request.queryParams("message");
		String revisionOrTimestamp = request.queryParams("revision");
		if (revisionOrTimestamp == null) {
			revisionOrTimestamp = request.queryParams("t");
		}
		sessions.add(new Session(message, revisionOrTimestamp));
		return "fake-session-id";
	}

	private String receiveReportHandler(Request request, Response response) throws ServletException, IOException {
		if (reportUploadStatusCode != null) {
			if (deletesSessionOnFailedReportUpload) {
				sessionIdsDeletedByServer.add(request.params("session"));
			}
			response.status(reportUploadStatusCode);
			return "Simulated report upload error";
		}

		request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement(""));

		// one request carries one part per uploaded report, so reading a single part
		// would silently drop all reports but the first
		for (Part report : request.raw().getParts()) {
			if (!"report".equals(report.getName())) {
				continue;
			}
			try (InputStream is = report.getInputStream()) {
				uploadedReportsByName.put(report.getSubmittedFileName(), is.readAllBytes());
			}
		}

		return "Report uploaded";
	}

	private String receiveVulnerabilityReportHandler(Request request, Response response)
			throws ServletException, IOException {
		int requestNumber = vulnerabilityReportRequestCounter.incrementAndGet();
		if (requestNumber <= countOfInitialFailedRequestsPerEndpoint) {
			response.status(SC_INTERNAL_SERVER_ERROR);
			return "Simulated intermittent server error";
		}

		request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement(""));
		Part report = request.raw().getPart("file");

		byte[] content;
		try (InputStream is = report.getInputStream()) {
			content = is.readAllBytes();
		}
		vulnerabilityReportUploads
				.add(new VulnerabilityReportUpload(request.queryParams("build-name"), request.queryParams("version"),
						request.queryParams("revision"), report.getSubmittedFileName(), content));

		// the real endpoint returns 204 with an empty body
		response.status(SC_NO_CONTENT);
		return "";
	}

	private String commitUploadSessionHandler(Request request, Response response) {
		if (uploadSessionCommitStatusCode != null) {
			response.status(uploadSessionCommitStatusCode);
			return "Simulated upload session commit error";
		}
		return "";
	}

	private String deleteSessionHandler(Request request, Response response) {
		String sessionId = request.params("session");
		if (sessionIdsDeletedByServer.contains(sessionId)) {
			response.status(SC_NOT_FOUND);
			return "Session with ID '" + sessionId + "' does not exist.";
		}
		deletedSessionIds.add(sessionId);
		return "";
	}

	@Override
	public void close() {
		spark.stop();
	}

	/**
	 * A vulnerability report uploaded to this Teamscale instance.
	 *
	 * @param buildName
	 *            the value of the "build-name" query parameter.
	 * @param version
	 *            the value of the "version" query parameter.
	 * @param revision
	 *            the value of the "revision" query parameter.
	 * @param fileName
	 *            the file name submitted for the "file" part.
	 * @param content
	 *            the raw content of the uploaded vulnerability report. Compare it
	 *            as an array: the generated equals() compares it by identity.
	 */
	public record VulnerabilityReportUpload(String buildName, String version, String revision, String fileName,
			byte[] content) {
	}

	/**
	 * An opened upload session.
	 *
	 * @param message
	 *            the message used for that session.
	 * @param revisionOrTimestamp
	 *            the revision or timestamp used during the upload.
	 */
	public record Session(String message, String revisionOrTimestamp) {
	}
}
