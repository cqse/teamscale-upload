package com.teamscale.upload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Arguments for an execution of the teamscale-upload executable's
 * {@link SbomCommandLineOptions#COMMAND_NAME} command.
 */
class SbomUploadArguments extends CommonUploadArguments<SbomUploadArguments> {

	/**
	 * The SBOM file that is uploaded unless {@link #withPattern(String)} overrides
	 * it. Tests use this to determine the expected file name and content.
	 */
	static final String DEFAULT_SBOM_PATH = "src/test/resources/sbom/bom.json";

	private String buildName = "teamscale-upload-it";
	private String buildVersion = "1.0.0";
	private String commit = "master:HEAD";
	private String pattern = DEFAULT_SBOM_PATH;
	private boolean autoDetectCommit = false;
	private boolean help = false;

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

	/**
	 * Requests the help screen. All other options are omitted, as argparse4j prints
	 * the help screen before it complains about missing required options.
	 */
	SbomUploadArguments withHelp() {
		this.help = true;
		return this;
	}

	/**
	 * Assembles the command that invokes the given teamscale-upload executable.
	 */
	@Override
	public String[] toCommand(String executable) {
		if (help) {
			return new String[] { executable, SbomCommandLineOptions.COMMAND_NAME, "--help" };
		}

		List<String> command = new ArrayList<>(Arrays.asList(executable, SbomCommandLineOptions.COMMAND_NAME));
		addCommonOptions(command);

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
		if (pattern != null) {
			command.add(pattern);
		}
		return command.toArray(new String[0]);
	}
}
