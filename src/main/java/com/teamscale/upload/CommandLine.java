package com.teamscale.upload;

import com.teamscale.upload.utils.LogUtils;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import okhttp3.HttpUrl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Parses and validates the command line arguments.
 */
public class CommandLine {

	/**
	 * The Teamscale project ID or alias.
	 */
	public final String project;
	/**
	 * The Teamscale username.
	 */
	public final String username;
	/**
	 * Teamscale access key used for authentication. Either obtained via
	 * command-line option, via stdin or via the environment variable
	 * $TEAMSCALE_ACCESSKEY.
	 */
	public final String accessKey;
	/**
	 * The Teamscale partition.
	 */
	public final String partition;
	/**
	 * The uploaded data's report format.
	 */
	public final String format;
	/**
	 * The commit to which to upload. May be null.
	 */
	public final String commit;
	/**
	 * The branch:timestamp to which to upload. May be null.
	 */
	public final String timestamp;
	/**
	 * The Teamscale server URL.
	 */
	public final HttpUrl url;
	/**
	 * The files to upload given on the command-line directly
	 */
	public final List<String> files;
	/**
	 * The input file to use or null if none is given.
	 */
	public final Path inputFile;
	/**
	 * Whether to validate SSL certificates and hostnames.
	 */
	public final Boolean validateSsl;
	/**
	 * The message given by the user or null if none was explicitly given (default
	 * message should be used in this case).
	 */
	public final String message;
	/**
	 * Additional lines to append to the end of the message. Does not include
	 * line-terminators at the end of each entry.
	 */
	public final List<String> additionalMessageLines;
	/**
	 * Whether to print stack traces for handled exceptions.
	 */
	public final boolean printStackTrace;

	private final String keystorePathAndPassword;

