package com.teamscale.upload.test_utils;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.MediaType;
import okhttp3.MultipartReader;
import okio.Buffer;
import org.assertj.core.api.Assertions;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private static final int SC_NO_CONTENT = 204;
	private static final int SC_NOT_FOUND = 404;
	private static final int SC_INTERNAL_SERVER_ERROR = 500;

	private static final char[] KEYSTORE_PASSWORD = "password".toCharArray();

	private static final Pattern OPEN_SESSION_PATH = Pattern
			.compile("/api/v8\\.2/projects/[^/]+/external-analysis/session");
	private static final Pattern SESSION_PATH = Pattern
			.compile("/api/v8\\.2/projects/[^/]+/external-analysis/session/[^/]+");
	private static final Pattern REPORT_PATH = Pattern
			.compile("/api/v8\\.2/projects/[^/]+/external-analysis/session/[^/]+/report");
	private static final Pattern VULNERABILITY_REPORT_PATH = Pattern
			.compile("/api/v2026\\.7\\.0/projects/[^/]+/vulnerability-report");

	/** Matches a parameter of a Content-Disposition header, for example {@code name="report"}. */
	private static final Pattern CONTENT_DISPOSITION_PARAMETER = Pattern.compile(";\\s*([a-z]+)=\"([^\"]*)\"");

	private final MockWebServer server = new MockWebServer();

	/**
	 * Time in seconds to wait in the {@link #openSession(RecordedRequest)}
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
				null, null);
	}

	/**
	 * Creates a server that answers every request with the given status code and
	 * body instead of processing it. Use this to simulate the error responses
	 * Teamscale sends, e.g. a 400 for an upload it rejects or a 404 for a project
	 * that does not exist.
	 */
	public static TeamscaleMockServer respondingWith(int port, int statusCode, String body) {
		return new TeamscaleMockServer(port, false, 0L, 0, statusCode, body);
	}

	private TeamscaleMockServer(int port, boolean useSelfSignedCertificate, long openSessionRequestTimeInSeconds,
			int countOfInitialFailedRequestsPerEndpoint, Integer forcedStatusCode, String forcedResponseBody) {
		if (KEYSTORE == null || TRUSTSTORE == null) {
			Assertions.fail(
					"Could not initialize TeamscaleMockServer: Could not find keystore.jks or truststore.jks test resources");
		}
		this.openSessionRequestTimeInSeconds = openSessionRequestTimeInSeconds;
		this.countOfInitialFailedRequestsPerEndpoint = countOfInitialFailedRequestsPerEndpoint;

		if (useSelfSignedCertificate) {
			server.useHttps(createSelfSignedSslContext().getSocketFactory());
		}
		server.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				if (forcedStatusCode != null) {
					return response(forcedStatusCode, forcedResponseBody);
				}
				try {
					return route(request);
				} catch (Exception e) {
					return response(SC_INTERNAL_SERVER_ERROR, "Exception: " + e.getMessage());
				}
			}
		});
		try {
			// The wildcard address also accepts IPv6. OkHttp tries ::1 after 127.0.0.1 failed, and would otherwise
			// report the refused ::1 connection instead of the actual error.
			server.start(new InetSocketAddress(port).getAddress(), port);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not start TeamscaleMockServer on port " + port, e);
		}
	}

	private static SSLContext createSelfSignedSslContext() {
		try (InputStream keystoreStream = new FileInputStream(KEYSTORE)) {
			KeyStore keyStore = KeyStore.getInstance("JKS");
			keyStore.load(keystoreStream, KEYSTORE_PASSWORD);
			KeyManagerFactory keyManagerFactory = KeyManagerFactory
					.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD);
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
			return sslContext;
		} catch (IOException | GeneralSecurityException e) {
			throw new AssertionError("Failed to load the self-signed certificate from " + KEYSTORE, e);
		}
	}

	private MockResponse route(RecordedRequest request) throws IOException, InterruptedException {
		String path = request.getUrl().encodedPath();
		if (!"POST".equals(request.getMethod())) {
			return response(SC_NOT_FOUND, "");
		}
		if (OPEN_SESSION_PATH.matcher(path).matches()) {
			return openSession(request);
		}
		if (SESSION_PATH.matcher(path).matches()) {
			return response(200, "");
		}
		if (REPORT_PATH.matcher(path).matches()) {
			return receiveReport(request);
		}
		if (VULNERABILITY_REPORT_PATH.matcher(path).matches()) {
			return receiveVulnerabilityReport(request);
		}
		return response(SC_NOT_FOUND, "");
	}

	private static MockResponse response(int statusCode, String body) {
		return new MockResponse.Builder().code(statusCode).body(body).build();
	}

	private MockResponse openSession(RecordedRequest request) throws InterruptedException {
		if (openSessionRequestTimeInSeconds > 0) {
			Thread.sleep(openSessionRequestTimeInSeconds * 1000);
		}
		int requestNumber = sessionRequestCounter.incrementAndGet();
		if (requestNumber <= countOfInitialFailedRequestsPerEndpoint) {
			return response(SC_INTERNAL_SERVER_ERROR, "Simulated intermittent server error");
		}
		String message = request.getUrl().queryParameter("message");
		String revisionOrTimestamp = request.getUrl().queryParameter("revision");
		if (revisionOrTimestamp == null) {
			revisionOrTimestamp = request.getUrl().queryParameter("t");
		}
		sessions.add(new Session(message, revisionOrTimestamp));
		return response(200, "fake-session-id");
	}

	private MockResponse receiveReport(RecordedRequest request) throws IOException {
		// one request carries one part per uploaded report, so reading a single part
		// would silently drop all reports but the first
		for (FormDataPart report : readFormDataParts(request)) {
			if ("report".equals(report.name())) {
				uploadedReportsByName.put(report.fileName(), report.content());
			}
		}
		return response(200, "Report uploaded");
	}

	private MockResponse receiveVulnerabilityReport(RecordedRequest request) throws IOException {
		int requestNumber = vulnerabilityReportRequestCounter.incrementAndGet();
		if (requestNumber <= countOfInitialFailedRequestsPerEndpoint) {
			return response(SC_INTERNAL_SERVER_ERROR, "Simulated intermittent server error");
		}

		FormDataPart report = readFormDataParts(request).stream().filter(part -> "file".equals(part.name()))
				.findFirst().orElseThrow(() -> new IOException("The request has no part named \"file\""));
		vulnerabilityReportUploads.add(new VulnerabilityReportUpload(request.getUrl().queryParameter("build-name"),
				request.getUrl().queryParameter("version"), request.getUrl().queryParameter("revision"),
				report.fileName(), report.content()));

		// the real endpoint returns 204 with an empty body
		return response(SC_NO_CONTENT, "");
	}

	/**
	 * Returns all parts of the multipart/form-data body of the given request.
	 */
	private static List<FormDataPart> readFormDataParts(RecordedRequest request) throws IOException {
		MediaType contentType = MediaType.parse(request.getHeaders().get("Content-Type"));
		if (contentType == null || contentType.parameter("boundary") == null || request.getBody() == null) {
			throw new IOException("The request has no multipart body");
		}
		List<FormDataPart> parts = new ArrayList<>();
		try (MultipartReader reader = new MultipartReader(new Buffer().write(request.getBody()),
				contentType.parameter("boundary"))) {
			for (MultipartReader.Part part = reader.nextPart(); part != null; part = reader.nextPart()) {
				Map<String, String> dispositionParameters = new HashMap<>();
				Matcher matcher = CONTENT_DISPOSITION_PARAMETER.matcher(part.headers().get("Content-Disposition"));
				while (matcher.find()) {
					dispositionParameters.put(matcher.group(1), matcher.group(2));
				}
				parts.add(new FormDataPart(dispositionParameters.get("name"), dispositionParameters.get("filename"),
						part.body().readByteArray()));
			}
		}
		return parts;
	}

	@Override
	public void close() {
		server.close();
	}

	/**
	 * One part of a multipart/form-data request body.
	 *
	 * @param name
	 *            the form field name.
	 * @param fileName
	 *            the submitted file name, or {@code null} if the part is no file.
	 * @param content
	 *            the raw content of the part.
	 */
	private record FormDataPart(String name, String fileName, byte[] content) {
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
