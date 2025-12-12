package com.teamscale.upload.test_utils;

import spark.Request;
import spark.Response;
import spark.Service;

/**
 * Mockserver class to simulate a proxy before Teamscale.
 */
public class ProxyMockServer implements AutoCloseable {
	/** The port where the proxy server is reachable. */
	public static final int PORT = 3000;
	private final Service spark;
	private final boolean requireAuth;

	public ProxyMockServer(boolean requireAuth) {
		this.spark = Service.ignite();
		spark.port(PORT);
		this.requireAuth = requireAuth;
		spark.post("/*", this::connect);
		spark.init();
	}

	private String connect(Request req, Response res) {
		if (req.headers("Proxy-Authorization") == null && requireAuth) {
			res.status(407);
			return "Proxy-Authorization required.";
		}
		return "Proxy Connection successful.";
	}

	@Override
	public void close() {
		spark.stop();
	}
}
