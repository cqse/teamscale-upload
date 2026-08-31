package com.teamscale.upload;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

import com.teamscale.upload.utils.MessageUtils;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Parses and validates the command line arguments of the default command, which
 * uploads external analysis reports.
 */
public class ReportCommandLineOptions extends CommonCommandLineOptions {

	/**
	 * The Teamscale partition.
	 */
	public final String partition;
	/**
	 * The uploaded data's report format.
	 */
	public final String format;
	/**
	 * The repository can be specified in combination with the commit/revision to
	 * identify the correct commit in situations where the same revision exists in
	 * multiple repositories.
	 */
	public final String repository;
	/**
	 * The branch:timestamp to which to upload. May be null.
	 */
	public final String timestamp;
	/**
	 * A path prefix for the uploaded test artifacts. For coverage reports it
	 * restricts the project files that covered paths are matched against; for test
	 * execution reports it is prepended to the test execution names. May be null.
	 */
	public final String pathPrefix;
	/**
	 * The files to upload given on the command-line directly
	 */
	public final List<String> files;
	/**
	 * The input file to use or null if none is given.
	 * <p>
	 * The file defines a mapping from report files to report-file-format. For
	 * example,
	 *
	 * <pre>
	 * [jacoco]
	 * src\test\resources\coverage_files\test*.simple
	 *
	 * [simple]
	 * src/test/resources/coverage_files/coverage.simple
	 * </pre>
	 */
	public final Path inputFile;
	/**
	 * The upload-commit message given by the user or null if none was explicitly
	 * given (a default message is created in this case
	 * {@link MessageUtils#createDefaultMessage(String, String, Collection)}).
	 */
	public final String message;
	/**
	 * Additional lines to append to the end of the message. Does not include
	 * line-terminators at the end of each entry.
	 */
	public final List<String> additionalMessageLines;

	private ReportCommandLineOptions(Namespace namespace) {
		super(namespace);
		this.partition = namespace.getString("partition");
		this.repository = namespace.getString("repository");
		this.timestamp = namespace.getString("branch_and_timestamp");
		this.pathPrefix = namespace.getString("path_prefix");
		this.files = getListSafe(namespace, "files");
		this.message = namespace.getString("message");
		this.additionalMessageLines = getListSafe(namespace, "append_to_message");

		String inputFilePath = namespace.getString("input");
		if (inputFilePath != null) {
			this.inputFile = Paths.get(inputFilePath);
		} else {
			this.inputFile = null;
		}

		String formatRaw = namespace.getString("format");
		if (formatRaw != null) {
			this.format = formatRaw.toUpperCase();
		} else {
			this.format = null;
		}

	}

