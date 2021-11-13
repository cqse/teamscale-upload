package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;

/**
 * An object of type ActionTestPlanRunSummary in the XCResult bundle summary output
 * for an {@link ActionResult#testsRef}.
 */
public class ActionTestPlanRunSummary {

    /**
     * The {@link ActionTestableSummary}s.
     */
    @JsonAdapter(WrappedArrayDeserializer.class)
    public final ActionTestableSummary[] testableSummaries;

    public ActionTestPlanRunSummary(ActionTestableSummary[] testableSummaries) {
        this.testableSummaries = testableSummaries;
    }
}
