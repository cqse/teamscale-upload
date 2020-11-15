package com.cqse.teamscaleupload.test_utils;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import spark.Request;
import spark.Response;
import spark.Spark;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static spark.Spark.awaitInitialization;
import static spark.Spark.exception;
import static spark.Spark.port;
import static spark.Spark.post;

/**
 * Mocks a Teamscale server: stores all report upload sessions.
 */
public class TeamscaleMockServer {

    public static final File KEYSTORE;
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

    public TeamscaleMockServer(int port) {
        this(port, false);
    }

    public TeamscaleMockServer(int port, boolean useSelfSignedCertificate) {
        if (useSelfSignedCertificate) {
            Spark.secure(KEYSTORE.getAbsolutePath(), "password", null, null);
        }
        port(port);
        post("/api/projects/:projectName/external-analysis/session", this::openSession);
        post("/api/projects/:projectName/external-analysis/session/:session", this::noOpHandler);
        post("/api/projects/:projectName/external-analysis/session/:session/report", this::noOpHandler);
        exception(Exception.class, (Exception exception, Request request, Response response) -> {
            response.status(SC_INTERNAL_SERVER_ERROR);
            response.body("Exception: " + exception.getMessage());
        });
        awaitInitialization();
    }

    private String openSession(Request request, Response response) {
        String message = request.queryParams("message");
        sessions.add(new Session(message));
        return "fake-session-id";
    }

    private String noOpHandler(Request request, Response response) {
        return "";
    }

}
