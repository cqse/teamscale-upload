package com.teamscale.upload;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.FileSystemUtils;
import com.teamscale.upload.utils.SecretUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;

/**
 Integration Tests. Runs the Maven-generated native image in different scenarios.
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
 */
@EnabledIfEnvironmentVariable(named = SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE, matches = ".*")
public class NativeImageIT extends IntegrationTestBase {

	/**
	 * Path of the graal-vm built executable.
	 */
	public static final String TEAMSCALE_UPLOAD_EXECUTABLE = "./target/teamscale-upload";

	/**
	 * All of these tests try to call the Teamscale-upload executable.
	 * If that does not exist (build failed), running the test makes no sense.
	 * <p>
	 * Currently dead code. Ideally, this would have an @BeforeAll annotation, but that somehow did not work with Maven.
	 */
	static void assertThatExecutableExists() {
		File expectedExecutable = new File(TEAMSCALE_UPLOAD_EXECUTABLE);
		Assertions.assertThat(expectedExecutable).exists();
		//Assertions.assertThat(expectedExecutable.canExecute()).isTrue();
	}

	@Override
	protected ProcessUtils.ProcessResult runUploader(Arguments arguments) {
		assertThatExecutableExists();
		return ProcessUtils.runWithStdIn(arguments.stdinFile, arguments.toCommand(TEAMSCALE_UPLOAD_EXECUTABLE));
	}
}
