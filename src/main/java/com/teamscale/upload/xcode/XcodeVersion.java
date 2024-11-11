package com.teamscale.upload.xcode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.LogUtils;

/**
 * Represents an Xcode version.
 *
 * @param major
 *            The minor version number (e.g., {@code 16} for version
 *            {@code 16.1} or {@link Integer#MAX_VALUE} for latest version)
 * @param minor
 *            The minor version number (e.g., {@code 1} for version {@code 16.1}
 *            or {@link Integer#MAX_VALUE} for latest version)
 */
public record XcodeVersion(int major, int minor) {

	/**
	 * Pattern that matches the output of an {@code xcodebuild -version} command to
	 * determine the installed version.
	 */
	private static final Pattern XCODE_BUILD_VERSION_PATTERN = Pattern
			.compile("^Xcode (?<major>\\d+)\\.(?<minor>\\d+)");

	/** Determines the version installed on this machine. */
	public static XcodeVersion determine() {
		ProcessUtils.ProcessResult result = ProcessUtils.run("xcodebuild", "-version");
		if (!result.wasSuccessful()) {
			LogUtils.warn("Could not determine installed Xcode version. Assuming latest Xcode version is installed.");
			LogUtils.debug("Error whilst running 'xcodebuild -version' command: ", result.exception);
			return latestVersion();
		}

		String xcodeBuildVersionCommandOutput = result.output;
		Matcher m = XCODE_BUILD_VERSION_PATTERN.matcher(xcodeBuildVersionCommandOutput);
		if (!m.find()) {
			LogUtils.warn("Could not determine installed Xcode version. Assuming latest Xcode version is installed.");
			LogUtils.debug("Output of 'xcodebuild -version' command:\n" + xcodeBuildVersionCommandOutput);
			return latestVersion();
		}

		int major = Integer.parseInt(m.group("major"));
		int minor = Integer.parseInt(m.group("minor"));
		return new XcodeVersion(major, minor);
	}

	/**
	 * Returns a {@link XcodeVersion} that represents the latest version.
	 * <p>
	 * Instead of determining the version via the web, {@link #major} and
	 * {@link #minor} will be simply set to {@link Integer#MAX_VALUE}.
	 */
	private static XcodeVersion latestVersion() {
		return new XcodeVersion(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}
}
