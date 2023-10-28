package com.teamscale.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.test_utils.TeamscaleMockServer;
import com.teamscale.upload.utils.SecretUtils;

/**
 * Integration Tests. Runs the Maven-generated native image in different scenarios.
 * This runs in the maven phase "integration tests"/"verify", not in the normal "test" phase because it requires that the "package" phase has run before.
 * <p>
 * Before you can run the test, you will need to generate the native image,
 * please refer to the repository's README.md for instructions.
 * <p>
 * You will also need to specify the access key for username
 * "teamscale-upload-build-test-user" on <a href="https://cqse.teamscale.io/">cqse.teamscale.io</a>. The user
 * has report-upload permission for project "teamscale-upload" and is used for
 * testing in the GitHub Project <a href="https://github.com/cqse/teamscale-upload">teamscale upload</a>. The
 * access token is stored as a "Secret" in GitHub. For local testing you will
 * need to set the environment variable
 * {@link SecretUtils#TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE}. It is stored
 * in 1password as "teamscale-upload-build-test-user".
 * <p>
 * We can't change the class name. It must end with "IT". In theory, you can configure the maven-failsafe-plugin "include" configuration option to something different, but it does not work (it tries to run this class in the normal, non-integration tests then, which fails).
 * TODO TS-35786: this needs to run JLink app instead
 */
@EnabledIfEnvironmentVariable(named = SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE, matches = ".*")
public class NativeImageIT {

	private static final int MOCK_TEAMSCALE_PORT = 24398;
	private static final String TEAMSCALE_TEST_USER = "teamscale-upload-build-test-user";
	public static final String TEAMSCALE_UPLOAD_EXECUTABLE = "./target/teamscale-upload";

	/**
	 * All of these tests try to call the Teamscale-upload executable.
	 * If that does not exist (build failed), running the test makes no sense.
	 */

	static void ensureExecutableExists() {
		File expectedExecutable = new File(TEAMSCALE_UPLOAD_EXECUTABLE);
		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(expectedExecutable).exists();
		softly.assertAll();
	}

