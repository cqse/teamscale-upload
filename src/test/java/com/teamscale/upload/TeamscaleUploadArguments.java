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

	TeamscaleUploadArguments withFormat(String format) {
		this.format = format;
		return this;
	}

	TeamscaleUploadArguments withPattern(String pattern) {
		this.pattern = pattern;
		return this;
	}

	TeamscaleUploadArguments withInsecure() {
		this.insecure = true;
		return this;
	}

	TeamscaleUploadArguments withCommit(String commit) {
		this.commit = commit;
		return this;
	}

	TeamscaleUploadArguments withTimestamp(String timestamp) {
		this.timestamp = timestamp;
		return this;
	}

	TeamscaleUploadArguments withRepository(String repository) {
		this.repository = repository;
		return this;
	}

	TeamscaleUploadArguments withAutoDetectCommit() {
		this.autoDetectCommit = true;
		return this;
	}

	TeamscaleUploadArguments withKeystore() {
		this.useKeystore = true;
		return this;
	}

	TeamscaleUploadArguments withAdditionalMessageLine(String line) {
		this.additionalMessageLine = line;
		return this;
	}

	TeamscaleUploadArguments withUrl(String url) {
		this.url = url;
		return this;
	}

	TeamscaleUploadArguments withStackTrace() {
		this.stackTrace = true;
		return this;
	}

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

	TeamscaleUploadArguments withAccessKeyViaStdin(String stdinFilePath) {
		this.accessKey = "-";
		// If the access key is set to '-', we need to pipe the key from a file via
		// stdin.
		this.stdinFile = new File(stdinFilePath);
		return this;
	}

	TeamscaleUploadArguments withUser(String user) {
		this.user = user;
		return this;
	}

	TeamscaleUploadArguments withProject(String project) {
		this.project = project;
		return this;
	}

	TeamscaleUploadArguments withInput(String input) {
		this.input = input;
		return this;
	}

	TeamscaleUploadArguments withPartition(String partition) {
		this.partition = partition;
		return this;
	}

	TeamscaleUploadArguments withMoveToLastCommit() {
		this.moveToLastCommit = true;
		return this;
	}

	TeamscaleUploadArguments withTimeoutInSeconds(String timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
		return this;
	}

	/**
	 * Assembles the command that invokes the given teamscale-upload executable.
	 */
	String[] toCommand(String executable) {
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

	private static String getAccessKeyFromCi() {
		String accessKey = System.getenv(SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE);
		if (accessKey == null) {
			return "not-a-ci-build";
		}
		return accessKey;
	}
}
