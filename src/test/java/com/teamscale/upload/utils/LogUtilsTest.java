package com.teamscale.upload.utils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogUtilsTest {

	private final PrintStream originalOut = System.out;

	@AfterEach
	void restore() {
		System.setOut(originalOut);
		LogUtils.disableDebugLogging();
	}

	/**
	 * A failed process without a Java exception, for example a non-zero exit code
	 * of xcodebuild, passes a null throwable.
	 */
	@Test
	void debugWithoutThrowablePrintsMessage() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
		LogUtils.enableDebugLogging();

		LogUtils.debug("command failed", (Throwable) null);

		assertThat(output.toString(StandardCharsets.UTF_8)).contains("DEBUG: command failed");
	}
}
