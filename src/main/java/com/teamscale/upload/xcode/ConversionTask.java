package com.teamscale.upload.xcode;

import java.io.File;
import java.util.concurrent.Callable;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.autodetect_revision.ProcessUtils.ProcessResult;
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
		ProcessResult result = ProcessUtils.run("xcrun", "xccov", "view", "--archive",
				reportDirectory.getAbsolutePath(), "--file", sourceFile);

		if (result.wasSuccessful()) {
			return new ConversionResult(sourceFile, result.output);
		}

		LogUtils.warn("Error while exporting coverage for source file " + sourceFile + ": " + result.errorOutput);
		return null;
	}
}
