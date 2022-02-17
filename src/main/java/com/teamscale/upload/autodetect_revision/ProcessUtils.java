package com.teamscale.upload.autodetect_revision;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.teamscale.upload.utils.FileSystemUtils;

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

	/**
	 * Starts a {@link Process} for the command and returns the
	 * {@link ProcessResult}.
	 */
	public static ProcessResult run(String... command) {
		return runWithStdIn(null, command);
	}

	/**
	 * Starts a {@link Process} for the command and returns the
	 * {@link ProcessResult}. Additionally, takes a file which can be used to pipe
	 * input to stdin of the command. The parameter stdinFile may be null to
	 * indicate that no stdin should be used.
	 */
	public static ProcessResult runWithStdIn(File stdInFile, String... command) {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(command);
			if (stdInFile != null) {
				processBuilder.redirectInput(stdInFile);
			}
			Process process = processBuilder.start();

			/*
			 * Both input streams need to be drained in separate threads since reading
			 * either stream can be a blocking operation if the other stream has reached the
			 * platform dependent buffer limit for standard output.
			 *
			 * See https://stackoverflow.com/a/7562321.
			 */
			ProcessOutputReader inputStreamReader = new ProcessOutputReader(process.getInputStream());
			ProcessOutputReader errorStreamReader = new ProcessOutputReader(process.getErrorStream());
			Thread inputStreamReaderThread = new Thread(inputStreamReader);
			Thread errorStreamReaderThread = new Thread(errorStreamReader);
			inputStreamReaderThread.start();
			errorStreamReaderThread.start();

			int exitCode = process.waitFor();

			/*
			 * Ensure that both threads have finished execution if the process terminates
			 * earlier than the threads.
			 */
			inputStreamReaderThread.join();
			errorStreamReaderThread.join();
			inputStreamReader.rethrowCaughtException();
			errorStreamReader.rethrowCaughtException();

			return new ProcessResult(exitCode, inputStreamReader.result, errorStreamReader.result, null);
		} catch (IOException | InterruptedException e) {
			return new ProcessResult(-1, "", e.getMessage(), e);
		}
	}

	/**
	 * Runnable for reading the input stream asynchronously from a separate thread
	 * to prevent a deadlock due to blocking read operations.
	 */
	private static class ProcessOutputReader implements Runnable {

		private final InputStream inputStream;

		private IOException exception;

		private String result;

		private ProcessOutputReader(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		@Override
		public void run() {
			try {
				result = FileSystemUtils.getInputAsString(inputStream);
			} catch (IOException e) {
				exception = e;
			}
		}

		private void rethrowCaughtException() throws IOException {
			if (exception != null) {
				throw exception;
			}
		}
	}

	/**
	 * The result of a process execution.
	 */
	public static class ProcessResult {

		/**
		 * The exit code of the process.
		 */
		public final int exitCode;

		/**
		 * The stdout output of the process.
		 */
		public final String output;

		/**
		 * The stderr output of the process.
		 */
		public final String errorOutput;

		/**
		 * A potential Java exception that occurred while executing the process.
		 */
		public final Exception exception;

		private ProcessResult(int exitCode, String output, String errorOutput, Exception exception) {
			this.exitCode = exitCode;
			this.output = output;
			this.errorOutput = errorOutput;
			this.exception = exception;
		}

		/**
		 * Returns the {@link #output} followed by the {@link #errorOutput}.
		 */
		public String getOutputAndErrorOutput() {
			String outputAndErrorOutput = "";
			if (output != null) {
				outputAndErrorOutput += output;
			}
			if (errorOutput != null) {
				outputAndErrorOutput += errorOutput;
			}
			return outputAndErrorOutput;
		}

		/**
		 * Returns true if the processed exited successfully.
		 */
		public boolean wasSuccessful() {
			return exception == null && exitCode == EXIT_CODE_SUCCESS;
		}
	}
}
