package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;

/**
 * Root object of type ActionTestPlanRunSummaries in the XCResult bundle summary
 * output for an {@link ActionResult#testsRef}. An example invocation is
 * {@code xcrun xcresulttool get --path path/to/some.xcresult --format json --id sometestrefid}.
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
