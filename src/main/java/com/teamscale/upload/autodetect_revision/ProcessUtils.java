package com.teamscale.upload.autodetect_revision;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.PumpStreamHandler;

/**
 * Utility methods for executing processes on the command line.
 */
public class ProcessUtils {

	/**
	 * Return code that we expect for Processes that were terminated with Ctrl-C by
	 * a user (check against {@link ProcessResult#exitCode}.
	 */
	public static final int EXIT_CODE_CTRL_C_TERMINATED = 130;

	private static final int EXIT_CODE_SUCCESS = 0;

	/** Run the command with the given arguments. */
	public static ProcessResult run(String command, String... arguments) {
		return runWithStdin(command, null, arguments);
	}

	/**
	 * Run the command with the given arguments. This method should not be called
	 * directly in production code (only with parameter stdinFile=null).
	 *
	 * To allow simulating user input in test, this method takes a file which can be
	 * used to pipe input to stdin of the command. The parameter stdinFile may be
	 * null to indicate that no stdin should be used.
	 */
	public static ProcessResult runWithStdin(String command, String stdinFile, String... arguments) {

		CommandLine commandLine = new CommandLine(command);
		commandLine.addArguments(arguments, false);

		ByteArrayInputStream input = null;
		if (stdinFile != null) {
			try {
				// We cannot directly pipe the file output to stdin, but we can use a byte array
				// input stream
				// https://stackoverflow.com/questions/4695664/how-to-pipe-a-string-argument-to-an-executable-launched-with-apache-commons-exec
				String accessKey = Files.readString(Paths.get(stdinFile));
				input = new ByteArrayInputStream(accessKey.getBytes(StandardCharsets.ISO_8859_1));
			} catch (IOException e) {
				System.err.println("Could not read access key from file `" + stdinFile + ".");
				return new ProcessResult(-1, "", e);
			}
		}

		DefaultExecutor executor = new DefaultExecutor();
		CaptureStreamHandler handler = new CaptureStreamHandler(input);
		executor.setStreamHandler(handler);
		executor.setExitValues(null); // don't throw in case of non-zero exit codes

		try {
			int exitCode = executor.execute(commandLine);
			return new ProcessResult(exitCode, handler.getStdOutAndStdErr(), null);
		} catch (IOException e) {
			System.err.println("Tried to run `" + command + " " + String.join(" ", arguments)
					+ "` which failed with an exception");
			e.printStackTrace();
			return new ProcessResult(-1, "", e);
		}
	}

	private static class CaptureStreamHandler implements ExecuteStreamHandler {

		private final ByteArrayOutputStream stdoutAndStderrStream = new ByteArrayOutputStream();
		private final PumpStreamHandler wrappedHandler;

		public CaptureStreamHandler(ByteArrayInputStream input) {
			// Currently, we do not need to differentiate between stdout and stderr
			wrappedHandler = new PumpStreamHandler(stdoutAndStderrStream, stdoutAndStderrStream, input);
		}

		public String getStdOutAndStdErr() {
			// we want to use the platform default charset here
			// as I'm guessing it's used for the process output
			return stdoutAndStderrStream.toString();
		}

		@Override
		public void setProcessInputStream(OutputStream os) {
			wrappedHandler.setProcessInputStream(os);
		}

		@Override
		public void setProcessErrorStream(InputStream is) {
			wrappedHandler.setProcessErrorStream(is);
		}

		@Override
		public void setProcessOutputStream(InputStream is) {
			wrappedHandler.setProcessOutputStream(is);
		}

		@Override
		public void start() {
			wrappedHandler.start();
		}

		@Override
		public void stop() throws IOException {
			wrappedHandler.stop();
		}
	}

	public static class ProcessResult {
		public final int exitCode;
		public final String stdoutAndStdErr;
		public final IOException exception;

		private ProcessResult(int exitCode, String stdoutAndStdErr, IOException exception) {
			this.exitCode = exitCode;
			this.stdoutAndStdErr = stdoutAndStdErr;
			this.exception = exception;
		}

		public boolean wasSuccessful() {
			return exception == null && exitCode == EXIT_CODE_SUCCESS;
		}
	}
}
