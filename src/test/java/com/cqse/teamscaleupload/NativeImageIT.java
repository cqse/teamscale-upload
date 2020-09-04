package com.cqse.teamscaleupload;

import com.cqse.teamscaleupload.autodetect_revision.ProcessUtils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Maven-generated native image in different scenarios.
 */
public class NativeImageIT {

    @Test
    public void wrongAccessKey() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withAccessKey("wrong-accesskey_"));
        assertThat(result.exitCode).isNotZero();
        assertThat(result.stdoutAndStdErr).contains("You provided incorrect credentials");
    }

    @Test
    public void wrongUser() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withUser("wrong-user_"));
        assertThat(result.exitCode).isNotZero();
        assertThat(result.stdoutAndStdErr).contains("You provided incorrect credentials");
    }

    @Test
    public void wrongProject() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withProject("wrong-project_"));
        assertThat(result.exitCode).isNotZero();
        assertThat(result.stdoutAndStdErr).contains("The project").contains("does not seem to exist in Teamscale");
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
