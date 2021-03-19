package com.teamscale.upload;

import okhttp3.Response;

/**
 * Utilities for interacting with stdout, stderr and terminating the program due to fatal errors.
 */
public class LogUtils {
    /**
     * Print error message and server response, then exit program
     */
    public static void fail(String message, Response response) {
        fail("Upload to Teamscale failed:\n\n" + message + "\n\nTeamscale's response:\n" +
                response.toString() + "\n" + OkHttpUtils.readBodySafe(response));
    }

    /**
     * Print the stack trace of the throwable as debug info, then print the error message and exit
     * the program.
     */
    public static void failWithStackTrace(String message, Throwable throwable) {
        throwable.printStackTrace();
        fail(message);
    }

    /**
     * Print error message and exit the program
     */
    public static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    /**
     * Print a warning message to stderr.
     */
    public static void warn(String message) {
        System.err.println("WARNING: " + message);
    }
}
