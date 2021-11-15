package com.teamscale.upload.report.xcode;

/**
 * An object of type ActionResult in the summary JSON output of the XCode xcresulttool executable.
 */
public class ActionResult {

    /**
     * The {@link CodeCoverageInfo}.
     */
    public final CodeCoverageInfo coverage;

    /**
     * The reference to the test object that can be used to load the
     * corresponding {@link ActionTestPlanRunSummaries}.
     */
    public final XCResultObjectIdReference testsRef;

    public ActionResult(CodeCoverageInfo coverage, XCResultObjectIdReference testsRef) {
        this.coverage = coverage;
        this.testsRef = testsRef;
    }
}
