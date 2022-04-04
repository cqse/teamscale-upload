package com.teamscale.upload.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Function;

/**
 * Utilities for interacting with secrets, such as the Teamscale access key.
 */
public class SecretUtils {

	/**
	 * Name of the environment variable which is used to store the Teamscale access
	 * key. This is not only relevant for users of the tool, but also for our tests.
	 */
	public static final String TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE = "TEAMSCALE_ACCESS_KEY";

	/**
	 * Determines the access key to be used for further authentication by using one
	 * of these in the following order. First match is used.
	 * <ul>
	 * <li>Provided via STDIN when option "--access-key -" is used</li>
	 * <li>Provided via the option --access-key <access-key></li>
	 * <li>Provided via environment variable
	 * {@link #TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE}</li>
	 * </ul>
	 *
	 * @return the access key or null if no access key was found
	 */
	public static String determineAccessKeyToUse(String accessKeyViaOption) {
		try {
			return determineAccessKeyToUse(accessKeyViaOption, System::getenv, System.in);
		} catch (IOException e) {
			LogUtils.failWithoutStackTrace("Reading the access key failed", e);
			return null;
		}
	}

	/**
	 * Testable version of {@link #determineAccessKeyToUse(String)} that allows
	 * mocking environment variables and stdin.
	 *
	 * @throws IOException in case reading the access key from stdin fails.
	 */
	/* package */ static String determineAccessKeyToUse(String accessKeyViaOption,
			Function<String, String> readEnvironmentVariable, InputStream systemIn) throws IOException {

		if (accessKeyViaOption == null) {
			// may be null, but is validated later
			return readEnvironmentVariable.apply(TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE);
		}

		if (accessKeyViaOption.equals("-")) {
			try (Scanner scanner = new Scanner(systemIn)) {
				return scanner.nextLine();
			} catch (NoSuchElementException | IllegalStateException e) {
				throw new IOException("You chose to read the access key from stdin but stdin was empty. Please provide the" +
						" access key via stdin or change the value of the --accesskey option.");
			}
		}

		return accessKeyViaOption;
	}
}
