package com.teamscale.upload.xcode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.teamscale.upload.autodetect_revision.ProcessUtils;
import com.teamscale.upload.autodetect_revision.ProcessUtils.ProcessResult;
import com.teamscale.upload.report.xcode.ActionRecord;
import com.teamscale.upload.report.xcode.ActionsInvocationRecord;
import com.teamscale.upload.utils.LogUtils;

/**
 * Converts an {@value ConversionUtils#XCRESULT_FILE_EXTENSION} file into the
 * {@value ConversionUtils#XCCOV_REPORT_FILE_EXTENSION} format.
 */
/* package */ class XcresultConverter extends ConverterBase<List<File>> {

	public XcresultConverter(XcodeVersion xcodeVersion, Path workingDirectory) {
		super(xcodeVersion, workingDirectory);
	}

	@Override
	public List<File> convert(File xcresult) throws ConversionException, IOException {
		ActionsInvocationRecord actionsInvocationRecord = readActionsInvocationRecord(xcresult);
		if (!actionsInvocationRecord.hasCoverageData()) {
			LogUtils.warn("XCResult bundle doesn't contain any coverage data: " + xcresult);
			return Collections.emptyList();
		}

		List<File> xccovArchives = convertToXccovArchives(xcresult, actionsInvocationRecord);
		List<File> convertedReports = new ArrayList<>();
		for (File xccovArchive : xccovArchives) {
			File convertedReport = new XccovArchiveConverter(getXcodeVersion(), getWorkingDirectory())
					.convert(xccovArchive);
			convertedReports.add(convertedReport);
		}
		return convertedReports;
	}

	private List<File> convertToXccovArchives(File xcresult, ActionsInvocationRecord actionsInvocationRecord)
			throws IOException, ConversionException {
		List<File> xccovArchives = new ArrayList<>(actionsInvocationRecord.actions.length);

		for (int i = 0; i < actionsInvocationRecord.actions.length; i++) {
			ActionRecord action = actionsInvocationRecord.actions[i];
			if (action == null || action.actionResult == null || action.actionResult.coverage == null
					|| action.actionResult.coverage.archiveRef == null
					|| action.actionResult.coverage.archiveRef.id == null) {
				continue;
			}
			StringBuilder fileName = new StringBuilder();
			fileName.append(xcresult.getName()).append(".").append(i);
			String testPlanName = action.testPlanName;
			if (testPlanName != null) {
				// Test plan name seems to not always be part of the data, hence, only add
				// append it to the name if present
				fileName.append(".").append(testPlanName);
			}
			fileName.append(ConversionUtils.XCCOV_ARCHIVE_FILE_EXTENSION);
			Path xccovArchive = getOutputFilePath(fileName.toString());

			String archiveRef = action.actionResult.coverage.archiveRef.id;
			List<String> command = new ArrayList<>();
			Collections.addAll(command, "xcrun", "xcresulttool", "export", "--type", "directory", "--path",
					xcresult.getAbsolutePath(), "--id", archiveRef, "--output-path",
					xccovArchive.toAbsolutePath().toString());
			if (getXcodeVersion().major >= 16) {
				// Starting with Xcode 16 this command is marked as deprecated and will fail if
				// ran without the legacy flag
				// see TS-40724 for more information
				command.add("--legacy");
			}

			ProcessResult result = ProcessUtils.run(command.toArray(new String[0]));
			if (!result.wasSuccessful()) {
				throw ConversionException.withProcessResult(
						"Could not convert report to " + ConversionUtils.XCCOV_ARCHIVE_FILE_EXTENSION, result);
			}
			xccovArchives.add(xccovArchive.toFile());
		}

		return xccovArchives;
	}

	private ActionsInvocationRecord readActionsInvocationRecord(File reportDirectory) throws ConversionException {
		List<String> command = new ArrayList<>();
		Collections.addAll(command, "xcrun", "xcresulttool", "get", "--path", reportDirectory.getAbsolutePath(),
				"--format", "json");
		if (getXcodeVersion().major >= 16) {
			// Starting with Xcode 16 this command is marked as deprecated and will fail if
			// ran without the legacy flag
			// see TS-40724 for more information
			command.add("--legacy");
		}

		ProcessResult result = ProcessUtils.run(command.toArray(new String[0]));
		if (!result.wasSuccessful()) {
			throw ConversionException
					.withProcessResult("Error while obtaining ActionInvocationsRecord from XCResult archive "
							+ reportDirectory.getAbsolutePath(), result);
		}
		String actionsInvocationRecordJson = result.output;
		return new Gson().fromJson(actionsInvocationRecordJson, ActionsInvocationRecord.class);
	}
}
