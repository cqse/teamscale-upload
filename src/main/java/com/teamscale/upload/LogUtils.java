package com.teamscale.upload;

import com.teamscale.upload.utils.SafeResponse;

/**
 * Utilities for interacting with stdout, stderr and terminating the program due to fatal errors.
 */
public class LogUtils {

    private static boolean printStackTracesForKnownErrors = false;

    /**
     * Enables printing stack traces even when the error is known and explicitly handled.
     * Useful for debugging incorrect error handling.
     */
    public static void enableStackTracePrintingForKnownErrors() {
        printStackTracesForKnownErrors = true;
    }

    /**
     * Print error message and server response, then exit program
     */
    public static void fail(String message, SafeResponse response) {
        fail("Upload to Teamscale failed:\n\n" + message + "\n\nTeamscale's response:\n" +
                response.toString() + "\n" + response.body);
    }

    /**
     * Print the stack trace of the throwable as debug info, then print the error message and exit
     * the program.
     * <p>
     * Use this function only for unexpected errors where we definitely want the user to report
     * the stack trace. For errors that the program can handle and where the stack trace is usually
     * just noise we don't care about, please use {@link #failWithoutStackTrace(String, Throwable)}
     * instead.
     */
    public static void failWithStackTrace(String message, Throwable throwable) {
        throwable.printStackTrace();
        fail(message);
    }

    /**
     * Print the error message for a known and handled error and exit the program.
     * <p>
     * If {@link #enableStackTracePrintingForKnownErrors()} was called, also prints the stack
     * trace of the given throwable.
     */
    public static void failWithoutStackTrace(String message, Throwable throwable) {
        if (printStackTracesForKnownErrors) {
            throwable.printStackTrace();
        } else {
            System.err.println("ERROR: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            System.err.println("Stack trace suppressed. Rerun this command with --stacktrace to see the stack trace." +
                    "");
        }
        fail(message);
    }

    /**
     * Print error message and exit the program
     */
    public static void fail(String message) {
        System.err.println();
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