	/**
	 * Parses the given command line arguments and validates them.
	 */
	public static ReportCommandLineOptions parseArguments(String[] args) {
		ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload").build().defaultHelp(true)
				.description("Upload coverage, findings, ... to Teamscale.")
				.version("Teamscale Upload " + ToolVersion.VERSION);
		parser.addArgument("--version").action(Arguments.version())
				.help("Prints the version number of this teamscale-upload tool and exits.");

		addCommonArguments(parser);

		parser.addArgument("-t", "--partition").metavar("PARTITION").required(true)
				.help("The partition into which the data is inserted in Teamscale."
						+ " Successive uploads into the same partition will overwrite the data"
						+ " previously inserted there, so use different partitions if you'd instead"
						+ " like to merge data from different sources (e.g. one for Findbugs findings"
						+ " and one for JaCoCo coverage).");
		parser.addArgument("-f", "--format").metavar("FORMAT").required(false)
				.help("The file format of the reports which are specified as command line arguments."
						+ "\nSee http://cqse.eu/upload-formats for a full list of supported file formats."
						+ "\nA report format must be supplied for each report file, either via --format or via --input.");
		addCommitArgument(parser, "The version control commit for which you obtained the report files."
				+ " E.g. if you obtained a test coverage report in your CI pipeline, then this"
				+ " is the commit the CI pipeline built before running the tests."
				+ " Can be either a Git SHA1, a SVN revision number or an Team Foundation changeset ID.");
		parser.addArgument("-r", "--repository").metavar("REPOSITORY").required(false)
				.help("When using the revision parameter, this parameter allows to pass a repository name which"
						+ " is used to identify the correct commit in situations where the same revision exists"
						+ " in multiple repositories.");
		parser.addArgument("-b", "--branch-and-timestamp").metavar("BRANCH_AND_TIMESTAMP").required(false)
				.help("The branch and Unix Epoch timestamp for which you obtained the report files."
						+ " E.g. if you obtained a test coverage report in your CI pipeline, then this"
						+ " is the branch and the commit timestamp of the commit that the CI pipeline"
						+ " built before running the tests. The timestamp must be milliseconds since"
						+ " 00:00:00 UTC Thursday, 1 January 1970 or the string 'HEAD' to upload to"
						+ " the latest revision on that branch." + "\nFormat: BRANCH:TIMESTAMP"
						+ "\nExample: master:1597845930000" + "\nExample: develop:HEAD");
		parser.addArgument("--path-prefix").metavar("PATH_PREFIX").required(false)
				.help("A path prefix for the uploaded test artifacts. For coverage reports, the covered"
						+ " paths are only matched against those files that have the specified prefix within"
						+ " the Teamscale project. This can be used if the same package structures and classes"
						+ " appear in multiple subfolders of a project. For test execution reports, the prefix"
						+ " is prepended to the test execution names, which allows to make otherwise ambiguous"
						+ " test paths unique.");
		parser.addArgument("--message").metavar("MESSAGE").required(false)
				.help("The message for the commit created in Teamscale for this upload. Will be"
						+ " visible in the Activity perspective. Defaults to a message containing"
						+ " useful meta-information about the upload and the machine performing it.");
		parser.addArgument("-i", "--input").metavar("INPUT").required(false)
				.help("A file which contains additional report file patterns. See INPUTFILE for a"
						+ " detailed description of the file format."
						+ "\nA report format must be supplied for each report file, either via --format or via --input.");
		parser.addArgument("--append-to-message").metavar("LINE").action(Arguments.append()).required(false)
				.help("Appends the given line to the message. Use this to augment the autogenerated"
						+ " message instead of replacing it. You may specify this parameter multiple"
						+ " times to append several lines to the message.");
		parser.addArgument("files").metavar("FILES").nargs("*")
				.help("Path(s) or pattern(s) of the report files to upload. Alternatively, you may"
						+ " provide input files via -i or --input");
		parser.epilog("For general usage help and alternative upload methods, please check our online"
				+ " documentation at:" + "\nhttp://cqse.eu/tsu-docs" + "\n\nTARGET COMMIT"
				+ "\n\nBy default, teamscale-upload tries to automatically detect the code commit"
				+ " to which to upload from environment variables or a Git or SVN checkout in the"
				+ " current working directory. If guessing fails, the upload will fail. This feature"
				+ " supports many common CI tools like Jenkins, GitLab, GitHub Actions, Travis CI etc."
				+ " If automatic detection fails, you can manually specify either a commit via --commit,"
				+ " a branch and timestamp via --branch-and-timestamp or you can upload to the latest"
				+ " commit on a branch via --branch-and-timestamp my-branch:HEAD." + "\n\nINPUTFILE"
				+ "\n\nThe input file allows to upload multiple report files for different formats in one"
				+ " upload session. Each section of reports must start with a specification of the"
				+ " report format. The report file patterns have the same format as used on the command"
				+ " line. The entries in the file are separated by line breaks. Blank lines are ignored."
				+ "\n\nExample:" + "\n\n[jacoco]" + "\npattern1/**.xml" + "\npattern2/**.xml" + "\n[findbugs]"
				+ "\npattern1/**.findbugs.xml" + "\npattern2/**.findbugs.xml" + "\n\nCOMMANDS"
				+ "\n\nBesides uploading external analysis reports, this tool provides the following"
				+ " additional commands:" + "\n\n" + SbomCommandLineOptions.COMMAND_NAME
				+ ": upload a Software Bill of Materials (SBOM) to Teamscale."
				+ "\nRun 'teamscale-upload " + SbomCommandLineOptions.COMMAND_NAME + " --help' for its options.");

		return parseAndValidate(parser, args, ReportCommandLineOptions::new);
	}

