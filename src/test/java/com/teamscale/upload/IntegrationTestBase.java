package com.teamscale.upload;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.test_utils.ProxyMockServer;
import com.teamscale.upload.test_utils.TeamscaleMockServer;
import com.teamscale.upload.utils.SecretUtils;

/**
 * Integration Tests. Subclasses execute the distribution in different
 * scenarios.
 */
public abstract class IntegrationTestBase {

	private static final int MOCK_TEAMSCALE_PORT = 24398;

	/**
	 * Executes the generated teamscale-upload distribution with the given
	 * arguments.
	 */
	protected abstract ProcessUtils.ProcessResult runUploader(UploadArguments arguments);

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void wrongAccessKey() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withAccessKey("wrong-accesskey_"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("You provided incorrect credentials");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void incorrectUrl() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withUrl("no-protocol:9999"));
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
		ProcessUtils.ProcessResult result = runUploader(
				new ReportUploadArguments().withUrl("http://domain.invalid:9999"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput)
					.contains("The host http://domain.invalid:9999/ could not be resolved");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void unreachableUrl() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withUrl("http://localhost:9999"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("The host http://localhost:9999/ refused a connection");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@EnabledIfEnvironmentVariable(named = SecretUtils.TEAMSCALE_PROXY_USER, matches = ".*")
	@EnabledIfEnvironmentVariable(named = SecretUtils.TEAMSCALE_PROXY_PASSWORD, matches = ".*")
	public void testProxyWithAuth() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT);
				// Only needs to be started.
				ProxyMockServer ignored = new ProxyMockServer(true)) {

			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
							.withProxy("localhost:" + ProxyMockServer.PORT).withDebug());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(result.getOutputAndErrorOutput()).contains("Proxy Connection successful");
		}
	}

	@Test
	public void testProxy() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT);
				// Only needs to be started.
				ProxyMockServer ignored = new ProxyMockServer(false)) {

			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
							.withProxy("localhost:" + ProxyMockServer.PORT).withDebug());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(result.getOutputAndErrorOutput()).contains("Proxy Connection successful");
		}
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void wrongUser() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withUser("wrong-user_"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("You provided incorrect credentials");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void wrongProject() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withProject("wrong-project_"));
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
				new ReportUploadArguments().withUrl("http://localhost:9999").withTimeoutInSeconds("0"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			// we ignore whitespace because the string is formatted differently coming out
			// of the jlink dist than from graalvm (different line width)
			softly.assertThat(result.errorOutput)
					.containsIgnoringWhitespaces("The timeout in seconds must be an integer greater than 0.");
		});
	}

	@Test
	public void timeoutIsNotANumber() {
		ProcessUtils.ProcessResult result = runUploader(
				new ReportUploadArguments().withUrl("http://localhost:9999").withTimeoutInSeconds("foo"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			// we ignore whitespace because the string is formatted differently coming out
			// of the jlink dist than from graalvm (different line width)
			softly.assertThat(result.errorOutput)
					.containsIgnoringWhitespaces("The timeout in seconds must be an integer greater than 0.");
		});
	}

	/**
	 * TS-28014: Sending an unknown revision also results in a 404 status code,
	 * which used to display a misleading error message saying "the project ID is
	 * not known". This test ensures that this scenario is handled better now.
	 */
	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void unknownRevision() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withCommit("doesnt-exist"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).doesNotContain("The project")
					.doesNotContain("does not seem to exist in Teamscale").doesNotContain("project ID")
					.contains("The revision")
					.contains("is not known to Teamscale or the version control system(s) you configured");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void patternMatchesNothing() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withPattern("**/matches.nothing"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("The pattern")
					.contains("could not be resolved to any files");
		});
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void insufficientPermissions() {
		ProcessUtils.ProcessResult result = runUploader(
				new ReportUploadArguments().withUser("teamscale-upload-build-test-user-no-permissions")
						.withAccessKey("ruG8MKMbLunyLooB7SlfkEATCISSWDKy"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("is not allowed to upload data to the Teamscale project");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void successfulSingleFormatUpload() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments());
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void successfulMultiFormatUpload() {
		ProcessUtils.ProcessResult result = runUploader(
				new ReportUploadArguments().withInput("src/test/resources/coverage_files/input_file"));
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void testDefaultMessage() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
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
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withTimeoutInSeconds("1"));
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
					.isNotZero();
			assertThat(result.errorOutput).contains("Request timeout reached.");
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void testTimeoutGreaterThanRequestTime() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, false, 2)) {
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withTimeoutInSeconds("3"));
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void mustRejectTimestampPassedInSeconds() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withTimestamp("master:1606764633"));
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
					new ReportUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withInsecure());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
		}
	}

	@Test
	public void selfSignedCertificateShouldNotBeAcceptedByDefault() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT));
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
					new ReportUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isNotZero();
				softly.assertThat(result.errorOutput).contains("--stacktrace");
			});
			assertThatOSCertificatesWereImported(result);
		}

		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withStackTrace());
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
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
					.withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withKeystore().withStackTrace());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void mustGuessRevision() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThat(server.sessions.get(0).revisionOrTimestamp()).hasSize(40); // size of a git SHA1
			assertThatOSCertificatesWereImported(result);
		}
	}

	/** Tests that passing the access key via stdin works as expected (TS-28611). */
	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void testCorrectAccessKeyFromStdIn() throws IOException {
		Path tempFilePath = null;
		try {
			// We create a temporary file where we write the correct access key from the
			// environment variable to test the input via stdin. We do not commit this file
			// as we do not want to leak the access key as plain string in the repository.
			String temporaryFileName = "temporary_access_key.txt";
			tempFilePath = Paths.get(temporaryFileName);
			Files.writeString(tempFilePath, System.getenv(SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE));

			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withAccessKeyViaStdin(temporaryFileName));
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
	@Disabled("TS-41072 Test should not run against production server")
	public void testIncorrectAccessKeyFromStdIn() {
		ProcessUtils.ProcessResult result = runUploader(
				new ReportUploadArguments().withAccessKeyViaStdin("src/test/resources/incorrect_access_key.txt"));
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
	@Disabled("TS-41072 Test should not run against production server")
	public void testCorrectAccessWithKeyFromEnvironmentVariable() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withoutAccessKeyInOption());
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@EnabledOnOs(OS.MAC)
	public void testXCResultConversion() throws IOException {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
					.withPattern("src/test/resources/coverage_files/output.xcresult.tar.gz").withFormat("XCODE")
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
			String actualConvertedContent = new String(
					requireNonNull(server.uploadedReportsByName.get("output.xcresult.tar.gz.xccov")),
					StandardCharsets.UTF_8);
			String expectedConvertedContent = new String(
					requireNonNull(readResource("output.xcresult.tar.gz.xccov.expected")), StandardCharsets.UTF_8);

			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThat(server.uploadedReportsByName).hasSize(1);
			assertThat(actualConvertedContent).isEqualTo(expectedConvertedContent);
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void testNonExistingFilePattern() {
		ProcessUtils.ProcessResult result = runUploader(
				new ReportUploadArguments().withPattern("foo.simple bar.simple"));
		assertThat(result.errorOutput).isEqualToIgnoringNewLines(
				"The pattern 'foo.simple bar.simple' could not be resolved to any files. Please check the pattern for correctness or remove it if you do not need it.");
		assertThat(result.exitCode).isOne();
	}

	/**
	 * This test uploads a report to our Teamscale server using a commit hash as
	 * upload target.
	 * <p>
	 * Since the hash must be a real commit hash, and we keep adding new commits to
	 * the project, this commit will get "old". New uploads to the old commit will
	 * cause rollbacks. We used a commit on master (hash
	 * b80faaa9fba686debfc410eb34a564dc30510b7d from 26.Aug 2020), therefore these
	 * rollbacks were a problem. Now we use a commit on an extra branch that was
	 * created from the commit above. The new commit is
	 * 3758a3a6c2d62ab787574f869b2352480c6f0c10 on branch "branch_for_upload_test".
	 * Uploads will roll back the new branch, but that should not be a problem since
	 * there are no further commits on this branch.
	 */
	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void successfulUploadWithRepository() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
				.withRepository("cqse/teamscale-upload").withPartition("NativeImageIT > TestRepository")
				.withCommit("3758a3a6c2d62ab787574f869b2352480c6f0c10"));
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.errorOutput).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	/**
	 * An input file without any patterns leaves nothing to upload. That is not an
	 * error, as a build that produced no reports should not fail the pipeline, so
	 * we say so and stop without contacting Teamscale at all.
	 */
	@Test
	public void uploadWithoutAnyReportsIsSkipped(@TempDir Path directory) throws IOException {
		Path emptyInputFile = Files.createFile(directory.resolve("empty_input_file"));
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
							.withInput(emptyInputFile.toString()).withoutPattern());
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isZero();
				softly.assertThat(result.errorOutput).contains("There are no files to upload");
				softly.assertThat(server.sessions).isEmpty();
				// no client is built, so the operating system's certificates stay unread
				softly.assertThat(result.output).doesNotContain("certificates from the operating system");
			});
		}
	}

	/**
	 * Tests that if an upload is made to Teamscale with no user-explicit revision
	 * provided, the auto-detected revision instead of <code>null</code> is
	 * mentioned in the error message if it is not known to Teamscale.
	 */
	@Test
	public void unknownAutodetectedRevisionIsMentionedInErrorMessage() {
		try (TeamscaleMockServer ignored = TeamscaleMockServer.respondingWith(MOCK_TEAMSCALE_PORT, 404,
				"Revision is not known to any of the available VCS repositories")) {
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput).doesNotContain("The revision 'null'");
				// the detected git SHA1, which the user never passed
				softly.assertThat(result.errorOutput)
						.containsPattern("The revision '[0-9a-f]{40}' is not known to Teamscale");
			});
		}
	}

	@Test
	public void retrySucceedsAfterIntermittentFailure() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, false, 0L, 1)) {
			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withMaxAttempts(3));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isZero();
				softly.assertThat(result.getOutputAndErrorOutput()).contains("Failed attempt 1 / 3");
				softly.assertThat(server.sessions).hasSize(1);
			});
		}
	}

	/**
	 * The commit is detected before the first attempt, not per attempt: detection
	 * shells out to Git or SVN and announces what it found, so repeating it would
	 * both cost a subprocess per attempt and make the log read as if the upload
	 * targeted a freshly derived commit each time.
	 */
	@Test
	public void retriedUploadDetectsTheCommitOnlyOnce() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, false, 0L, 2)) {
			ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit().withMaxAttempts(3));
			String output = result.getOutputAndErrorOutput();
			// which message announces the commit depends on where it was found, and that
			// differs between a CI build and a local checkout
			long detections = Pattern.compile("Using (?:Git commit|SVN revision|commit/revision/changeset) ")
					.matcher(output).results().count();
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + output).isZero();
				softly.assertThat(output).contains("Failed attempt 1 / 3").contains("Failed attempt 2 / 3");
				softly.assertThat(detections).describedAs("Stderr and stdout: " + output).isOne();
				softly.assertThat(server.sessions).hasSize(1);
			});
		}
	}

	@Test
	public void retryExhaustedWithUnreachableUrl() {
		ProcessUtils.ProcessResult result = runUploader(
				new ReportUploadArguments().withUrl("http://localhost:9999").withMaxAttempts(2));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("Failed attempt 1 / 2");
			softly.assertThat(result.errorOutput).contains("Upload failed after 2 attempt(s)");
		});
	}

	@Test
	public void nonRetriableErrorDoesNotRetry() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withMaxAttempts(3));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput).doesNotContain("Failed attempt");
				softly.assertThat(result.errorOutput).contains("Failed to connect via HTTPS");
			});
		}
	}

	@Test
	public void vulnerabilityReportUploadSendsAllParameters() throws IOException {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(
					new VulnerabilityReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
							.withBuildName("my-service").withBuildVersion("1.4.2").withCommit("abcdef1234"));

			Path expectedReport = Paths.get(VulnerabilityReportUploadArguments.DEFAULT_REPORT_PATH);
			byte[] expectedContent = Files.readAllBytes(expectedReport);

			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.vulnerabilityReportUploads).hasSize(1);

			TeamscaleMockServer.VulnerabilityReportUpload upload = server.vulnerabilityReportUploads.get(0);
			assertSoftlyThat(softly -> {
				softly.assertThat(upload.buildName()).isEqualTo("my-service");
				softly.assertThat(upload.version()).isEqualTo("1.4.2");
				softly.assertThat(upload.revision()).isEqualTo("abcdef1234");
				softly.assertThat(upload.fileName()).isEqualTo(expectedReport.getFileName().toString());
				softly.assertThat(upload.content()).isEqualTo(expectedContent);
			});
		}
	}

	@Test
	public void vulnerabilityReportUploadAutodetectsCommit() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new VulnerabilityReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.vulnerabilityReportUploads).hasSize(1);
			// The revision is auto-detected from $GITHUB_SHA and friends in CI, or
			// otherwise from the teamscale-upload Git checkout the tests run in. We only
			// assert the length of the git SHA1 because the two sources disagree on the
			// exact value: on pull requests, $GITHUB_SHA is the merge commit, not the
			// checked-out HEAD.
			assertThat(server.vulnerabilityReportUploads.get(0).revision()).hasSize(40);
		}
	}

	@Test
	public void vulnerabilityReportUploadWithoutBuildNameIsRejected() {
		ProcessUtils.ProcessResult result = runUploader(
				new VulnerabilityReportUploadArguments().withUrl("http://localhost:9999").withoutBuildName());
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).containsIgnoringWhitespaces("argument --build-name is required");
		});
	}

	@Test
	public void vulnerabilityReportUploadWithoutBuildVersionIsRejected() {
		ProcessUtils.ProcessResult result = runUploader(
				new VulnerabilityReportUploadArguments().withUrl("http://localhost:9999").withoutBuildVersion());
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).containsIgnoringWhitespaces("argument --build-version is required");
		});
	}

	@Test
	public void vulnerabilityReportUploadRejectsReservedCharacterInBuildName() {
		ProcessUtils.ProcessResult result = runUploader(
				new VulnerabilityReportUploadArguments().withUrl("http://localhost:9999").withBuildName("my#service"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			// the command line library adjusts the word spacing based on the terminal
			// width.
			// The option must be named, as it also appears in the usage line for any other
			// argument error
			softly.assertThat(result.errorOutput)
					.containsIgnoringWhitespaces("The value you provided for --build-name contains '#'")
					.containsIgnoringWhitespaces("must not appear");
		});
	}

	@Test
	public void vulnerabilityReportUploadRejectsReservedCharacterInCommit() {
		ProcessUtils.ProcessResult result = runUploader(
				new VulnerabilityReportUploadArguments().withUrl("http://localhost:9999").withCommit("abc#def"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput)
					.containsIgnoringWhitespaces("The value you provided for --commit contains '#'");
			// nothing is sent, so the failure cannot be Teamscale's
			softly.assertThat(result.errorOutput).doesNotContain("Teamscale rejected the upload request");
		});
	}

	@Test
	public void vulnerabilityReportUploadRejectsEmptyBuildName() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new VulnerabilityReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withBuildName(""));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput)
						.containsIgnoringWhitespaces("The value you provided for --build-name is blank");
				// Teamscale would answer this with a 400, so the point of the check is that we
				// never get there
				softly.assertThat(server.vulnerabilityReportUploads).isEmpty();
			});
		}
	}

	/**
	 * Teamscale rejects a blank identifier, not merely an empty one, so a value of
	 * spaces must not travel to the server either.
	 */
	@Test
	public void vulnerabilityReportUploadRejectsWhitespaceOnlyBuildVersion() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new VulnerabilityReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withBuildVersion("   "));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput)
						.containsIgnoringWhitespaces("The value you provided for --build-version is blank");
				softly.assertThat(server.vulnerabilityReportUploads).isEmpty();
			});
		}
	}

	@Test
	public void vulnerabilityReportUploadWithoutFileIsRejected() {
		ProcessUtils.ProcessResult result = runUploader(
				new VulnerabilityReportUploadArguments().withUrl("http://localhost:9999").withoutPattern());
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).containsIgnoringWhitespaces("too few arguments");
			softly.assertThat(result.errorOutput).containsIgnoringWhitespaces("REPORT");
		});
	}

	@Test
	public void vulnerabilityReportUploadWithNonExistentFileIsRejected() {
		ProcessUtils.ProcessResult result = runUploader(
				new VulnerabilityReportUploadArguments().withUrl("http://localhost:9999")
						.withPattern("src/test/resources/vulnerability_report/does-not-exist.json"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("could not be resolved to any files");
		});
	}

	@Test
	public void vulnerabilityReportUploadWithDirectoryInsteadOfFileIsRejected() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(
					new VulnerabilityReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
							.withPattern("src/test/resources/vulnerability_report"));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput).contains("could not be resolved to any files");
				softly.assertThat(result.errorOutput).doesNotContain("Could not find the specified report file");
				softly.assertThat(server.vulnerabilityReportUploads).isEmpty();
			});
		}
	}

	@Test
	public void vulnerabilityReportUploadRejectsPatternMatchingSeveralFiles(@TempDir Path reportDirectory)
			throws IOException {
		Path report = Paths.get(VulnerabilityReportUploadArguments.DEFAULT_REPORT_PATH);
		Files.copy(report, reportDirectory.resolve("foo.json"));
		Files.copy(report, reportDirectory.resolve("bar.json"));

		String pattern = reportDirectory.toString().replace('\\', '/') + "/**/*.json";
		ProcessUtils.ProcessResult result = runUploader(
				new VulnerabilityReportUploadArguments().withUrl("http://localhost:9999").withPattern(pattern));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("matches more than one file")
					.contains("overwrite each other");
			// the listed paths come from File::getPath, so they carry the separator of the
			// platform the test runs on
			softly.assertThat(result.errorOutput).contains(File.separator + "foo.json")
					.contains(File.separator + "bar.json");
		});
	}

	@Test
	public void vulnerabilityReportUploadRetriesAfterIntermittentFailure() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, false, 0L, 2)) {
			ProcessUtils.ProcessResult result = runUploader(new VulnerabilityReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withMaxAttempts(3));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isZero();
				softly.assertThat(result.getOutputAndErrorOutput()).contains("Failed attempt 1 / 3")
						.contains("Failed attempt 2 / 3");
				softly.assertThat(server.vulnerabilityReportUploads).hasSize(1);
			});
		}
	}

	@Test
	public void vulnerabilityReportUploadReportsFailuresAfterAllAttempts() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, false, 0L, 3)) {
			ProcessUtils.ProcessResult result = runUploader(new VulnerabilityReportUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withMaxAttempts(3));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.getOutputAndErrorOutput()).contains("Failed attempt 1 / 3")
						.contains("Failed attempt 2 / 3").doesNotContain("Failed attempt 3 / 3");
				softly.assertThat(result.errorOutput).contains("Upload failed after 3 attempt(s)");
				softly.assertThat(server.vulnerabilityReportUploads).isEmpty();
			});
		}
	}

	@Test
	public void rejectedVulnerabilityReportUploadShowsItsExplanation() {
		try (TeamscaleMockServer ignored = TeamscaleMockServer.respondingWith(MOCK_TEAMSCALE_PORT, 400,
				"The 'build-name' must not contain '#'.")) {
			ProcessUtils.ProcessResult result = runUploader(
					new VulnerabilityReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput).contains("Teamscale rejected the upload request as invalid.");
				softly.assertThat(result.errorOutput).contains("The 'build-name' must not contain '#'.");
			});
		}
	}

	@Test
	public void vulnerabilityReportUploadToUnknownProjectShowsDetailedHints() {
		try (TeamscaleMockServer ignored = TeamscaleMockServer.respondingWith(MOCK_TEAMSCALE_PORT, 404, "Not found")) {
			ProcessUtils.ProcessResult result = runUploader(
					new VulnerabilityReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput).contains("does not seem to exist in Teamscale");
				// a 404 may also mean the endpoint is missing, which only the vulnerability
				// report command says
				softly.assertThat(result.errorOutput)
						.contains("may be too old to support vulnerability report uploads");
			});
		}
	}

	/**
	 * The hint that Teamscale may not support vulnerability report uploads yet
	 * would be misleading for the report upload, whose endpoint has existed for a
	 * long time.
	 */
	@Test
	public void reportUploadToUnknownProjectDoesNotHintAtVulnerabilityReportSupport() {
		try (TeamscaleMockServer ignored = TeamscaleMockServer.respondingWith(MOCK_TEAMSCALE_PORT, 404, "Not found")) {
			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).isNotZero();
				softly.assertThat(result.errorOutput).contains("does not seem to exist in Teamscale");
				softly.assertThat(result.errorOutput).doesNotContain("vulnerability report");
			});
		}
	}

	@Test
	public void vulnerabilityReportHelpIsAvailable() {
		ProcessUtils.ProcessResult result = runUploader(new VulnerabilityReportUploadArguments().withHelp());
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
					.isZero();
			softly.assertThat(result.getOutputAndErrorOutput()).containsIgnoringWhitespaces("--build-name")
					.containsIgnoringWhitespaces("--build-version");
		});
	}

	/**
	 * The report command keeps taking any number of report files as positional
	 * arguments, which is what stops the tool from telling the commands apart by
	 * position alone.
	 */
	@Test
	public void reportCommandAcceptsSeveralReportFiles() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(
					new ReportUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withPatterns(
							"src/test/resources/coverage_files/coverage.simple",
							"src/test/resources/coverage_files/coverage2.simple"));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isZero();
				softly.assertThat(server.uploadedReportsByName).containsOnlyKeys("coverage.simple", "coverage2.simple");
			});
		}
	}

	@Test
	public void mainHelpListsAllCommands() {
		ProcessUtils.ProcessResult result = runUploader(new NoCommandArguments("--help"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
					.isZero();
			softly.assertThat(result.getOutputAndErrorOutput())
					.containsIgnoringWhitespaces(ReportCommandLineOptions.COMMAND_NAME)
					.containsIgnoringWhitespaces(VulnerabilityReportCommandLineOptions.COMMAND_NAME);
		});
	}

	@Test
	public void reportHelpIsAvailable() {
		ProcessUtils.ProcessResult result = runUploader(new ReportUploadArguments().withHelp());
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
					.isZero();
			softly.assertThat(result.getOutputAndErrorOutput()).containsIgnoringWhitespaces("--partition")
					.containsIgnoringWhitespaces("INPUTFILE");
		});
	}

	/**
	 * Uploading reports used to need no command at all, so an invocation from a
	 * pipeline that was not migrated yet must say what changed rather than just
	 * name the first option as unrecognized.
	 */
	@Test
	public void reportUploadWithoutTheCommandExplainsTheChange() {
		ProcessUtils.ProcessResult result = runUploader(new NoCommandArguments("--server", "http://localhost:9999",
				"--project", "teamscale-upload", "--user", "build", "--accesskey", "not-a-ci-build", "--format",
				"simple", "--partition", "test", "src/test/resources/coverage_files/coverage.simple"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.getOutputAndErrorOutput()).contains("You did not specify a command")
					.contains("teamscale-upload " + ReportCommandLineOptions.COMMAND_NAME + " --server");
		});
	}

	/**
	 * --version asks about the tool rather than about an upload, so it is an option
	 * of the tool itself and needs no command.
	 */
	@Test
	public void toolVersionIsAvailableWithoutACommand() {
		ProcessUtils.ProcessResult result = runUploader(new NoCommandArguments("--version"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
					.isZero();
			softly.assertThat(result.getOutputAndErrorOutput()).contains("Teamscale Upload");
		});
	}

	private void assertThatOSCertificatesWereImported(ProcessUtils.ProcessResult result) {
		assertSoftlyThat(softly -> {
			softly.assertThat(result.errorOutput)
					.doesNotContain("Could not import certificates from the operating system");
			softly.assertThat(result.output)
					.containsPattern(Pattern.compile("Imported \\d+ certificates from the operating system\\."));
		});
	}

	private byte[] readResource(String name) throws IOException {
		try (InputStream stream = IntegrationTestBase.class.getResourceAsStream(name)) {
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
		return session.message().replaceAll("uploaded from .*", "uploaded from HOSTNAME").replaceAll("uploaded at .*",
				"uploaded at DATE");
	}
}
