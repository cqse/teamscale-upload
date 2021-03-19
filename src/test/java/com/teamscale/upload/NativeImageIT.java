package com.teamscale.upload;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.test_utils.TeamscaleMockServer;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Maven-generated native image in different scenarios.
 * To run this test locally, you must provide the `build` user's access key for
 * https://demo.teamscale.com in the environment variable `ACCESS_KEY`.
 * Otherwise this test will be skipped silently.
 */
@EnabledIfEnvironmentVariable(named = "ACCESS_KEY", matches = ".*")
public class NativeImageIT {

    private static final int MOCK_TEAMSCALE_PORT = 24398;

    @Test
    public void wrongAccessKey() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withAccessKey("wrong-accesskey_"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("You provided incorrect credentials");
        softly.assertAll();
    }

    @Test
    public void incorrectUrl() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withUrl("no-protocol:9999"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("You  provided  an  invalid  URL");
        softly.assertAll();
    }

    @Test
    public void unresolvableUrl() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withUrl("http://does-not-existt:9999"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("could not be resolved");
        softly.assertAll();
    }

    @Test
    public void unreachableUrl() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withUrl("http://localhost:9999"));
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(result.exitCode).isNotZero();
        softly.assertThat(result.stdoutAndStdErr).contains("refused a connection");
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
        softly.assertThat(result.stdoutAndStdErr).contains("The pattern").contains("could not be resolved to any files");
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
    public void successfulSingleFormatUpload() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments());
        assertThat(result.exitCode)
                .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                .isZero();
    }

    @Test
    public void successfulMultiFormatUpload() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments().withInput("coverage_files/input_file"));
        assertThat(result.exitCode)
                .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                .isZero();
    }

    @Test
    public void testDefaultMessage() {
        TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT);
        ProcessUtils.ProcessResult result = runUploader(new Arguments()
                .withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
                .withAdditionalMessageLine("Build ID: 1234"));
        assertThat(result.exitCode)
                .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                .isZero();
        assertThat(server.sessions).hasSize(1).first().extracting(this::extractNormalizedMessage)
                .isEqualTo("NativeImageIT external analysis results uploaded at DATE" +
                        "\n\nuploaded from HOSTNAME" +
                        "\nfor revision: master:HEAD" +
                        "\nincludes data in the following formats: SIMPLE" +
                        "\nBuild ID: 1234");
    }

    @Test
    public void mustRejectTimestampPassedInSeconds() {
        ProcessUtils.ProcessResult result = runUploader(new Arguments()
                .withTimestamp("master:1606764633"));
        assertThat(result.exitCode)
                .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                .isNotZero();
        assertThat(result.stdoutAndStdErr).contains("seconds").contains("milliseconds")
                .contains("1970").contains("2020");
    }

    @Test
    public void selfSignedCertificateShouldBeAcceptedByDefault() {
        try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
            ProcessUtils.ProcessResult result = runUploader(new Arguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT)
                    .withInsecure());
            assertThat(result.exitCode)
                    .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                    .isZero();
            assertThat(server.sessions).hasSize(1);
        }
    }

    @Test
    public void selfSignedCertificateShouldNotBeAcceptedWhenValidationIsEnabled() {
        try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
            ProcessUtils.ProcessResult result = runUploader(new Arguments()
                    .withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT));
            assertThat(result.exitCode)
                    .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                    .isNotZero();
            assertThat(result.stdoutAndStdErr).contains("self-signed").contains("--insecure");
        }
    }

    @Test
    public void selfSignedCertificateShouldBeAcceptedWhenKeystoreIsUsed() {
        try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
            ProcessUtils.ProcessResult result = runUploader(new Arguments()
                    .withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withKeystore());
            assertThat(result.exitCode)
                    .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                    .isZero();
            assertThat(server.sessions).hasSize(1);
        }
    }

    @Test
    public void mustGuessRevision() {
        try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
            ProcessUtils.ProcessResult result = runUploader(new Arguments()
                    .withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
            assertThat(result.exitCode)
                    .describedAs("Stderr and stdout: " + result.stdoutAndStdErr)
                    .isZero();
            assertThat(server.sessions).hasSize(1);
            assertThat(server.sessions.get(0).revisionOrTimestamp).hasSize(40); // size of a git SHA1
        }
    }

    private String extractNormalizedMessage(TeamscaleMockServer.Session session) {
        return session.message.replaceAll("uploaded from .*", "uploaded from HOSTNAME")
                .replaceAll("uploaded at .*", "uploaded at DATE");
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
        private final String format = "simple";
        private final String partition = "NativeImageIT";
        private String pattern = "coverage_files\\*.simple";
        private String input = null;
        private boolean insecure = false;
        private boolean useKeystore = false;
        private boolean autoDetectCommit = false;
        private String timestamp = "master:HEAD";
        private String additionalMessageLine = null;

        private Arguments withPattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        private Arguments withInsecure() {
            this.insecure = true;
            return this;
        }

        private Arguments withTimestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        private Arguments withAutoDetectCommit() {
            this.autoDetectCommit = true;
            return this;
        }

        private Arguments withKeystore() {
            this.useKeystore = true;
            return this;
        }

        private Arguments withAdditionalMessageLine(String line) {
            this.additionalMessageLine = line;
            return this;
        }

        private Arguments withUrl(String url) {
            this.url = url;
            return this;
        }

        private Arguments withAccessKey(String accessKey) {
            this.accessKey = accessKey;
            return this;
        }

        private Arguments withUser(String user) {
            this.user = user;
            return this;
        }

        private Arguments withProject(String project) {
            this.project = project;
            return this;
        }

        private Arguments withInput(String input) {
            this.input = input;
            return this;
        }

        private String[] toStringArray() {
            List<String> arguments = new ArrayList<>(Arrays.asList("-s", url, "-u", user, "-a", accessKey, "-f", format,
                    "-p", project, "-t", partition));
            if (input != null) {
                arguments.add("-i");
                arguments.add(input);
            }
            arguments.add(pattern);
            if (insecure) {
                arguments.add("--insecure");
            }
            if (useKeystore) {
                arguments.add("--trusted-keystore");
                arguments.add(TeamscaleMockServer.TRUSTSTORE.getAbsolutePath() + ";password");
            }
            if (additionalMessageLine != null) {
                arguments.add("--append-to-message");
                arguments.add(additionalMessageLine);
            }
            if (!autoDetectCommit) {
                arguments.add("--branch-and-timestamp");
                arguments.add(timestamp);
            }

            return arguments.toArray(new String[0]);
        }
    }

    private ProcessUtils.ProcessResult runUploader(Arguments arguments) {
        return ProcessUtils.run("./target/teamscale-upload", arguments.toStringArray());
    }

}