	private CommandLine(Namespace namespace) {
		this.project = namespace.getString("project");
		this.username = namespace.getString("user");
		String accessKeyViaOption = namespace.getString("accesskey");
		this.accessKey = determineAccessKeyToUse(accessKeyViaOption);
		this.partition = namespace.getString("partition");
		this.commit = namespace.getString("commit");
		this.timestamp = namespace.getString("branch_and_timestamp");
		this.files = getListSafe(namespace, "files");
		this.url = HttpUrl.parse(namespace.getString("server"));
		this.message = namespace.getString("message");
		this.keystorePathAndPassword = namespace.getString("trusted_keystore");
		this.validateSsl = !namespace.getBoolean("insecure");
		this.additionalMessageLines = getListSafe(namespace, "append_to_message");
		this.printStackTrace = namespace.getBoolean("stacktrace");

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
	 * Determines the access key to be used for further authentication by using one
	 * of these in the following order:
	 * <ul>
	 * <li>Provided via the option --access-key <access-key></li>
	 * <li>Provided via STDIN when option "--access-key -" is used</li>
	 * <li>Provided via environment variable $TEAMSCALE_ACCESSKEY</li>
	 * </ul>
	 */
	private String determineAccessKeyToUse(String accessKeyViaOption) {
		if (accessKeyViaOption != null && !("-".equals(accessKeyViaOption))) {
			return accessKeyViaOption;
		} else if (accessKeyViaOption != null) {
			System.out.print("Reading access key from standard input: ");
			Scanner inputScanner = new Scanner(System.in);
			String accessKeyViaStdin = inputScanner.nextLine();
			inputScanner.close();
			System.out.println("\nRead access key");
			return accessKeyViaStdin;
		}
		return System.getenv("TEAMSCALE_ACCESSKEY"); // may be null, but is validated later
	}

	private static List<String> getListSafe(Namespace namespace, String key) {
		List<String> list = namespace.getList(key);
		if (list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	/**
	 * Parses the given command line arguments and validates them.
	 */
	public static CommandLine parseArguments(String[] args) {
		ArgumentParser parser = ArgumentParsers.newFor("teamscale-upload").build().defaultHelp(true)
				.description("Upload coverage, findings, ... to Teamscale.");

		parser.addArgument("-s", "--server").metavar("URL").required(true)
				.help("The url under which the Teamscale server can be reached.");
		parser.addArgument("-p", "--project").metavar("PROJECT").required(true)
				.help("The project ID or alias (NOT the project name!) to which to upload the data.");
		parser.addArgument("-u", "--user").metavar("USER").required(true)
				.help("The username used to perform the upload. Must have the"
						+ " 'Perform External Uploads' permission for the given Teamscale project.");
		parser.addArgument("-a", "--accesskey").metavar("ACCESSKEY").required(false)
				.help("The IDE access key of the given user. Can be retrieved in Teamscale under Admin > Users."
						+ "Alternatively, use '--accesskey -' for the program to obtain the access key via"
						+ "the standard input, or specify environment variable $TEAMSCALE_ACCESSKEY.");
		parser.addArgument("-t", "--partition").metavar("PARTITION").required(true)
				.help("The partition into which the data is inserted in Teamscale."
						+ " Successive uploads into the same partition will overwrite the data"
						+ " previously inserted there, so use different partitions if you'd instead"
						+ " like to merge data from different sources (e.g. one for Findbugs findings"
						+ " and one for JaCoCo coverage).");
		parser.addArgument("-f", "--format").metavar("FORMAT").required(false)
				.help("The file format of the reports which are specified as command line arguments."
						+ "\nSee http://cqse.eu/upload-formats for a full list of supported file formats.");
		parser.addArgument("-c", "--commit").metavar("REVISION").required(false)
				.help("The version control commit for which you obtained the report files."
						+ " E.g. if you obtained a test coverage report in your CI pipeline, then this"
						+ " is the commit the CI pipeline built before running the tests."
						+ " Can be either a Git SHA1, a SVN revision number or an Team Foundation changeset ID.");
		parser.addArgument("-b", "--branch-and-timestamp").metavar("BRANCH_AND_TIMESTAMP").required(false)
				.help("The branch and Unix Epoch timestamp for which you obtained the report files."
						+ " E.g. if you obtained a test coverage report in your CI pipeline, then this"
						+ " is the branch and the commit timestamp of the commit that the CI pipeline"
						+ " built before running the tests. The timestamp must be milliseconds since"
						+ " 00:00:00 UTC Thursday, 1 January 1970 or the string 'HEAD' to upload to"
						+ " the latest revision on that branch." + "\nFormat: BRANCH:TIMESTAMP"
						+ "\nExample: master:1597845930000" + "\nExample: develop:HEAD");
		parser.addArgument("--message").metavar("MESSAGE").required(false)
				.help("The message for the commit created in Teamscale for this upload. Will be"
						+ " visible in the Activity perspective. Defaults to a message containing"
						+ " useful meta-information about the upload and the machine performing it.");
		parser.addArgument("-i", "--input").metavar("INPUT").required(false)
				.help("A file which contains additional report file patterns. See INPUTFILE for a"
						+ " detailed description of the file format.");
		parser.addArgument("-k", "--insecure").action(Arguments.storeTrue()).required(false)
				.help("Causes SSL certificates to be accepted without validation, which makes"
						+ " using this tool with self-signed or invalid certificates easier.");
		parser.addArgument("--trusted-keystore").required(false)
				.help("A Java keystore file and its corresponding password. The keystore contains"
						+ " additional certificates that should be trusted when performing SSL requests."
						+ " Separate the path from the password with a semicolon, e.g:"
						+ "\n/path/to/keystore.jks;PASSWORD"
						+ "\nThe path to the keystore must not contain a semicolon. When this option"
						+ " is used, --validate-ssl will automatically be enabled as well.");
		parser.addArgument("--append-to-message").metavar("LINE").action(Arguments.append()).required(false)
				.help("Appends the given line to the message. Use this to augment the autogenerated"
						+ " message instead of replacing it. You may specify this parameter multiple"
						+ " times to append several lines to the message.");
		parser.addArgument("files").metavar("FILES").nargs("*")
				.help("Path(s) or pattern(s) of the report files to upload. Alternatively, you may"
						+ " provide input files via -i or --input");
		parser.addArgument("--stacktrace").action(Arguments.storeTrue()).required(false)
				.help("Enables printing stack traces in all cases where errors occur." + " Used for debugging.");

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
				+ "\npattern1/**.findbugs.xml" + "\npattern2/**.findbugs.xml");

		try {
			Namespace namespace = parser.parseArgs(args);
			CommandLine commandLine = new CommandLine(namespace);
			commandLine.validate(parser);
			return commandLine;
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
			return null;
		}

	}

	/**
	 * Returns the path to the keystore to use for self-signed certificates or null
	 * if none was configured.
	 */
	public String getKeyStorePath() {
		if (keystorePathAndPassword == null) {
			return null;
		}
		return keystorePathAndPassword.split(";", 2)[0];
	}

	/**
	 * Returns the password for the keystore to use for self-signed certificates or
	 * null if none was configured.
	 */
	public String getKeyStorePassword() {
		if (keystorePathAndPassword == null) {
			return null;
		}
		return keystorePathAndPassword.split(";", 2)[1];
	}

	/**
	 * Checks the validity of the command line arguments and throws an exception if
	 * any invalid configuration is detected.
	 */
	private void validate(ArgumentParser parser) throws ArgumentParserException {
		if (url == null) {
			throw new ArgumentParserException("You provided an invalid URL in the --server option", parser);
		}

		validateKeystoreSettings(parser);
		validateAccessKey(parser);

		if (hasMoreThanOneCommitOptionSet()) {
			throw new ArgumentParserException("You used more than one of --commit and --branch-and-timestamp."
					+ " You must choose one of these options to specify the commit for which you would like to"
					+ " upload data to Teamscale", parser);
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

	private void validateKeystoreSettings(ArgumentParser parser) throws ArgumentParserException {
		if (!validateSsl && keystorePathAndPassword != null) {
			LogUtils.warn("You specified a trusted keystore via --trust-keystore but also disabled SSL"
					+ " validation via --insecure. SSL validation is now disabled and your keystore"
					+ " will not be used.");
		}

		if (keystorePathAndPassword != null && !keystorePathAndPassword.contains(";")) {
			throw new ArgumentParserException("You forgot to add the password for the --trust-keystore file "
					+ keystorePathAndPassword + "."
					+ " You must add it to the end of the path, separated by a semicolon, e.g: --trust-keystore "
					+ keystorePathAndPassword + ";PASSWORD", parser);
		}
	}

	private void validateAccessKey(ArgumentParser parser) throws ArgumentParserException {
		if (accessKey == null) {
			throw new ArgumentParserException("You did not specify a Teamscale access key. You can either specify"
					+ "it via --accesskey <access key>, via setting the environment variable $TEAMSCALE_ACCESSKEY or via stdin"
					+ " using '--accesskey -'.", parser);
		}
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