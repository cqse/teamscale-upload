package com.teamscale.upload.autodetect_revision;

public class SvnChecker {

    public static String findRevision() {
        if (!isInsideSvn()) {
            System.out.println("The working directory does not appear to be within an SVN repository.");
            return null;
        }

        ProcessUtils.ProcessResult result = ProcessUtils.run("svn", "info", "--show-item", "revision");
        if (result.wasSuccessful()) {
            String revision = result.stdoutAndStdErr.trim();
            System.out.println("Using SVN revision " + revision);
            return revision;
        }

        System.out.println("Failed to read checked-out SVN revision. svn info --show-item revision returned: " +
                result.stdoutAndStdErr);
        return null;
    }

    private static boolean isInsideSvn() {
        ProcessUtils.ProcessResult result = ProcessUtils.run("svn", "info");
        return result.wasSuccessful() && result.stdoutAndStdErr.contains("URL:");
    }

}
