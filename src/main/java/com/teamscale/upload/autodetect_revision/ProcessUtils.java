package com.teamscale.upload.autodetect_revision;

import java.io.File;
import java.io.IOException;

import com.teamscale.upload.utils.FileSystemUtils;

/**
 * Utility methods for executing processes on the command line.
 */
public class ProcessUtils {

	private static final int EXIT_CODE_SUCCESS = 0;

	/**
	 * Starts a {@link Process} for the command and returns the
	 * {@link ProcessResult}.
	 */
	public static ProcessResult run(String... command) {
		return run(null, command);
	}

	/**
	 * Starts a {@link Process} for the command and returns the
	 * {@link ProcessResult}. Additionally, takes a file which can be used to pipe
	 * input to stdin of the command. The parameter stdinFile may be null to
	 * indicate that no stdin should be used.
	 */
	public static ProcessResult run(File stdInFile, String... command) {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder(command);
			if (stdInFile != null) {
				processBuilder.redirectInput(stdInFile);
			}
			Process process = processBuilder.start();
			String output = FileSystemUtils.getInputAsString(process.getInputStream());
			String errorOutput = FileSystemUtils.getInputAsString(process.getErrorStream());
			int exitCode = process.waitFor();

			if (exitCode != EXIT_CODE_SUCCESS) {
				return new ProcessResult(exitCode, output, errorOutput, null);
			}

			return new ProcessResult(exitCode, output, errorOutput, null);
		} catch (IOException | InterruptedException e) {
			return new ProcessResult(-1, null, "Error while executing process: " + e.getMessage(), e);
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
			return exception == null && exitCode == 0;
		}
	}
}