	@Test
	public void wrongAccessKey() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withAccessKey("wrong-accesskey_"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("You provided incorrect credentials");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void incorrectUrl() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withUrl("no-protocol:9999"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			// the command line library we use adjusts the word spacing based on the
			// terminal width so on different machines the output may contain a different
			// number of spaces. This behaviour can unfortunately not be disabled
			softly.assertThat(result.errorOutput)
					.matches(Pattern.compile(".*You +provided +an +invalid +URL.*", Pattern.DOTALL));
		});
	}

	@Test
	public void unresolvableUrl() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withUrl("http://domain.invalid:9999"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("could not be resolved");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void unreachableUrl() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withUrl("http://localhost:9999"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("refused a connection");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void wrongUser() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withUser("wrong-user_"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("You provided incorrect credentials");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void wrongProject() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withProject("wrong-project_"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("The project")
					.contains("does not seem to exist in Teamscale");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void timeoutTooSmall() {
		ProcessUtils.ProcessResult result = runUploader(
				new Arguments().withUrl("http://localhost:9999").withTimeoutInSeconds("0"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("The timeout in seconds")
					.contains("must be an integer greater").contains("than 0.");
		});
	}

	@Test
	public void timeoutIsNotANumber() {
		ProcessUtils.ProcessResult result = runUploader(
				new Arguments().withUrl("http://localhost:9999").withTimeoutInSeconds("foo"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("The timeout in seconds")
					.contains("must be an integer greater").contains("than 0.");
		});
	}

	/**
	 * TS-28014: Sending an unknown revision also results in a 404 status code,
	 * which used to display a misleading error message saying "the project ID is
	 * not known". This test ensures that this scenario is handled better now.
	 */
	@Test
	public void unknownRevision() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withCommit("doesnt-exist"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).doesNotContain("The project")
					.doesNotContain("does not seem to exist in Teamscale")
					.doesNotContain("project ID").contains("The revision")
					.contains("is not known to Teamscale or the version control system(s) you configured");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void patternMatchesNothing() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withPattern("**/matches.nothing"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("The pattern")
					.contains("could not be resolved to any files");
		});
	}

	@Test
	public void insufficientPermissions() {
		ProcessUtils.ProcessResult result = runUploader(
				new Arguments().withUser("teamscale-upload-build-test-user-no-permissions").withAccessKey("ruG8MKMbLunyLooB7SlfkEATCISSWDKy"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("is not allowed to upload data to the Teamscale project");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void successfulSingleFormatUpload() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments());
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void successfulMultiFormatUpload() {
		ProcessUtils.ProcessResult result = runUploader(
				new Arguments().withInput("src/test/resources/coverage_files/input_file"));
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void testDefaultMessage() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new Arguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAdditionalMessageLine("Build ID: 1234"));
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1).first().extracting(this::extractNormalizedMessage)
					.isEqualTo("NativeImageIT external analysis results uploaded at DATE" + "\n\nuploaded from HOSTNAME"
							+ "\nfor revision: master:HEAD" + "\nincludes data in the following formats: SIMPLE"
							+ "\nBuild ID: 1234");
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void testTimeoutSmallerThanRequestTime() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, false, 2)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withTimeoutInSeconds("1"));
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
					.isNotZero();
			assertThat(result.errorOutput).contains("Request timeout reached.");
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void testTimeoutGreaterThanRequestTime() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, false, 2)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withTimeoutInSeconds("3"));
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void mustRejectTimestampPassedInSeconds() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withTimestamp("master:1606764633"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
					.isNotZero();
			softly.assertThat(result.errorOutput).contains("seconds").contains("milliseconds").contains("1970")
					.contains("2020");
		});
	}

	@Test
	public void selfSignedCertificateShouldBeAcceptedWithInsecureFlag() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withInsecure());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void selfSignedCertificateShouldNotBeAcceptedByDefault() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isNotZero();
				softly.assertThat(result.errorOutput).contains("self-signed").contains("--insecure");
			});
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void printStackTraceForKnownErrorsOnlyWhenRequested() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isNotZero();
				softly.assertThat(result.errorOutput).contains("--stacktrace");
			});
			assertThatOSCertificatesWereImported(result);
		}

		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withStackTrace());
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isNotZero();
				softly.assertThat(result.errorOutput).contains("\tat com.teamscale.upload.TeamscaleUpload")
						.doesNotContain("--stacktrace");
			});
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void selfSignedCertificateShouldBeAcceptedWhenKeystoreIsUsed() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withKeystore().withStackTrace());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void mustGuessRevision() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(
					new Arguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThat(server.sessions.get(0).revisionOrTimestamp).hasSize(40); // size of a git SHA1
			assertThatOSCertificatesWereImported(result);
		}
	}

	/** Tests that passing the access key via stdin works as expected (TS-28611). */
	@Test
	public void testCorrectAccessKeyFromStdIn() throws IOException {
		Path tempFilePath = null;
		try {
			// We create a temporary file where we write the correct access key from the
			// environment variable to test the input via stdin. We do not commit this file
			// as we do not want to leak the access key as plain string in the repository.
			String temporaryFileName = "temporary_access_key.txt";
			tempFilePath = Paths.get(temporaryFileName);
			Files.writeString(tempFilePath, System.getenv(SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE));

			ProcessUtils.ProcessResult result = runUploader(new Arguments().withAccessKeyViaStdin(temporaryFileName));
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThatOSCertificatesWereImported(result);
		} finally {
			if (tempFilePath != null && Files.exists(tempFilePath)) {
				Files.delete(tempFilePath);
			}
		}
	}

	/** Tests that passing an incorrect access key via stdin fails (TS-28611). */
	@Test
	public void testIncorrectAccessKeyFromStdIn() {
		ProcessUtils.ProcessResult result = runUploader(
				new Arguments().withAccessKeyViaStdin("src/test/resources/incorrect_access_key.txt"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("You provided incorrect credentials");
		});
		assertThatOSCertificatesWereImported(result);
	}

	/**
	 * Tests that passing the access key via environment variable works as expected
	 * (TS-28611).
	 */
	@Test
	public void testCorrectAccessWithKeyFromEnvironmentVariable() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withoutAccessKeyInOption());
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);

	}

	@Test
	@EnabledOnOs(OS.MAC)
	public void testXCResultConversion() throws IOException {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new Arguments()
					.withPattern("src/test/resources/coverage_files/output.xcresult.tar.gz").withFormat("XCODE")
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThat(server.uploadedReportsByName).hasSize(1);
			assertThat(server.uploadedReportsByName.get("output.xcresult.tar.gz.xccov"))
					.containsExactly(readResource("output.xcresult.tar.gz.xccov.expected"));
			assertThatOSCertificatesWereImported(result);
		}
	}

	/**
	 * This test uploads a report to our Teamscale server using a commit hash as upload target.
	 * <p>
	 * Since the hash must be a real commit hash, and we keep adding new commits to the project, this commit will get "old".
	 * New uploads to the old commit will cause rollbacks.
	 * We used a commit on master (hash b80faaa9fba686debfc410eb34a564dc30510b7d from 26.Aug 2020), therefore these rollbacks were a problem.
	 * Now we use a commit on an extra branch that was created from the commit above.
	 * The new commit is 3758a3a6c2d62ab787574f869b2352480c6f0c10 on branch "branch_for_upload_test".
	 * Uploads will roll back the new branch, but that should not be a problem since there are no further commits on this branch.
	 */
	@Test
	public void successfulUploadWithRepository() {
		ProcessUtils.ProcessResult result = runUploader(
				new Arguments().withRepository("cqse/teamscale-upload").withPartition("NativeImageIT > TestRepository")
						.withCommit("3758a3a6c2d62ab787574f869b2352480c6f0c10"));
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.errorOutput).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void successfulUploadWithMoveToLastCommit() {
		ProcessUtils.ProcessResult result = runUploader(new Arguments().withMoveToLastCommit());
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.errorOutput).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	private void assertThatOSCertificatesWereImported(ProcessUtils.ProcessResult result) {
		assertSoftlyThat(softly -> {
			softly.assertThat(result.errorOutput)
					.doesNotContain("Could not import certificates from the operating system");
			softly.assertThat(result.output).containsPattern(Pattern.compile("Imported \\d+ certificates from the operating system\\."));
		});
	}

	private byte[] readResource(String name) throws IOException {
		try (InputStream stream = NativeImageIT.class.getResourceAsStream(name)) {
			if (stream == null) {
				return null;
			}
			return stream.readAllBytes();
		}
	}

	private void assertSoftlyThat(Consumer<SoftAssertions> verifier) {
		SoftAssertions softly = new SoftAssertions();
		verifier.accept(softly);
		softly.assertAll();
	}

	private String extractNormalizedMessage(TeamscaleMockServer.Session session) {
		return session.message.replaceAll("uploaded from .*", "uploaded from HOSTNAME").replaceAll("uploaded at .*",
				"uploaded at DATE");
	}

	private ProcessUtils.ProcessResult runUploader(Arguments arguments) {
		return ProcessUtils.runWithStdIn(arguments.stdinFile, arguments.toCommand(TEAMSCALE_UPLOAD_EXECUTABLE));
	}

	private static String getAccessKeyFromCi() {
		String accessKey = System.getenv(SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE);
		if (accessKey == null) {
			return "not-a-ci-build";
		}
		return accessKey;
	}

	private static class Arguments {
		private String partition = "NativeImageIT";
		private String url = "https://cqse.teamscale.io/";
		private String user = TEAMSCALE_TEST_USER;
		private String accessKey = getAccessKeyFromCi();
		private String project = "teamscale-upload";
		private String format = "simple";
		private String pattern = "src/test/resources/coverage_files\\*.simple";
		private String input = null;
		private boolean insecure = false;
		private boolean useKeystore = false;
		private boolean autoDetectCommit = false;
		private String timestamp = "master:HEAD";
		private String commit = null;
		private String repository = null;
		private String additionalMessageLine = null;
		private boolean stackTrace = false;
		private File stdinFile = null;
		private boolean moveToLastCommit = false;
		private String timeoutInSeconds = null;

		private Arguments withFormat(String format) {
			this.format = format;
			return this;
		}

		private Arguments withPattern(String pattern) {
			this.pattern = pattern;
			return this;
		}

		private Arguments withInsecure() {
			this.insecure = true;
			return this;
		}

		private Arguments withCommit(String commit) {
			this.commit = commit;
			return this;
		}

		private Arguments withTimestamp(String timestamp) {
			this.timestamp = timestamp;
			return this;
		}

		private Arguments withRepository(String repository) {
			this.repository = repository;
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

		private Arguments withStackTrace() {
			this.stackTrace = true;
			return this;
		}

		private Arguments withAccessKey(String accessKey) {
			this.accessKey = accessKey;
			return this;
		}

		/**
		 * No access key is specified as option. The key which is specified in the
		 * environment variable should be used instead.
		 */
		private Arguments withoutAccessKeyInOption() {
			this.accessKey = null;
			return this;
		}

		private Arguments withAccessKeyViaStdin(String stdinFilePath) {
			this.accessKey = "-";
			// If the access key is set to '-', we need to pipe the key from a file via
			// stdin.
			this.stdinFile = new File(stdinFilePath);
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

		private Arguments withPartition(String partition) {
			this.partition = partition;
			return this;
		}

		private Arguments withMoveToLastCommit() {
			this.moveToLastCommit = true;
			return this;
		}

		private Arguments withTimeoutInSeconds(String timeoutInSeconds) {
			this.timeoutInSeconds = timeoutInSeconds;
			return this;
		}

		private String[] toCommand(String executable) {
			List<String> command = new ArrayList<>(
					Arrays.asList(executable, "-s", url, "-u", user, "-f", format, "-p", project, "-t", partition));
			if (accessKey != null) {
				command.add("-a");
				command.add(accessKey);
			}
			if (input != null) {
				command.add("-i");
				command.add(input);
			}
			command.add(pattern);
			if (insecure) {
				command.add("--insecure");
			}
			if (useKeystore) {
				command.add("--trusted-keystore");
				command.add(TeamscaleMockServer.TRUSTSTORE.getAbsolutePath() + ";password");
			}
			if (additionalMessageLine != null) {
				command.add("--append-to-message");
				command.add(additionalMessageLine);
			}
			if (stackTrace) {
				command.add("--stacktrace");
			}
			if (moveToLastCommit) {
				command.add("--move-to-last-commit");
			}

			if (commit != null) {
				command.add("--commit");
				command.add(commit);
			} else if (!autoDetectCommit) {
				command.add("--branch-and-timestamp");
				command.add(timestamp);
			}

			if (repository != null) {
				command.add("--repository");
				command.add(repository);
			}
			if (timeoutInSeconds != null) {
				command.add("--timeout");
				command.add(timeoutInSeconds);
			}

			return command.toArray(new String[0]);
		}
	}

}
