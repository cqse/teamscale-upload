package com.teamscale.upload.xcode;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.LogUtils;

/**
 * Represents an Xcode version.
 */
public final class XCodeVersion {

	/**
	 * Pattern that matches the output of an {@code xcodebuild -version} command to
	 * determine the installed version.
	 */
	private static final Pattern XCODE_BUILD_VERSION_PATTERN = Pattern
			.compile("^Xcode (?<major>\\d+)\\.(?<minor>\\d+)");
	/**
	 * The minor version number (e.g., {@code 16} for version {@code 16.1} or
	 * {@link Integer#MAX_VALUE} for latest version)
	 */
	public final int major;

	/**
	 * The minor version number (e.g., {@code 1} for version {@code 16.1} or
	 * {@link Integer#MAX_VALUE} for latest version)
	 */
	public final int minor;

	public XCodeVersion(int major, int minor) {
		this.major = major;
		this.minor = minor;
	}

	/** Determines the version installed on this machine. */
	public static XCodeVersion determine() {
		ProcessUtils.ProcessResult result = ProcessUtils.run("xcodebuild", "-version");
		if (!result.wasSuccessful()) {
			LogUtils.warn("Could not determine installed Xcode version. Assuming latest Xcode version is installed.");
			LogUtils.debug("Error whilst running 'xcodebuild -version' command: " + result.errorOutput,
					result.exception);
			return latestVersion();
		}

		String xcodeBuildVersionCommandOutput = result.output;
		Matcher xcodeVersionPatternMatcher = XCODE_BUILD_VERSION_PATTERN.matcher(xcodeBuildVersionCommandOutput);
		if (!xcodeVersionPatternMatcher.find()) {
			LogUtils.warn("Could not determine installed Xcode version. Assuming latest Xcode version is installed.");
			LogUtils.debug("Output of 'xcodebuild -version' command:\n" + xcodeBuildVersionCommandOutput);
			return latestVersion();
		}

		int major = Integer.parseInt(xcodeVersionPatternMatcher.group("major"));
		int minor = Integer.parseInt(xcodeVersionPatternMatcher.group("minor"));
		return new XCodeVersion(major, minor);
	}

	/**
	 * Returns a {@link XCodeVersion} that represents the latest version.
	 * <p>
	 * Instead of determining the version via the web, {@link #major} and
	 * {@link #minor} will be simply set to {@link Integer#MAX_VALUE}.
	 */
	private static XCodeVersion latestVersion() {
		return new XCodeVersion(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}

		XCodeVersion that = (XCodeVersion) obj;
		return this.major == that.major && this.minor == that.minor;
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor);
	}

	@Override
	public String toString() {
		return "XcodeVersion " + major + "." + minor;
	}
}
