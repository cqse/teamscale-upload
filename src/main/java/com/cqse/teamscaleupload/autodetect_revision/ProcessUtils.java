package com.cqse.teamscaleupload.autodetect_revision;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProcessUtils {

    private static class CaptureStreamHandler implements ExecuteStreamHandler {

        private final ByteArrayOutputStream stdoutAndStderrStream = new ByteArrayOutputStream();
        private final PumpStreamHandler wrappedHandler = new PumpStreamHandler(stdoutAndStderrStream);

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

    public static ProcessResult run(String command, String... arguments) {
        CommandLine commandLine = new CommandLine(command);
        commandLine.addArguments(arguments);
        DefaultExecutor executor = new DefaultExecutor();
        CaptureStreamHandler handler = new CaptureStreamHandler();
        executor.setStreamHandler(handler);
        executor.setExitValues(null); // don't throw in case of non-zero exit codes
        try {
            int exitCode = executor.execute(commandLine);
            return new ProcessResult(exitCode, handler.getStdOutAndStdErr(), null);
        } catch (IOException e) {
            System.err.println("Tried to run `" + command + " " + String.join(" ", arguments) + "` which failed with an exception");
            e.printStackTrace();
            return new ProcessResult(-1, "", e);
        }
    }

}
