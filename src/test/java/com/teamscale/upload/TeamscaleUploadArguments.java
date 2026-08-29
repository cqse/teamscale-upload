package com.teamscale.upload;

import java.util.ArrayList;
import java.util.List;

/**
 * Arguments for an execution of the teamscale-upload executable's default
 * command, which uploads external analysis reports.
 */
class TeamscaleUploadArguments extends CommonUploadArguments<TeamscaleUploadArguments> {

	private String partition = "NativeImageIT";
	private String format = "simple";
	private String pattern = "src/test/resources/coverage_files\\*.simple";
	private String input = null;
	private boolean autoDetectCommit = false;
	private String timestamp = "master:HEAD";
	private String commit = null;
	private String repository = null;
	private String pathPrefix = null;
	private String additionalMessageLine = null;
	private boolean help = false;

	/**
	 * Requests the help screen. All other options are omitted, as argparse4j prints
	 * the help screen before it complains about missing required options.
	 */
	TeamscaleUploadArguments withHelp() {
		this.help = true;
		return this;
	}

	/**
	 * Sets the report format.
	 */
	TeamscaleUploadArguments withFormat(String format) {
		this.format = format;
		return this;
	}

	/**
	 * Sets the report-file path pattern. This sets the {@link ReportCommandLineOptions#files}
	 * option (i.e., "pattern" == "files").
	 */
	TeamscaleUploadArguments withPattern(String pattern) {
		this.pattern = pattern;
		return this;
	}

	/**
	 * Sets the commit (hash) to which we upload the reports.
	 */
	TeamscaleUploadArguments withCommit(String commit) {
		this.commit = commit;
		return this;
	}

	/**
	 * Sets the commit to which we upload the reports e.g. "master:HEAD".
	 */
	TeamscaleUploadArguments withTimestamp(String timestamp) {
		this.timestamp = timestamp;
		return this;
	}

	/**
	 * Sets the target-repository name (name of repo connector in a Teamscale project).
	 */
	TeamscaleUploadArguments withRepository(String repository) {
		this.repository = repository;
		return this;
	}

	/**
	 * Sets the path prefix prepended to all paths in the uploaded reports.
	 */
	TeamscaleUploadArguments withPathPrefix(String pathPrefix) {
		this.pathPrefix = pathPrefix;
		return this;
	}

	/**
	 * Sets whether we should auto-detect the current commit and use it as a target
	 * commit.
	 */
	TeamscaleUploadArguments withAutoDetectCommit() {
		this.autoDetectCommit = true;
		return this;
	}

	/**
	 * Uses the given line for the "--append-to-message" parameter.
	 */
	TeamscaleUploadArguments withAdditionalMessageLine(String line) {
		this.additionalMessageLine = line;
		return this;
	}

	/**
	 * Configures the input (path to a file which contains additional report file
	 * patterns).
	 */
	TeamscaleUploadArguments withInput(String input) {
		this.input = input;
		return this;
	}

	/**
	 * Sets the partition into which the data is inserted in Teamscale.
	 */
	TeamscaleUploadArguments withPartition(String partition) {
		this.partition = partition;
		return this;
	}

	/**
	 * Assembles the command that invokes the given teamscale-upload executable.
	 */
	@Override
	public String[] toCommand(String executable) {
		if (help) {
			return new String[] { executable, "--help" };
		}

		List<String> command = new ArrayList<>(List.of(executable));
		addCommonOptions(command);

		command.add("--format");
		command.add(format);
		command.add("--partition");
		command.add(partition);
		if (input != null) {
			command.add("--input");
			command.add(input);
		}
		// "files" is a positional argument. ("pattern" == "files")
		command.add(pattern);
		if (additionalMessageLine != null) {
			command.add("--append-to-message");
			command.add(additionalMessageLine);
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
		if (pathPrefix != null) {
			command.add("--path-prefix");
			command.add(pathPrefix);
		}
		return command.toArray(new String[0]);
	}
}
