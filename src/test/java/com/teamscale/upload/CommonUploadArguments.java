package com.teamscale.upload;

import java.io.File;
import java.util.List;

import com.teamscale.upload.test_utils.TeamscaleMockServer;
import com.teamscale.upload.utils.SecretUtils;

/**
 * The arguments that every teamscale-upload command accepts: which Teamscale
 * server and project to talk to, how to authenticate and how to establish the
 * connection. Mirrors {@link CommonCommandLineOptions} on the test side.
 * <p>
 * Subclasses add the arguments specific to their command.
 *
 * @param <T>
 *            the concrete subclass, so that the setters defined here can be
 *            chained with the ones defined by the subclass.
 */
abstract class CommonUploadArguments<T extends CommonUploadArguments<T>> implements UploadArguments {

	private static final String TEAMSCALE_TEST_USER = "teamscale-upload-build-test-user";

	private String url = "https://cqse.teamscale.io/";
	private String user = TEAMSCALE_TEST_USER;
	private String accessKey = getAccessKeyFromCi();
	private String project = "teamscale-upload";
	private String proxy = null;
	private boolean insecure = false;
	private boolean useKeystore = false;
	private boolean stackTrace = false;
	private boolean debug = false;
	private Integer maxAttempts = null;
	private String timeoutInSeconds = null;
	private File stdinFile = null;

	/**
	 * Returns this object typed as the concrete subclass, so that the setters
	 * defined here can be chained with the ones defined by the subclass.
	 */
	@SuppressWarnings("unchecked")
	private T self() {
		return (T) this;
	}

	/** Configures the given url as Teamscale server. */
	T withUrl(String url) {
		this.url = url;
		return self();
	}

	/** Configures the Teamscale user. */
	T withUser(String user) {
		this.user = user;
		return self();
	}

	/** Configures the Teamscale project. */
	T withProject(String project) {
		this.project = project;
		return self();
	}

	/** Configures the access key for the Teamscale server. */
	T withAccessKey(String accessKey) {
		this.accessKey = accessKey;
		return self();
	}

	/**
	 * No access key is specified as an option. The key which is specified in the
	 * environment variable should be used instead.
	 */
	T withoutAccessKeyInOption() {
		this.accessKey = null;
		return self();
	}

	/**
	 * Configures that the access key is read from the given file.
	 */
	T withAccessKeyViaStdin(String stdinFilePath) {
		this.accessKey = "-";
		// If the access key is set to '-', we need to pipe the key from a file via
		// stdin.
		this.stdinFile = new File(stdinFilePath);
		return self();
	}

	/** Sets the proxy to use when doing the upload in the format url:port. */
	T withProxy(String proxy) {
		this.proxy = proxy;
		return self();
	}

	/**
	 * Sets whether to use insecure certificate checking (i.e., skip checking
	 * entirely).
	 */
	T withInsecure() {
		this.insecure = true;
		return self();
	}

	/**
	 * Sets whether to use the {@link TeamscaleMockServer#TRUSTSTORE} as parameter
	 * for --trusted-keystore.
	 */
	T withKeystore() {
		this.useKeystore = true;
		return self();
	}

	/** Sets whether to use the --stacktrace option. */
	T withStackTrace() {
		this.stackTrace = true;
		return self();
	}

	/** Enables debug logging. */
	T withDebug() {
		this.debug = true;
		return self();
	}

	/** Sets the maximum number of attempts for transient errors. */
	T withMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
		return self();
	}

	/** Sets the timeout for the Teamscale-service call. */
	T withTimeoutInSeconds(String timeoutInSeconds) {
		this.timeoutInSeconds = timeoutInSeconds;
		return self();
	}

	@Override
	public File getStdinFile() {
		return stdinFile;
	}

	/**
	 * Appends the options that all commands share to the given command.
	 */
	protected void addCommonOptions(List<String> command) {
		command.add("--server");
		command.add(url);
		command.add("--user");
		command.add(user);
		command.add("--project");
		command.add(project);
		if (accessKey != null) {
			command.add("--accesskey");
			command.add(accessKey);
		}
		if (proxy != null) {
			command.add("--proxy");
			command.add(proxy);
		}
		if (insecure) {
			command.add("--insecure");
		}
		if (useKeystore) {
			command.add("--trusted-keystore");
			command.add(TeamscaleMockServer.TRUSTSTORE.getAbsolutePath() + ";password");
		}
		if (stackTrace) {
			command.add("--stacktrace");
		}
		if (debug) {
			command.add("--debug");
		}
		if (timeoutInSeconds != null) {
			command.add("--timeout");
			command.add(timeoutInSeconds);
		}
		if (maxAttempts != null) {
			command.add("--max-attempts");
			command.add(String.valueOf(maxAttempts));
		}
	}

	private static String getAccessKeyFromCi() {
		String accessKey = System.getenv(SecretUtils.TEAMSCALE_ACCESS_KEY_ENVIRONMENT_VARIABLE);
		if (accessKey == null) {
			return "not-a-ci-build";
		}
		return accessKey;
	}
}
