package com.teamscale.upload.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SecretUtilsTest {

	private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);

	private static String emptyEnvironment(String variableName) {
		return null;
	}

	private static InputStream fromString(String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void readAccessKeyFromOption() throws Exception {
		assertThat(SecretUtils.determineAccessKeyToUse("key", SecretUtilsTest::emptyEnvironment, EMPTY_STREAM))
				.isEqualTo("key");
	}

	@Test
	void readAccessKeyFromEnvironment() throws Exception {
		assertThat(SecretUtils.determineAccessKeyToUse(null, (variableName) -> "key", EMPTY_STREAM)).isEqualTo("key");
	}

	@Test
	void readAccessKeyFromStream() throws Exception {
		assertThat(SecretUtils.determineAccessKeyToUse("-", SecretUtilsTest::emptyEnvironment, fromString("key")))
				.isEqualTo("key");
		assertThat(SecretUtils.determineAccessKeyToUse("-", SecretUtilsTest::emptyEnvironment, fromString("key\n")))
				.isEqualTo("key");
	}

	/** Ensures that the three locations are searched in the correct order. */
	@Test
	void testOrder() throws Exception {
		assertThat(SecretUtils.determineAccessKeyToUse("-", (variableName) -> "environment", fromString("stdin")))
				.isEqualTo("stdin");
		assertThat(SecretUtils.determineAccessKeyToUse("option", (variableName) -> "environment", fromString("stdin")))
				.isEqualTo("option");
		assertThat(SecretUtils.determineAccessKeyToUse(null, (variableName) -> "environment", fromString("stdin")))
				.isEqualTo("environment");
	}

	@Test
	void emptyStdinShouldNotCauseExceptions() {
		assertThatThrownBy(
				() -> SecretUtils.determineAccessKeyToUse("-", SecretUtilsTest::emptyEnvironment, EMPTY_STREAM))
				.hasMessageContaining("stdin");
		assertThatThrownBy(
				() -> SecretUtils.determineAccessKeyToUse("-", SecretUtilsTest::emptyEnvironment, fromString("")))
				.hasMessageContaining("stdin");
	}

}