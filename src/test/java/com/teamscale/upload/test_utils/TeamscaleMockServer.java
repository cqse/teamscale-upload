package com.teamscale.upload.test_utils;

import org.assertj.core.api.Assert;
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

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

/**
 * Mocks a Teamscale server: stores all report upload sessions.
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
				// will fail in constructor, a failure there is not handled properly by the test framework
				KEYSTORE = null;
			}
			URL truststoreResource = TeamscaleMockServer.class.getResource("truststore.jks");
			if (truststoreResource != null) {
				TRUSTSTORE = new File(truststoreResource.toURI());
			} else {
				// will fail in constructor, a failure there is not handled properly by the test framework
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

	private final Service spark;

	/**
	 * Time in seconds to wait in the {@link #openSession(Request, Response)}
	 * handler to simulate slow Teamscale request processing.
	 */
	private final long openSessionRequestTimeInSeconds;

	public TeamscaleMockServer(int port) {
		this(port, false);
	}

	public TeamscaleMockServer(int port, boolean useSelfSignedCertificate) {
		this(port, useSelfSignedCertificate, 0L);
	}

	public TeamscaleMockServer(int port, boolean useSelfSignedCertificate, long openSessionRequestTimeInSeconds) {
		if (KEYSTORE == null || TRUSTSTORE == null) {
			Assertions.fail("Could not initialize TeamscaleMockServer: Could not find keystore.jks or truststore.jks test resources");
		}
		this.spark = Service.ignite();
		this.openSessionRequestTimeInSeconds = openSessionRequestTimeInSeconds;

		if (useSelfSignedCertificate) {
			spark.secure(KEYSTORE.getAbsolutePath(), "password", null, null);
		}
		spark.port(port);
		spark.post("/api/v8.2/projects/:projectName/external-analysis/session", this::openSession);
		spark.post("/api/v8.2/projects/:projectName/external-analysis/session/:session", this::noOpHandler);
		spark.post("/api/v8.2/projects/:projectName/external-analysis/session/:session/report", this::receiveReportHandler);
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

		Part report = request.raw().getPart("report");

		try (InputStream is = report.getInputStream()) {
			uploadedReportsByName.put(report.getSubmittedFileName(), is.readAllBytes());
		}

		return "Report uploaded";
	}

	private String noOpHandler(Request request, Response response) {
		return "";
	}

	@Override
	public void close() {
		spark.stop();
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
