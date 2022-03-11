package com.teamscale.upload.utils;

import java.util.Scanner;

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
	 * of these in the following order:
	 * <ul>
	 * <li>Provided via environment variable
	 * {@link #TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE}</li>
	 * <li>Provided via the option --access-key <access-key></li>
	 * <li>Provided via STDIN when option "--access-key -" is used</li>
	 * </ul>
	 */
	public static String determineAccessKeyToUse(String accessKeyViaOption) {
		if (accessKeyViaOption == null) {
			// may be null, but is validated later
			return System.getenv(TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE);
		}

		if (accessKeyViaOption.equals("-")) {
			LogUtils.debug("Reading access key from standard input");
			Scanner inputScanner = new Scanner(System.in);
			String accessKeyViaStdin = inputScanner.nextLine();
			inputScanner.close();
			LogUtils.debug("Successfully read access key");
			return accessKeyViaStdin;
		}

		return accessKeyViaOption;
	}
}
