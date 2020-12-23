package com.cqse.teamscaleupload.resolve;

/**
 * Thrown if resolving file patterns fails.
 */
public class FilePatternResolutionException extends Exception {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constructor.
     */
    public FilePatternResolutionException(String message, Throwable cause) {
        super(message, cause);
    }

}
