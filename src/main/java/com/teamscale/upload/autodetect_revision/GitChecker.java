package com.teamscale.upload.autodetect_revision;

/**
 * Tries to detect a Git repository in the current directory and read its
 * checked out commit.
 */
public class GitChecker {

	/**
	 * If the current working directory is a valid git repository, returns its
	 * current commit SHA-1. Otherwise, returns null.
	 */
	public static String findCommit() {
		if (!isInsideGit()) {
			System.out.println("The working directory does not appear to be within a Git repository.");
			return null;
		}

		ProcessUtils.ProcessResult result = ProcessUtils.run("git", "rev-parse", "HEAD");
		if (result.wasSuccessful()) {
			String sha1 = result.output.trim();
			System.out.println("Using Git commit " + sha1);
			return sha1;
		}

		System.out.println("Failed to read checked-out Git commit. git rev-parse returned: " + result.errorOutput);
		return null;
	}

	private static boolean isInsideGit() {
		ProcessUtils.ProcessResult result = ProcessUtils.run("git", "rev-parse", "--is-inside-work-tree");
		return result.wasSuccessful() && result.output.trim().equalsIgnoreCase("true");
	}

}
