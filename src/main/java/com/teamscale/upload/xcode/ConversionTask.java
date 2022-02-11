package com.teamscale.upload.xcode;

import java.io.File;
import java.util.concurrent.Callable;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.LogUtils;

/**
 * Conversion task for one source file in the XCResult report directory.
 */
class ConversionTask implements Callable<ConversionResult> {

	private final File reportDirectory;

	private final String sourceFile;

	ConversionTask(File reportDirectory, String sourceFile) {
		this.reportDirectory = reportDirectory;
		this.sourceFile = sourceFile;
	}

	@Override
	public ConversionResult call() {
		ProcessUtils.ProcessResult results = ProcessUtils.run("xcrun", null, "xccov", "view", "--archive",
				reportDirectory.getAbsolutePath(), "--file", sourceFile);
		if (results.wasSuccessful()) {
			String result = results.stdoutAndStdErr;
			return new ConversionResult(sourceFile, result);
		} else if (results.exitCode == ProcessUtils.EXIT_CODE_CTRL_C_TERMINATED) {
			// Drop exception since this only occurs if the user terminates the application
			// with Ctrl+C.
			return null;
		} else if (results.exception != null) {
			LogUtils.warn("Error while exporting coverage for source file " + sourceFile + ": "
					+ results.exception.getMessage());
			return null;
		} else {
			LogUtils.warn("Error while exporting coverage for source file " + sourceFile + " (unknown error)");
			return null;
		}
	}
}
