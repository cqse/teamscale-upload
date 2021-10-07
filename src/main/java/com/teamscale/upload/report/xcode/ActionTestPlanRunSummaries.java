package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;

/**
 * Data object for the summary JSON output of the XCode xcresulttool executable for a {@link ActionResult#testsRef}.
 * An example invocation is {@code xcrun xcresulttool get --path path/to/some.xcresult --format json --id abc123}.
 */
public class ActionTestPlanRunSummaries {

    /**
     * The {@link ActionTestPlanRunSummary}s.
     */
    @JsonAdapter(WrappedArrayDeserializer.class)
    public final ActionTestPlanRunSummary[] summaries;

    public ActionTestPlanRunSummaries(ActionTestPlanRunSummary[] summaries) {
        this.summaries = summaries;
    }
}
