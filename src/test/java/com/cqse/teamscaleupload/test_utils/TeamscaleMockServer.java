package com.cqse.teamscaleupload.test_utils;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import spark.Request;
import spark.Response;
import spark.Service;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

/**
 * Mocks a Teamscale server: stores all report upload sessions.
 */
public class TeamscaleMockServer implements AutoCloseable {

    private static final File KEYSTORE;

    /**
     * Trust store pre-filled with the self-signed certificate used by the {@link TeamscaleMockServer}.
     */
    public static final File TRUSTSTORE;

    static {
        try {
            KEYSTORE = new File(TeamscaleMockServer.class.getResource("keystore.jks").toURI());
            TRUSTSTORE = new File(TeamscaleMockServer.class.getResource("truststore.jks").toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError("Failed to get keystore from resources", e);
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

        public Session(String message) {
            this.message = message;
        }
    }

    /**
     * All {@link Session}s opened on this Teamscale instance.
     */
    public final List<Session> sessions = new ArrayList<>();
    private final Service spark;

    public TeamscaleMockServer(int port) {
        this(port, false);
    }

    public TeamscaleMockServer(int port, boolean useSelfSignedCertificate) {
        this.spark = Service.ignite();

        if (useSelfSignedCertificate) {
            spark.secure(KEYSTORE.getAbsolutePath(), "password", null, null);
        }
        spark.port(port);
        spark.post("/api/projects/:projectName/external-analysis/session", this::openSession);
        spark.post("/api/projects/:projectName/external-analysis/session/:session", this::noOpHandler);
        spark.post("/api/projects/:projectName/external-analysis/session/:session/report", this::noOpHandler);
        spark.exception(Exception.class, (Exception exception, Request request, Response response) -> {
            response.status(SC_INTERNAL_SERVER_ERROR);
            response.body("Exception: " + exception.getMessage());
        });
        spark.awaitInitialization();
    }

    private String openSession(Request request, Response response) {
        String message = request.queryParams("message");
        sessions.add(new Session(message));
        return "fake-session-id";
    }

    private String noOpHandler(Request request, Response response) {
        return "";
    }

    @Override
    public void close() {
        spark.stop();
    }

}
