package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;

/**
 * The {@link ActionTestPlanRunSummary} in an {@link ActionTestPlanRunSummaries} object.
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
