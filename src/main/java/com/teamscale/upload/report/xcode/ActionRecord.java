package com.teamscale.upload.report.xcode;

/**
 * An object of type ActionRecord in the summary JSON output of the XCode xcresulttool executable.
 */
public class ActionRecord {

    /**
     * The {@link ActionResult}.
     */
    public final ActionResult actionResult;

    public ActionRecord(ActionResult actionResult) {
        this.actionResult = actionResult;
    }

    /**
     * Returns true if the {@link #actionResult} indicates that the XCResult bundle contains coverage data.
     */
    public boolean hasCoverageData() {
        return actionResult.coverage != null && actionResult.coverage.hasCoverageData;
    }
}
