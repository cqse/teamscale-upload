package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;

/**
 * The {@link ActionTestableSummary} in an {@link ActionTestPlanRunSummary} object.
 */
public class ActionTestableSummary {

    /**
     * The contained {@link ActionTest}s.
     */
    @JsonAdapter(WrappedArrayDeserializer.class)
    public final ActionTest[] tests;

    public ActionTestableSummary(ActionTest[] tests) {
        this.tests = tests;
    }
}