	/**
	 * Checks the validity of the command line arguments and throws an exception if
	 * any invalid configuration is detected.
	 */
	@Override
	protected void validate(ArgumentParser parser) throws ArgumentParserException {
		validateCommonOptions(parser);

		if (hasMoreThanOneCommitOptionSet()) {
			throw new ArgumentParserException("You used more than one of --commit and --branch-and-timestamp."
					+ " You must choose one of these options to specify the commit for which you would like to"
					+ " upload data to Teamscale", parser);
		}

		if (this.commit == null && this.repository != null) {
			throw new ArgumentParserException("You can only specify a repository if you also specify a commit.",
					parser);
		}

		if (files.isEmpty() && inputFile == null) {
			throw new ArgumentParserException("You did not provide any report files to upload."
					+ " You must either specify the paths of the report files as command line"
					+ " arguments or provide them in an input file via --input", parser);
		}

		if (!files.isEmpty() && format == null) {
			throw new ArgumentParserException("Please specify a report format with --format"
					+ " if you pass report patterns as command line arguments", parser);
		}

		validateBranchAndTimestamp(parser);
	}

	private void validateBranchAndTimestamp(ArgumentParser parser) throws ArgumentParserException {
		if (timestamp == null) {
			return;
		}

		String[] parts = timestamp.split(":", 2);
		if (parts.length == 1) {
			throw new ArgumentParserException("You specified an invalid branch and timestamp"
					+ " with --branch-and-timestamp: " + timestamp + "\nYou must  use the"
					+ " format BRANCH:TIMESTAMP, where TIMESTAMP is a Unix timestamp in milliseconds"
					+ " or the string 'HEAD' (to upload to the latest commit on that branch).", parser);
		}

		String timestampPart = parts[1];
		if (timestampPart.equalsIgnoreCase("HEAD")) {
			return;
		}

		validateTimestamp(parser, timestampPart);
	}

	private void validateTimestamp(ArgumentParser parser, String timestampPart) throws ArgumentParserException {
		try {
			long unixTimestamp = Long.parseLong(timestampPart);
			if (unixTimestamp < 10000000000L) {
				String millisecondDate = DateTimeFormatter.RFC_1123_DATE_TIME
						.format(Instant.ofEpochMilli(unixTimestamp).atZone(ZoneOffset.UTC));
				String secondDate = DateTimeFormatter.RFC_1123_DATE_TIME
						.format(Instant.ofEpochSecond(unixTimestamp).atZone(ZoneOffset.UTC));
				throw new ArgumentParserException("You specified an invalid timestamp with"
						+ " --branch-and-timestamp. The timestamp '" + timestampPart + "'" + " is equal to "
						+ millisecondDate + ". This is probably not what"
						+ " you intended. Most likely you specified the timestamp in seconds,"
						+ " instead of milliseconds. If you use " + timestampPart + "000" + " instead, it will mean "
						+ secondDate, parser);
			}
		} catch (NumberFormatException e) {
			throw new ArgumentParserException("You specified an invalid timestamp with"
					+ " --branch-and-timestamp. Expected either 'HEAD' or a unix timestamp"
					+ " in milliseconds since 00:00:00 UTC Thursday, 1 January 1970, e.g."
					+ " master:1606743774000\nInstead you used: " + timestampPart, parser);
		}
	}

	private boolean hasMoreThanOneCommitOptionSet() {
		return commit != null && timestamp != null;
	}
}
