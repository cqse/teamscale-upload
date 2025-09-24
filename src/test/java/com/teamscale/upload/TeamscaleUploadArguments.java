package com.teamscale.upload;

import com.teamscale.upload.test_utils.TeamscaleMockServer;
import com.teamscale.upload.utils.SecretUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Arguments for an execution of the teamscale-upload executable.
 */
class TeamscaleUploadArguments {
	private static final String TEAMSCALE_TEST_USER = "teamscale-upload-build-test-user";
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

	/**
	 * The file from which the teamscale-upload executable should draw its stdin.
	 */
	File stdinFile = null;
	private boolean moveToLastCommit = false;
	private String timeoutInSeconds = null;

	/**
	 * Sets the report format
	 */
	TeamscaleUploadArguments withFormat(String format) {
		this.format = format;
		return this;
	}
	/**
	 * Sets the report-file path pattern. This sets the {@link CommandLine#files} option (i.e., "pattern" == "files").
	 */
	TeamscaleUploadArguments withPattern(String pattern) {
		this.pattern = pattern;
		return this;
	}

	/**
	 * Sets whether to use insecure certificate checking (i.e., skip checking entirely)
	 */
	TeamscaleUploadArguments withInsecure() {
		this.insecure = true;
		return this;
	}

	/**
	 * sets the commit (hash) to which we upload the reports
	 */
	TeamscaleUploadArguments withCommit(String commit) {
		this.commit = commit;
		return this;
	}
	/**
	 * sets the commit to which we upload the reports (e.g. "master:HEAD")
	 */
	TeamscaleUploadArguments withTimestamp(String timestamp) {
		this.timestamp = timestamp;
		return this;
	}

	/**
	 * Sets the target-repository name (name of repo connector in Teamscale project)
	 */
	TeamscaleUploadArguments withRepository(String repository) {
		this.repository = repository;
		return this;
	}

	/**
	 * Sets whether we should auto-detect the current commit and use it as target commit.
	 */
	TeamscaleUploadArguments withAutoDetectCommit() {
		this.autoDetectCommit = true;
		return this;
	}

	/**
	 * Sets whether to use the {@link TeamscaleMockServer#TRUSTSTORE} as parameter for --trusted-keystore.
	 */
	TeamscaleUploadArguments withKeystore() {
		this.useKeystore = true;
		return this;
	}

	/**
	 * uses the given line for the "--append-to-message" parameter
	 */
	TeamscaleUploadArguments withAdditionalMessageLine(String line) {
		this.additionalMessageLine = line;
		return this;
	}

	/**
	 * Configures the given url as Teamscale server
	 */
	TeamscaleUploadArguments withUrl(String url) {
		this.url = url;
		return this;
	}

	/**
	 * sets whether to use the --stacktrace option
	 */
	TeamscaleUploadArguments withStackTrace() {
		this.stackTrace = true;
		return this;
	}

	/**
	 * Confiures the access key for the Teamscale server
	 */
	TeamscaleUploadArguments withAccessKey(String accessKey) {
		this.accessKey = accessKey;
		return this;
	}

	/**
	 * No access key is specified as option. The key which is specified in the
	 * environment variable should be used instead.
	 */
	TeamscaleUploadArguments withoutAccessKeyInOption() {
		this.accessKey = null;
		return this;
	}

	/**
	 * Configures that the access key is read from the given file
	 */
	TeamscaleUploadArguments withAccessKeyViaStdin(String stdinFilePath) {
		this.accessKey = "-";
		// If the access key is set to '-', we need to pipe the key from a file via
		// stdin.
		this.stdinFile = new File(stdinFilePath);
		return this;
	}

	/**
	 * Configures the Teamscale user
	 */
	TeamscaleUploadArguments withUser(String user) {
		this.user = user;
		return this;
	}

	/**
	 * Configures the Teamscale project
	 */
	TeamscaleUploadArguments withProject(String project) {
		this.project = project;
		return this;
	}
	/**
	 * Configures the input (path to a file which contains additional report file patterns)
	 */
	TeamscaleUploadArguments withInput(String input) {
		this.input = input;
		return this;
	}

	/**
	 * sets the partition into which the data is inserted in Teamscale
	 */
	TeamscaleUploadArguments withPartition(String partition) {
		this.partition = partition;
		return this;
	}

	/**
	 * Sets whether we use the "--move-to-last-commit" option
	 */
	TeamscaleUploadArguments withMoveToLastCommit() {
		this.moveToLastCommit = true;
		return this;
	}

	/**
	 * Sets the timeout for the Teamscale-service call
	 */
	TeamscaleUploadArguments withTimeoutInSeconds(String timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
		return this;
	}

	/**
	 * Assembles the command that invokes the given teamscale-upload executable.
	 */
	String[] toCommand(String executable) {
		List<String> command = new ArrayList<>(
				Arrays.asList(executable, "--server", url, "--user", user, "--format", format, "--project", project, "--partition", partition));
		if (accessKey != null) {
			command.add("--accesskey");
			command.add(accessKey);
		}
		if (input != null) {
			command.add("--input");
			command.add(input);
		}
		// "files" is a positional argument. ("pattern" == "files")
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

	private static String getAccessKeyFromCi() {
		String accessKey = System.getenv(SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE);
		if (accessKey == null) {
			return "not-a-ci-build";
		}
		return accessKey;
	}
}
