package com.teamscale.upload;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Parses and validates the command line arguments of the
 * {@value #COMMAND_NAME} command, which uploads a Software Bill of Materials.
 */
public class SbomCommandLineOptions extends CommonCommandLineOptions {

	/**
	 * The name under which this command is invoked, i.e.
	 * <code>teamscale-upload sbom ...</code>.
	 */
	public static final String COMMAND_NAME = "sbom";

	/**
	 * Teamscale uses this character internally to separate the build name from the
	 * version, so neither of them may contain it.
	 */
	private static final String RESERVED_CHARACTER = "#";

	/**
	 * The name identifying the build the SBOM belongs to.
	 */
	public final String buildName;
	/**
	 * The version (build number) identifying this upload within the build.
	 */
	public final String buildVersion;
	/**
	 * The path or pattern of the SBOM file to upload, as given on the command line.
	 * Teamscale stores one SBOM per build name and version, so there is exactly one.
	 */
	public final String filePathOrPattern;

	private SbomCommandLineOptions(Namespace namespace) {
		super(namespace);
		this.buildName = namespace.getString("build_name");
		this.buildVersion = namespace.getString("build_version");
		this.filePathOrPattern = namespace.getString("file");
	}

	/**
	 * Parses the given command line arguments and validates them. The arguments
	 * must not include the {@value #COMMAND_NAME} command itself.
	 */
	public static SbomCommandLineOptions parseArguments(String[] args) {
		ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload " + COMMAND_NAME).build().defaultHelp(true)
				.description("Upload a Software Bill of Materials (SBOM) to Teamscale.");

		addCommonArguments(parser);

		parser.addArgument("--build-name").metavar("BUILD_NAME").required(true)
				.help("The name identifying the build this SBOM belongs to. E.g. the name of the"
						+ " service or component that was built. Must not contain '" + RESERVED_CHARACTER + "'.");
		parser.addArgument("--build-version").metavar("BUILD_VERSION").required(true)
				.help("The version (e.g. the build number) identifying this upload within the build."
						+ " Uploading again with the same --build-name and --build-version overwrites"
						+ " the previously uploaded SBOM. Must not contain '" + RESERVED_CHARACTER + "'.");
		addCommitArgument(parser, "The version control commit the build was produced from. This links the SBOM to"
				+ " a commit in Teamscale. Can be either a Git SHA1, a SVN revision number or a"
				+ " Team Foundation changeset ID. If omitted, teamscale-upload tries to detect"
				+ " the commit automatically.");
		parser.addArgument("file").metavar("SBOM")
				.help("Path or pattern of the SBOM file to upload. Teamscale stores one SBOM per"
						+ " --build-name and --build-version, so this must resolve to a single file."
						+ " Supported formats are CycloneDX (JSON or XML) and SPDX 2.x (JSON)."
						+ " The format is detected automatically.");
		parser.epilog("For general usage help and alternative upload methods, please check our online"
				+ " documentation at:" + "\nhttp://cqse.eu/tsu-docs" + "\n\nEXAMPLE"
				+ "\n\nteamscale-upload " + COMMAND_NAME + " --server https://teamscale.example.com"
				+ " --project my-project --user build --build-name my-service --build-version 1.4.2"
				+ " bom.json" + "\n\nTARGET COMMIT"
				+ "\n\nIf you do not specify --commit, teamscale-upload tries to automatically detect"
				+ " the code commit from environment variables or a Git or SVN checkout in the current"
				+ " working directory. This feature supports many common CI tools like Jenkins, GitLab,"
				+ " GitHub Actions, Travis CI etc. If automatic detection fails, the upload will fail"
				+ " and you must specify the commit manually via --commit.");

		return parseAndValidate(parser, args, SbomCommandLineOptions::new);
	}

	@Override
	protected void validate(ArgumentParser parser) throws ArgumentParserException {
		validateCommonOptions(parser);

		validateIdentifier(parser, buildName, "--build-name");
		validateIdentifier(parser, buildVersion, "--build-version");
	}

	private static void validateIdentifier(ArgumentParser parser, String value, String optionName)
			throws ArgumentParserException {
		if (value.contains(RESERVED_CHARACTER)) {
			throw new ArgumentParserException("The value you provided for " + optionName + " contains '"
					+ RESERVED_CHARACTER + "': " + value + "\nTeamscale uses this character internally to separate the"
					+ " build name from the version, so it must not appear in either of them."
					+ " Please choose a value without '" + RESERVED_CHARACTER + "'.", parser);
		}
	}

	@Override
	public String getAdditionalHttp404Hint() {
		return "If the project ID and the URL are correct, your Teamscale server may be too old to"
				+ " support SBOM uploads. Please check with your Teamscale administrator whether your"
				+ " Teamscale version already offers this feature.";
	}
}
