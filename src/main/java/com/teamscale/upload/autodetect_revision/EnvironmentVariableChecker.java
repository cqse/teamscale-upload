package com.teamscale.upload.autodetect_revision;

import java.util.Arrays;
import java.util.List;

/**
 * Checks well-known environment variables for commit infos.
 */
public class EnvironmentVariableChecker {

    private static final List<String> COMMIT_ENVIRONMENT_VARIABLES = Arrays.asList(
            // user-specified as a fallback
            "COMMIT",

            // Git
            "GIT_COMMIT", // Jenkins https://www.theserverside.com/blog/Coffee-Talk-Java-News-Stories-and-Opinions/Complete-Jenkins-Git-environment-variables-list-for-batch-jobs-and-shell-script-builds
            "Build.SourceVersion", // Azure DevOps https://docs.microsoft.com/en-us/azure/devops/pipelines/build/variables?view=azure-devops&tabs=yaml#build-variables
            "CIRCLE_SHA1", // Circle CI https://circleci.com/docs/2.0/env-vars/#built-in-environment-variables
            "TRAVIS_COMMIT", // Travis CI https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
            "BITBUCKET_COMMIT", // Bitbucket Pipelines https://confluence.atlassian.com/bitbucket/environment-variables-794502608.html
            "CI_COMMIT_SHA", // GitLab Pipelines https://docs.gitlab.com/ee/ci/variables/predefined_variables.html
            "APPVEYOR_REPO_COMMIT", // AppVeyor https://www.appveyor.com/docs/environment-variables/
            "GITHUB_SHA", // GitHub actions https://help.github.com/en/actions/configuring-and-managing-workflows/using-environment-variables#default-environment-variables

            // SVN
            "SVN_REVISION", // Jenkins https://stackoverflow.com/questions/43780145/no-svn-revision-in-jenkins-environment-variable https://issues.jenkins-ci.org/browse/JENKINS-14797

            // Both
            "build_vcs_number" // TeamCity https://confluence.jetbrains.com/display/TCD8/Predefined+Build+Parameters https://stackoverflow.com/questions/2882953/how-to-get-branch-specific-svn-revision-numbers-in-teamcity
    );

    /**
     * Returns either a commit that was found in an environment variable (Git SHA1 or SVN revision number or TFS changeset number) or null if none was found.
     */
    public static String findCommit() {
        for (String variable : COMMIT_ENVIRONMENT_VARIABLES) {
            String commit = System.getenv(variable);
            if (commit != null) {
                System.out.println("Using commit/revision/changeset " + commit + " from environment variable " + variable);
                return commit;
            }
        }


        System.out.println("Found no commit/revision/changeset info in any environment variables.");
        return null;
    }

}
