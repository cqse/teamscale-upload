package com.teamscale.upload;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.teamscale.upload.utils.SecretUtils;

/**
 * Arguments for an execution of the teamscale-upload executable's
 * {@link SbomCommandLine#COMMAND_NAME} command.
 */
class SbomUploadArguments implements UploadArguments {

	private static final String TEAMSCALE_TEST_USER = "teamscale-upload-build-test-user";

	/**
	 * The SBOM file that is uploaded unless {@link #withPattern(String)} overrides
	 * it. Tests use this to determine the expected file name and content.
	 */
	static final String DEFAULT_SBOM_PATH = "src/test/resources/sbom/bom.json";

	private String url = "https://cqse.teamscale.io/";
	private String user = TEAMSCALE_TEST_USER;
	private String accessKey = getAccessKeyFromCi();
	private String project = "teamscale-upload";
	private String buildName = "teamscale-upload-it";
	private String buildVersion = "1.0.0";
	private String commit = "master:HEAD";
	private String pattern = DEFAULT_SBOM_PATH;
	private boolean autoDetectCommit = false;
	private boolean debug = false;
	private boolean help = false;
	private Integer maxAttempts = null;

	/** Configures the Teamscale server url. */
	SbomUploadArguments withUrl(String url) {
		this.url = url;
		return this;
	}

	/** Configures the Teamscale project. */
	SbomUploadArguments withProject(String project) {
		this.project = project;
		return this;
	}

	/** Sets the --build-name option. */
	SbomUploadArguments withBuildName(String buildName) {
		this.buildName = buildName;
		return this;
	}

	/** Omits the --build-name option. */
	SbomUploadArguments withoutBuildName() {
		this.buildName = null;
		return this;
	}

	/** Sets the --build-version option. */
	SbomUploadArguments withBuildVersion(String buildVersion) {
		this.buildVersion = buildVersion;
		return this;
	}

	/** Omits the --build-version option. */
	SbomUploadArguments withoutBuildVersion() {
		this.buildVersion = null;
		return this;
	}

	/** Sets the --commit option. */
	SbomUploadArguments withCommit(String commit) {
		this.commit = commit;
		return this;
	}

	/**
	 * Omits the --commit option so that the commit must be detected automatically.
	 */
	SbomUploadArguments withAutoDetectCommit() {
		this.autoDetectCommit = true;
		return this;
	}

	/** Sets the path or pattern of the SBOM file to upload. */
	SbomUploadArguments withPattern(String pattern) {
		this.pattern = pattern;
		return this;
	}

	/** Omits the SBOM file argument. */
	SbomUploadArguments withoutPattern() {
		this.pattern = null;
		return this;
	}

	/** Enables debug logging. */
	SbomUploadArguments withDebug() {
		this.debug = true;
		return this;
	}

	/** Sets the maximum number of attempts for transient errors. */
	SbomUploadArguments withMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
		return this;
	}

	/**
	 * Requests the help screen. All other options are omitted, as argparse4j prints
	 * the help screen before it complains about missing required options.
	 */
	SbomUploadArguments withHelp() {
		this.help = true;
		return this;
	}

	@Override
	public File getStdinFile() {
		return null;
	}

	@Override
	public String[] toCommand(String executable) {
		if (help) {
			return new String[] { executable, SbomCommandLine.COMMAND_NAME, "--help" };
		}

		List<String> command = new ArrayList<>(
				Arrays.asList(executable, SbomCommandLine.COMMAND_NAME, "--server", url, "--user", user, "--project",
						project));
		if (accessKey != null) {
			command.add("--accesskey");
			command.add(accessKey);
		}
		if (buildName != null) {
			command.add("--build-name");
			command.add(buildName);
		}
		if (buildVersion != null) {
			command.add("--build-version");
			command.add(buildVersion);
		}
		if (commit != null && !autoDetectCommit) {
			command.add("--commit");
			command.add(commit);
		}
		if (maxAttempts != null) {
			command.add("--max-attempts");
			command.add(String.valueOf(maxAttempts));
		}
		if (debug) {
			command.add("--debug");
		}
		if (pattern != null) {
			command.add(pattern);
		}
		return command.toArray(new String[0]);
	}

	private static String getAccessKeyFromCi() {
		String accessKey = System.getenv(SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE);
		if (accessKey == null) {
			return "not-a-ci-build";
		}
		return accessKey;
	}
}
