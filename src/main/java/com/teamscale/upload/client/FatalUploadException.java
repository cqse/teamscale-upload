package com.teamscale.upload.client;

/**
 * Signals an error that ends the upload without further retries.
 * <p>
 * The HTTP code throws this exception instead of terminating the program
 * directly, so that {@link ReportUploadClient} can delete an open upload
 * session before {@link TeamscaleRequestExecutor#performUpload} reports the
 * error and exits.
 */
public class FatalUploadException extends Exception {

	public FatalUploadException(String message) {
		super(message);
	}

	public FatalUploadException(String message, Throwable cause) {
		super(message, cause);
	}
}
