package com.teamscale.upload.xcode;

import java.io.File;
import java.io.IOException;

/** Utilities for converting Xcode coverage reports. */
public class ConversionUtils {
	/**
	 * File extension used for converted XCResult bundles.
	 */
	public static final String XCCOV_REPORT_FILE_EXTENSION = ".xccov";

	/**
	 * File extension used for xccov archives
	 *
	 * @see #isXccovArchive(File)
	 */
	public static final String XCCOV_ARCHIVE_FILE_EXTENSION = ".xccovarchive";

	/**
	 * File extension used for xcresult files
	 *
	 * @see #isXccovArchive(File)
	 */
	public static final String XCRESULT_FILE_EXTENSION = ".xcresult";

	/**
	 * Returns true if the file is a regular XCResult bundle directory indicated by
	 * the ".xcresult" ending in the directory name.
	 */
	public static boolean isXcresultBundle(File file) {
		return file.isDirectory() && file.getName().endsWith(XCRESULT_FILE_EXTENSION);
	}

	/**
	 * Returns true if the file is a xccov archive which is more compact than a
	 * regular XCResult bundle. A xccov archive can only be generated by XCode
	 * internal tooling but provides much better performance when extracting
	 * coverage. Note that xccov archives don't contain test results.
	 */
	public static boolean isXccovArchive(File file) {
		return file.isDirectory() && file.getName().endsWith(XCCOV_ARCHIVE_FILE_EXTENSION);
	}

	/**
	 * Runs the given conversions tasks and ensures that the given teardown is
	 * executed, even after an interrupt.
	 */
	public static <R> R runWithTeardown(ConversionFunction<R> execute, Runnable teardown)
			throws ConversionException, IOException {
		Thread cleanupShutdownHook = new Thread(teardown);
		try {
			Runtime.getRuntime().addShutdownHook(cleanupShutdownHook);

			return execute.run();
		} finally {
			Runtime.getRuntime().removeShutdownHook(cleanupShutdownHook);
			teardown.run();
		}
	}

	/** Removes the suffix from the given string, if present. */
	public static String removeSuffix(String string, String suffix) {
		if (string.endsWith(suffix)) {
			string = string.substring(0, string.length() - suffix.length());
		}
		return string;
	}
}
