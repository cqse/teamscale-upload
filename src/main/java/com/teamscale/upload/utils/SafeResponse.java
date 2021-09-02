package com.teamscale.upload.utils;

import java.io.IOException;

import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Wraps a {@link okhttp3.Response} but makes it always safe to access the response body.
 */
public class SafeResponse {

    public final Response unsafeResponse;
    public final String body;

    public SafeResponse(Response unsafeResponse) {
        this.unsafeResponse = unsafeResponse;
        this.body = readBodySafe(unsafeResponse);
    }

    /**
     * Either returns the body of the response or if that cannot be read, a safe fallback string.
     */
    private static String readBodySafe(Response response) {
        try {
            ResponseBody body = response.body();
            if (body == null) {
                return "<no response body>";
            }
            return body.string();
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to read response body: " + e.getMessage();
        }
    }

}
