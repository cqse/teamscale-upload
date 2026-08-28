package com.teamscale.upload;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.teamscale.upload.utils.LogUtils;
import com.teamscale.upload.utils.SecretUtils;

import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * The command-line options that every teamscale-upload command shares: which
 * Teamscale server and project to talk to, how to authenticate and how to
 * establish the connection.
 * <p>
 * Subclasses add the options specific to their command.
 */
public abstract class CommonCommandLineOptions {

	/**
	 * The Teamscale project ID.
	 */
	public final String project;
	/**
	 * The Teamscale username.
	 */
	public final String username;
	/**
	 * Teamscale access key used for authentication.
	 */
	public final String accessKey;
	/**
	 * The Teamscale server URL.
	 */
	public final HttpUrl url;
	/**
	 * Whether to validate SSL certificates and hostnames.
	 */
	public final Boolean validateSsl;
	/**
	 * Url and port of the proxy to use.
	 */
	public final String proxy;
	/**
	 * Whether to print stack traces for handled exceptions.
	 */
	public final boolean printStackTrace;
	/**
	 * Whether to print debug log output.
	 */
	public final boolean debugLogEnabled;
	/**
	 * The timeout in seconds for TCP connect, read and write of the
	 * {@link OkHttpClient} used for requests. Defaults to 60 seconds.
	 */
	public final String timeoutInSecondsAsString;
	/**
	 * The maximum number of attempts for transient network errors. Defaults to 3.
	 */
	public final int maxAttempts;

	private final String keystorePathAndPassword;

	protected CommonCommandLineOptions(Namespace namespace) {
		this.project = namespace.getString("project");
		this.username = namespace.getString("user");
		this.accessKey = SecretUtils.determineAccessKeyToUse(namespace.getString("accesskey"));
		this.url = HttpUrl.parse(namespace.getString("server"));
		this.proxy = namespace.getString("proxy");
		this.keystorePathAndPassword = namespace.getString("trusted_keystore");
		this.validateSsl = !namespace.getBoolean("insecure");
		this.timeoutInSecondsAsString = namespace.getString("timeout");
		this.printStackTrace = namespace.getBoolean("stacktrace");
		this.debugLogEnabled = namespace.getBoolean("debug");
		this.maxAttempts = namespace.getInt("max_attempts");
	}

	/**
	 * Registers the options shared by all commands on the given parser.
	 */
	public static void addCommonArguments(ArgumentParser parser) {
		parser.addArgument("-s", "--server").metavar("URL").required(true)
				.help("The url under which the Teamscale server can be reached.");
		parser.addArgument("-p", "--project").metavar("PROJECT").required(true)
				.help("The project ID (NOT the project name!) to which to upload the data.");
		parser.addArgument("-u", "--user").metavar("USER").required(true)
				.help("The username used to perform the upload. Must have the"
						+ " 'Perform External Uploads' permission for the given Teamscale project.");
		parser.addArgument("-a", "--accesskey").metavar("ACCESSKEY").required(false)
				.help("The IDE access key of the given user. Can be retrieved in Teamscale under Admin > Users."
						+ "If the argument is a single dash, i.e. '--accesskey -', teamscale-upload will read the"
						+ " access key from standard input. As a third option, you can provide the access key in the"
						+ " environment variable $" + SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE + ".");
		parser.addArgument("-x", "--proxy").metavar("PROXY").required(false).help(
				"The proxy url + port that should be used to connect to Teamscale. Format url:port, e.g. localhost:8080. "
						+ "If your proxy needs authentication, you can set the TEAMSCALE_PROXY_USER and TEAMSCALE_PROXY_PASSWORD"
						+ " environment variables and teamscale-upload will automatically respect them.");
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
		parser.addArgument("--stacktrace").action(Arguments.storeTrue()).required(false)
				.help("Enables printing stack traces in all cases where errors occur. Used for debugging.");
		parser.addArgument("--debug").action(Arguments.storeTrue()).required(false)
				.help("Enables printing debug log output. This automatically enables --stacktrace.");
		parser.addArgument("--timeout").metavar("TIMEOUT_IN_SECONDS").required(false)
				.help("Sets the timeout in seconds for TCP connect, read and write for HTTP requests. "
						+ "Defaults to 60 seconds.");
		parser.addArgument("--max-attempts").metavar("MAX_ATTEMPTS").type(Integer.class).setDefault(3).required(false)
				.help("The maximum number of attempts for uploads that fail due to transient network errors"
						+ " (e.g. connection resets, server errors). Defaults to 3.");
	}

