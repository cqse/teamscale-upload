package com.teamscale.upload.xcode;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

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
		try {
			String result = ProcessUtils.executeProcess("xcrun", "xccov", "view", "--archive",
					reportDirectory.getAbsolutePath(), "--file", sourceFile);

			return new ConversionResult(sourceFile, result);
		} catch (InterruptedException e) {
			// Drop exception since this only occurs if the user terminates the application
			// with Ctrl+C.
		} catch (IOException e) {
			LogUtils.warn("Error while exporting coverage for source file " + sourceFile + ": " + e.getMessage());
		}

		return null;
	}
}
