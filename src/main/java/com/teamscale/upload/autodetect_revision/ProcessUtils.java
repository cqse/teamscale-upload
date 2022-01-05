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

import com.teamscale.upload.utils.FileSystemUtils;
import com.teamscale.upload.utils.LogUtils;

/**
 * Utility methods for executing processes on the command line.
 */
public class ProcessUtils {

	private static final int EXIT_CODE_CTRL_C_TERMINATED = 130;

	private static final int EXIT_CODE_SUCCESS = 0;

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

	public static ProcessResult run(String command, String... arguments) {
		return runWithStdin(command, null, arguments);
	}

	/**
	 * Starts a {@link Process} for the commands.
	 */
	public static Process startProcess(String... commands) throws IOException {
		return new ProcessBuilder(commands).start();
	}

	/**
	 * Starts a {@link Process} for the commands and returns the standard output as
	 * a {@link String}.
	 */
	public static String executeProcess(String... commands) throws IOException, InterruptedException {
		Process process = startProcess(commands);
		String output = FileSystemUtils.getInputAsString(process.getInputStream());
		ensureProcessFinishedWithoutErrors(process);
		return output;
	}

	/**
	 * Ensures that the {@link Process} has finished successfully and logs errors as
	 * warnings to the console.
	 */
	private static void ensureProcessFinishedWithoutErrors(Process process) throws IOException, InterruptedException {
		String errorOutput = FileSystemUtils.getInputAsString(process.getErrorStream());
		int exitCode = process.waitFor();

		if (exitCode != EXIT_CODE_SUCCESS && exitCode != EXIT_CODE_CTRL_C_TERMINATED) {
			LogUtils.warn(String.format("Process %s terminated with non-zero exit value %d: %s", process,
					process.exitValue(), errorOutput));
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
			return exception == null && exitCode == 0;
		}
	}
}