	/**
	 * Parses the given arguments with the given parser, builds the options object
	 * via the given factory and validates it.
	 * <p>
	 * Terminates the program if the arguments are invalid or if the user requested
	 * the help screen.
	 */
	protected static <T extends CommonCommandLineOptions> T parseAndValidate(ArgumentParser parser, String[] args,
			Function<Namespace, T> factory) {
		try {
			Namespace namespace = parser.parseArgs(args);
			T options = factory.apply(namespace);
			options.validate(parser);
			return options;
		} catch (HelpScreenException e) {
			System.exit(0); // requesting the help screen should return exit code 0
			return null;
		} catch (ArgumentParserException e) {
			parser.handleError(e);
			System.exit(1);
			return null;
		}
	}

	/**
	 * Returns the given list from the namespace or an empty list if it is not set.
	 */
	protected static List<String> getListSafe(Namespace namespace, String key) {
		List<String> list = namespace.getList(key);
		if (list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	/**
	 * Checks the validity of the command line arguments and throws an exception if
	 * any invalid configuration is detected.
	 */
	protected abstract void validate(ArgumentParser parser) throws ArgumentParserException;

	/**
	 * The version control revision this upload targets. Used to produce helpful
	 * error messages when Teamscale does not know the revision.
	 */
	public abstract String getRevision();

	/**
	 * An additional hint that is appended to the error message when Teamscale
	 * answers a request with HTTP 404. Returns null if this command has no
	 * additional hint to offer.
	 */
	public String getNotFoundHint() {
		return null;
	}

	/**
	 * Validates this {@link CommonCommandLineOptions}.
	 */
	protected void validateCommonOptions(ArgumentParser parser) throws ArgumentParserException {
		if (url == null) {
			throw new ArgumentParserException("You provided an invalid URL in the --server option", parser);
		}

		validateTimeoutInSeconds(parser);
		validateMaxAttempts(parser);
		validateProxy(parser);
		validateKeystoreSettings(parser);
		validateAccessKey(parser);
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
	 * Returns the timeout in seconds as a {@link Long}.
	 */
	public long getTimeoutInSeconds() {
		if (timeoutInSecondsAsString == null) {
			return 60L;
		}
		return Long.parseLong(timeoutInSecondsAsString);
	}

	private void validateMaxAttempts(ArgumentParser parser) throws ArgumentParserException {
		if (maxAttempts <= 0) {
			throw new ArgumentParserException("The maximum number of attempts must be a positive integer.", parser);
		}
	}

	private void validateProxy(ArgumentParser parser) throws ArgumentParserException {
		if (proxy == null) {
			return;
		}
		String[] proxyParts = proxy.split(":");
		if (proxyParts.length == 2) {
			String port = proxyParts[1];
			try {
				Integer.parseInt(port);
			} catch (NumberFormatException e) {
				throw new ArgumentParserException(
						"The proxy port is not a number. Please check that the proxy parameter follows the format proxy-url:port",
						parser);
			}
		} else {
			throw new ArgumentParserException(
					"The proxy parameter is in the wrong format, please only specify `proxy-url:port`.", parser);
		}
	}

	private void validateTimeoutInSeconds(ArgumentParser parser) throws ArgumentParserException {
		if (timeoutInSecondsAsString == null) {
			return;
		}
		try {
			long timeoutInSeconds = Long.parseLong(timeoutInSecondsAsString);
			if (timeoutInSeconds <= 0L) {
				throw new ArgumentParserException("The timeout in seconds must be an integer greater than 0.", parser);
			}
		} catch (NumberFormatException e) {
			throw new ArgumentParserException("The timeout in seconds must be an integer greater than 0.", parser);
		}
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
			throw new ArgumentParserException("You did not specify a Teamscale access key. You can either specify "
					+ "it via --accesskey, via the environment variable $"
					+ SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE + " or via stdin using '--accesskey -'.",
					parser);
		}
	}
}
