package com.teamscale.upload.report.xcode;

/**
 * An {@link ActionResult} that summarizes build and test execution steps.
 */
public class ActionResult {

    /**
     * The {@link CodeCoverageInfo}.
     */
    public final CodeCoverageInfo coverage;

    /**
     * The refernce to the test object that can be used to load the
     * corresponding {@link ActionTestPlanRunSummaries}.
     */
    public final Reference testsRef;

    public ActionResult(CodeCoverageInfo coverage, Reference testsRef) {
        this.coverage = coverage;
        this.testsRef = testsRef;
    }
}
