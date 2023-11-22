package com.teamscale.upload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.teamscale.upload.utils.FileSystemUtils;
import com.teamscale.upload.utils.ZipFile;
import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.api.SoftAssertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.SecretUtils;

import static com.teamscale.upload.utils.FileSystemUtils.TEMP_DIR_PATH;
import static com.teamscale.upload.utils.FileSystemUtils.deleteRecursively;

/**
 Integration Tests. Runs the JLink-generated native distribution in different scenarios.
 * <p>
 * Before you can run the test, you will need to generate the distribution.
 * <p>
 * You will also need to specify the access key for username
 * "teamscale-upload-build-test-user" on <a href="https://cqse.teamscale.io/">cqse.teamscale.io</a>. The user
 * has report-upload permission for project "teamscale-upload" and is used for
 * testing in the GitHub Project <a href="https://github.com/cqse/teamscale-upload">teamscale upload</a>. The
 * access token is stored as a "Secret" in GitHub. For local testing you will
 * need to set the environment variable
 * {@link SecretUtils#TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE}. It is stored
 * in 1password as "teamscale-upload-build-test-user".
 */
@EnabledIfEnvironmentVariable(named = SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE, matches = ".*")
public class JLinkIntegrationTest extends IntegrationTestBase {

	private static File executable;
	@BeforeAll
	public static void extractDistribution() throws IOException {
		Path tempDir = Path.of(TEMP_DIR_PATH, "JLinkTest");
		// cleanup before tests
		deleteRecursively(tempDir.toFile());
		String distribution;
		String executableInZip;
		if (SystemUtils.IS_OS_WINDOWS) {
			distribution = "build/distributions/teamscale-upload-windows-x86_64.zip";
			executableInZip = "bin/teamscale-upload.bat";
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			// Can't distinguish between Mac (Intel) and Mac (ARM) here, sorry.
			// Assuming ARM
			distribution = "build/distributions/teamscale-upload-macos-aarch64.zip";
			executableInZip = "bin/teamscale-upload";
		} else {
			distribution = "build/distributions/teamscale-upload-linux-x86_64.zip";
			executableInZip = "bin/teamscale-upload";
		}
		executable = new File(tempDir + "/teamscale-upload/" + executableInZip);

		if (!executable.exists()) {
			try (ZipFile zipFile = new ZipFile(new File(distribution))) {
				FileSystemUtils.unzip(zipFile, tempDir.toFile());
			}
		}
		validateExecutable();
	}

	/**
	 * Checks whether the executable exists and can be executed (i.e., whether the extraction step worked).
	 */
	private static void validateExecutable() {
		if (!executable.exists()) {
			Assertions.fail("Could not find executable after extracting distribution. " + executable.getPath());
		}
		if (!executable.canExecute()) {
			Assertions.fail("teamscale-upload script exists, but is not executable after extracting distribution. " + executable.getPath());
		}
	}

	@Override
	protected ProcessUtils.ProcessResult runUploader(Arguments arguments) {
		validateExecutable();
		return ProcessUtils.runWithStdIn(arguments.stdinFile, arguments.toCommand(executable.getPath()));
	}

}
