package com.teamscale.upload;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

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
	protected abstract ProcessUtils.ProcessResult runUploader(TeamscaleUploadArguments arguments);

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void wrongAccessKey() {
		ProcessUtils.ProcessResult result = runUploader(
				new TeamscaleUploadArguments().withAccessKey("wrong-accesskey_"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("You provided incorrect credentials");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void incorrectUrl() {
		ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments().withUrl("no-protocol:9999"));
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
				new TeamscaleUploadArguments().withUrl("http://domain.invalid:9999"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput)
					.contains("The host http://domain.invalid:9999/ could not be resolved");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void unreachableUrl() {
		ProcessUtils.ProcessResult result = runUploader(
				new TeamscaleUploadArguments().withUrl("http://localhost:9999"));
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
					new TeamscaleUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
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
					new TeamscaleUploadArguments().withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT)
							.withProxy("localhost:" + ProxyMockServer.PORT).withDebug());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(result.getOutputAndErrorOutput()).contains("Proxy Connection successful");
		}
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void wrongUser() {
		ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments().withUser("wrong-user_"));
		assertSoftlyThat(softly -> {
			softly.assertThat(result.exitCode).isNotZero();
			softly.assertThat(result.errorOutput).contains("You provided incorrect credentials");
		});
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void wrongProject() {
		ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments().withProject("wrong-project_"));
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
				new TeamscaleUploadArguments().withUrl("http://localhost:9999").withTimeoutInSeconds("0"));
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
				new TeamscaleUploadArguments().withUrl("http://localhost:9999").withTimeoutInSeconds("foo"));
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
		ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments().withCommit("doesnt-exist"));
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
		ProcessUtils.ProcessResult result = runUploader(
				new TeamscaleUploadArguments().withPattern("**/matches.nothing"));
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
				new TeamscaleUploadArguments().withUser("teamscale-upload-build-test-user-no-permissions")
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
		ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments());
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void successfulMultiFormatUpload() {
		ProcessUtils.ProcessResult result = runUploader(
				new TeamscaleUploadArguments().withInput("src/test/resources/coverage_files/input_file"));
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	public void testDefaultMessage() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
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
			ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
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
			ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withTimeoutInSeconds("3"));
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	@Disabled("TS-41072 Test should not run against production server")
	public void mustRejectTimestampPassedInSeconds() {
		ProcessUtils.ProcessResult result = runUploader(
				new TeamscaleUploadArguments().withTimestamp("master:1606764633"));
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
					new TeamscaleUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withInsecure());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
		}
	}

	@Test
	public void selfSignedCertificateShouldNotBeAcceptedByDefault() {
		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(
					new TeamscaleUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT));
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
					new TeamscaleUploadArguments().withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT));
			assertSoftlyThat(softly -> {
				softly.assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput())
						.isNotZero();
				softly.assertThat(result.errorOutput).contains("--stacktrace");
			});
			assertThatOSCertificatesWereImported(result);
		}

		try (TeamscaleMockServer ignored = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT, true)) {
			ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
					.withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withStackTrace());
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
			ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
					.withUrl("https://localhost:" + MOCK_TEAMSCALE_PORT).withKeystore().withStackTrace());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThatOSCertificatesWereImported(result);
		}
	}

	@Test
	public void mustGuessRevision() {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
					.withUrl("http://localhost:" + MOCK_TEAMSCALE_PORT).withAutoDetectCommit());
			assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
			assertThat(server.sessions).hasSize(1);
			assertThat(server.sessions.get(0).revisionOrTimestamp).hasSize(40); // size of a git SHA1
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
					new TeamscaleUploadArguments().withAccessKeyViaStdin(temporaryFileName));
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
				new TeamscaleUploadArguments().withAccessKeyViaStdin("src/test/resources/incorrect_access_key.txt"));
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
		ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments().withoutAccessKeyInOption());
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.getOutputAndErrorOutput()).isZero();
		assertThatOSCertificatesWereImported(result);
	}

	@Test
	@EnabledOnOs(OS.MAC)
	public void testXCResultConversion() throws IOException {
		try (TeamscaleMockServer server = new TeamscaleMockServer(MOCK_TEAMSCALE_PORT)) {
			ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
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
				new TeamscaleUploadArguments().withPattern("foo.simple bar.simple"));
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
		ProcessUtils.ProcessResult result = runUploader(new TeamscaleUploadArguments()
				.withRepository("cqse/teamscale-upload").withPartition("NativeImageIT > TestRepository")
				.withCommit("3758a3a6c2d62ab787574f869b2352480c6f0c10"));
		assertThat(result.exitCode).describedAs("Stderr and stdout: " + result.errorOutput).isZero();
		assertThatOSCertificatesWereImported(result);
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
		return session.message.replaceAll("uploaded from .*", "uploaded from HOSTNAME").replaceAll("uploaded at .*",
				"uploaded at DATE");
	}
}
