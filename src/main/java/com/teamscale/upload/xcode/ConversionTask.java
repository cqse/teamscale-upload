package com.teamscale.upload.xcode;

import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.utils.LogUtils;

import java.io.ByteArrayOutputStream;
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
            Process process = ProcessUtils.executeProcess("xcrun", "xccov", "view", "--archive",
                    reportDirectory.getAbsolutePath(), "--file", sourceFile);
            byte[] result;

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                /*
                 * We read the output before waiting for the process to finish since xccov doesn't terminate
                 * if the output is large and not read from the input stream. The behavior is special to
                 * xccov and doesn't occur for any other call of XCode command line tools.
                 */
                process.getInputStream().transferTo(baos);
                result = baos.toByteArray();
            }

            ProcessUtils.ensureProcessFinishedWithoutErrors(process, XCResultConverter.TIMEOUT_SECONDS);

            return new ConversionResult(sourceFile, result);
        } catch (IOException | InterruptedException e) {
            LogUtils.warn("Error while exporting coverage for source file " + sourceFile + ": " + e.getMessage());
            return null;
        }
    }
}
