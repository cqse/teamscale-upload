package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.teamscale.upload.report.xcode.deserializers.WrappedStringDeserializer;

/**
 * An object of type ActionRecord in the summary JSON output of the XCode
 * xcresulttool executable.
 */
public class ActionRecord {

	/**
	 * The {@link ActionResult}.
	 */
	public final ActionResult actionResult;

	/**
	 * List of {@link ActionRecord}s.
	 */
	@SerializedName("testPlanName")
	@JsonAdapter(WrappedStringDeserializer.class)
	public final String testPlanName;

	public ActionRecord(ActionResult actionResult, String testPlanName) {
		this.actionResult = actionResult;
		this.testPlanName = testPlanName;
	}

	/**
	 * Returns true if the {@link #actionResult} indicates that the XCResult bundle
	 * contains coverage data.
	 */
	public boolean hasCoverageData() {
		return actionResult.coverage != null && actionResult.coverage.hasCoverageData;
	}
}
