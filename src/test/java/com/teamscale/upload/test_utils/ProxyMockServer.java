package com.teamscale.upload.test_utils;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

/**
 * Mockserver class to simulate a proxy before Teamscale.
 */
public class ProxyMockServer implements AutoCloseable {
	/** The port where the proxy server is reachable. */
	public static final int PORT = 3000;

	private final MockWebServer server = new MockWebServer();

	private final boolean requireAuth;

	public ProxyMockServer(boolean requireAuth) {
		this.requireAuth = requireAuth;
		server.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				return connect(request);
			}
		});
		try {
			// The wildcard address also accepts IPv6. OkHttp tries ::1 after 127.0.0.1 failed, and would otherwise
			// report the refused ::1 connection instead of the actual error.
			server.start(new InetSocketAddress(PORT).getAddress(), PORT);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not start ProxyMockServer on port " + PORT, e);
		}
	}

	private MockResponse connect(RecordedRequest request) {
		if (!"POST".equals(request.getMethod())) {
			return new MockResponse.Builder().code(404).build();
		}
		if (request.getHeaders().get("Proxy-Authorization") == null && requireAuth) {
			return new MockResponse.Builder().code(407).body("Proxy-Authorization required.").build();
		}
		return new MockResponse.Builder().code(200).body("Proxy Connection successful.").build();
	}

	@Override
	public void close() {
		server.close();
	}
}
