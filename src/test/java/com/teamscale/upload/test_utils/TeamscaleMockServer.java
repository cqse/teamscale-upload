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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
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

	private final AtomicInteger sessionRequestCounter = new AtomicInteger(0);

	private final AtomicInteger vulnerabilityReportRequestCounter = new AtomicInteger(0);

	/**
	 * The status code with which every request is answered, or null to answer them
	 * normally. See {@link #respondWith(int, String)}.
	 */
	private Integer forcedStatusCode = null;

	/**
	 * The response body with which every request is answered, or null to answer them
	 * normally. See {@link #respondWith(int, String)}.
	 */
	private String forcedResponseBody = "";

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
		if (KEYSTORE == null || TRUSTSTORE == null) {
			Assertions.fail(
					"Could not initialize TeamscaleMockServer: Could not find keystore.jks or truststore.jks test resources");
		}
		this.spark = Service.ignite();
		this.openSessionRequestTimeInSeconds = openSessionRequestTimeInSeconds;
		this.countOfInitialFailedRequestsPerEndpoint = countOfInitialFailedRequestsPerEndpoint;

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
		spark.post("/api/v8.2/projects/:projectName/external-analysis/session/:session", this::noOpHandler);
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

	/**
	 * Makes this server answer every request with the given status code and body,
	 * instead of processing it. Use this to simulate the error responses Teamscale
	 * sends, e.g. a 400 for an upload it rejects or a 404 for a project that does
	 * not exist.
	 */
	public TeamscaleMockServer respondWith(int statusCode, String body) {
		this.forcedStatusCode = statusCode;
		this.forcedResponseBody = body;
		return this;
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
		vulnerabilityReportUploads.add(new VulnerabilityReportUpload(request.queryParams("build-name"), request.queryParams("version"),
				request.queryParams("revision"), report.getSubmittedFileName(), content));

		// the real endpoint returns 204 with an empty body
		response.status(SC_NO_CONTENT);
		return "";
	}

	private String noOpHandler(Request request, Response response) {
		return "";
	}

	@Override
	public void close() {
		spark.stop();
	}

	/**
	 * A vulnerability report uploaded to this Teamscale instance.
	 */
	public static class VulnerabilityReportUpload {

		/** The value of the "build-name" query parameter. */
		public final String buildName;

		/** The value of the "version" query parameter. */
		public final String version;

		/** The value of the "revision" query parameter. */
		public final String revision;

		/** The file name submitted for the "file" part. */
		public final String fileName;

		/** The raw content of the uploaded vulnerability report. */
		public final byte[] content;

		public VulnerabilityReportUpload(String buildName, String version, String revision, String fileName, byte[] content) {
			this.buildName = buildName;
			this.version = version;
			this.revision = revision;
			this.fileName = fileName;
			this.content = content;
		}
	}

	/**
	 * An opened upload session.
	 */
	public static class Session {

		/**
		 * The message used for that session.
		 */
		public final String message;

		/**
		 * The revision or timestamp used during the upload.
		 */
		public final String revisionOrTimestamp;

		public Session(String message, String revisionOrTimestamp) {
			this.message = message;
			this.revisionOrTimestamp = revisionOrTimestamp;
		}
	}
}
