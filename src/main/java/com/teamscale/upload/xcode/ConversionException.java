package com.teamscale.upload.xcode;

import com.teamscale.upload.autodetect_revision.ProcessUtils;

/**
 * Custom exception used to indicate errors during conversion.
 */
public class ConversionException extends Exception {

	public ConversionException(String message) {
		super(message);
	}

	public ConversionException(String message, Exception e) {
		super(message, e);
	}

	/**
	 * Creates a {@link ConversionException} with the given message and the
	 * {@linkplain ProcessUtils.ProcessResult#errorOutput error output of the
	 * command}.
	 */
	public static ConversionException withProcessResult(String message, ProcessUtils.ProcessResult processResult) {
		String messageIncludingErrorOutput = message;
		if (processResult.errorOutput != null) {
			messageIncludingErrorOutput += " (command output: " + processResult.errorOutput + ")";
		}
		return new ConversionException(messageIncludingErrorOutput, processResult.exception);
	}
}
