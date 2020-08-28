package com.cqse.teamscaleupload.autodetect_revision;

public class GitChecker {

    public static String findCommit() {
        if (!isInsideGit()) {
            System.out.println("The working directory does not appear to be within a Git repository.");
            return null;
        }

        ProcessUtils.ProcessResult result = ProcessUtils.run("git", "rev-parse", "HEAD");
        if (result.wasSuccessful()) {
            String sha1 = result.stdoutAndStdErr.trim();
            System.out.println("Using Git commit " + sha1);
            return sha1;
        }

        System.out.println("Failed to read checked-out Git commit. git rev-parse returned: " + result.stdoutAndStdErr);
        return null;
    }

    private static boolean isInsideGit() {
        ProcessUtils.ProcessResult result = ProcessUtils.run("git", "rev-parse", "--is-inside-work-tree");
        return result.wasSuccessful() && result.stdoutAndStdErr.trim().equalsIgnoreCase("true");
    }

}
