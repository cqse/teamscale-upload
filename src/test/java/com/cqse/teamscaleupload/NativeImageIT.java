package com.cqse.teamscaleupload;

import com.cqse.teamscaleupload.autodetect_revision.ProcessUtils;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Maven-generated native image in different scenarios.
 * To run this test locally, you must provide the `build` user's access key for
 * https://demo.teamscale.com in the environment variable `ACCESS_KEY`.
 */
public class NativeImageIT {

    @Test
    public void wrongAccessKey() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withAccessKey("wrong-accesskey_"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("You provided incorrect credentials");
        softly.assertAll();
    }

    @Test
    public void wrongUser() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withUser("wrong-user_"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("You provided incorrect credentials");
        softly.assertAll();
    }

    @Test
    public void wrongProject() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withProject("wrong-project_"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("The project").contains("does not seem to exist in Teamscale");
        softly.assertAll();
    }

    @Test
    public void patternMatchesNothing() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withPattern("**/matches.nothing"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("Could not find any files to upload");
        softly.assertAll();
    }

    @Test
    public void insufficientPermissions() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments()
                .withUser("has-no-permissions").withAccessKey("SU2nfdkpcsoOXK2zDVf2DLEQiDaMD8fM"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("is not allowed to upload data to the Teamscale project");
        softly.assertAll();
    }

    @Test
    public void successfulUpload() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments());
        assertThat(result.exitCode)
                .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                .isZero();
    }

    private static String getAccessKeyFromCi() {
        String accessKey = System.getenv("ACCESS_KEY");
        if (accessKey == null) {
            return "not-a-ci-build";
        }
        return accessKey;
    }

    private static class Arguments {
        private String url = "https://demo.teamscale.com";
        private String user = "build";
        private String accessKey = getAccessKeyFromCi();
        private String project = "teamscale-upload";
        private String format = "simple";
        private String partition = "NativeImageIT";
        private String pattern = "**.simple";

        public Arguments withPattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Arguments withAccessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        public Arguments withUser(String user) {
            this.user = user;
            return this;
        }

        public Arguments withProject(String project) {
            this.project = project;
            return this;
        }

        public String[] toStringArray() {
            return new String[]{"-s", url, "-u", user, "-a", accessKey, "-f", format,
                    "-p", project, "-t", partition, pattern};
        }
    }

    private ProcessUtils.ProcessResult runUploader(Arguments arguments) {
        return ProcessUtils.run("./target/teamscale-upload", arguments.toStringArray());
    }

}
