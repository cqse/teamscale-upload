package com.teamscale.upload.client;

import java.io.IOException;

import javax.net.ssl.SSLHandshakeException;

import com.teamscale.upload.utils.LogUtils;

/**
 * Utility methods concerned with retrying uploads that fail due to transient network errors.
 */
public class RetryUtils {

	/**
	 * A single upload attempt that may fail with an {@link IOException}.
	 */
	@FunctionalInterface
	public interface UploadAttempt {

		/** Performs the upload. */
		void perform() throws IOException;
	}

	/**
	 * Performs the given upload, retrying it up to {@code maxAttempts} times if it
	 * fails with an {@link IOException}.
	 * <p>
	 * An {@link SSLHandshakeException} is not retried but rethrown, as retrying will
	 * not make a broken certificate setup work. If all attempts fail, the program is
	 * terminated with an error message.
	 */
	public static void performWithRetry(int maxAttempts, UploadAttempt attempt) throws IOException {
		for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
			try {
				attempt.perform();
				return;
			} catch (SSLHandshakeException e) {
				throw e;
			} catch (IOException e) {
				if (attemptNumber < maxAttempts) {
					LogUtils.warn("Failed attempt " + attemptNumber + " / " + maxAttempts + ": " + e.getMessage());
				} else {
					LogUtils.failWithoutStackTrace(
							"Upload failed after " + maxAttempts + " attempt(s): " + e.getMessage(), e);
				}
			}
		}
	}
}
