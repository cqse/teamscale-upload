package com.cqse.teamscaleupload.autodetect_revision;

/**
 * Utilities for automatically detecting the commit to which to upload data.
 */
public class AutodetectCommitUtils {

    /**
     * Tries to automatically detect the commit to which data should be uploaded.
     * Returns null if no such commit can be detected.
     */
    public static String detectCommit() {
        String commit = EnvironmentVariableChecker.findCommit();
        if (commit != null) {
            return commit;
        }

        commit = GitChecker.findCommit();
        if (commit != null) {
            return commit;
        }

        return SvnChecker.findRevision();
    }

}
