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

	/**
	 * The HTTP status code of the Teamscale response that caused this exception,
	 * or {@code null} if the cause is not an HTTP response, e.g. an unknown host.
	 */
	private final Integer httpStatusCode;

	public FatalUploadException(String message) {
		super(message);
		this.httpStatusCode = null;
	}

	public FatalUploadException(String message, Throwable cause) {
		super(message, cause);
		this.httpStatusCode = null;
	}

	public FatalUploadException(String message, int httpStatusCode) {
		super(message);
		this.httpStatusCode = httpStatusCode;
	}

	/** @see #httpStatusCode */
	public Integer getHttpStatusCode() {
		return httpStatusCode;
	}
}
