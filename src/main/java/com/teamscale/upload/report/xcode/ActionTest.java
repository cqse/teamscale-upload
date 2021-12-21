package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.teamscale.upload.report.testwise_coverage.TestInfo;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;
import com.teamscale.upload.report.xcode.deserializers.WrappedDoubleDeserializer;
import com.teamscale.upload.report.xcode.deserializers.WrappedStringDeserializer;
import com.teamscale.upload.utils.LogUtils;

import java.util.Optional;

/**
 * An object of type ActionTest in the XCResult bundle summary output for an
 * {@link ActionResult#testsRef}.
 */
public class ActionTest {

	/**
	 * The test identifier which is in the format of a uniform path. The identifier
	 * of a test always includes the identifier of the parent test as a prefix.
	 */
	@JsonAdapter(WrappedStringDeserializer.class)
	public final String identifier;

	/**
	 * The execution duration in seconds.
	 */
	@JsonAdapter(WrappedDoubleDeserializer.class)
	public final double duration;

	/**
	 * The test status. For possible values see {@link #getTestExecutionResult()}.
	 *
	 * @see #getTestExecutionResult()
	 */
	@JsonAdapter(WrappedStringDeserializer.class)
	public final String testStatus;

	/**
	 * The sub-tests below this {@link ActionTest}. This field is null for
	 * {@link ActionTest}s representing actual test functions/methods and such tests
	 * map directly to the {@link TestInfo} objects without the need to consider
	 * parent {@link ActionTest} objects.
	 */
	@SerializedName("subtests")
	@JsonAdapter(WrappedArrayDeserializer.class)
	public final ActionTest[] subTests;

	public ActionTest(double duration, String identifier, String testStatus, ActionTest[] subTests) {
		this.duration = duration;
		this.identifier = identifier;
		this.testStatus = testStatus;
		this.subTests = subTests;
	}

	/**
	 * Returns the Teamscale ETestExecutionResult value as a String corresponding to
	 * the {@link #testStatus}.
	 */
	public Optional<String> getTestExecutionResult() {
		if (testStatus == null) {
			/*
			 * Can happen if this is not an actual test node but rather some intermediate
			 * node that simply doesn't have any children.
			 */
			return Optional.empty();
		}

		switch (testStatus) {
		case "Success":
		case "Expected Failure":
			return Optional.of("PASSED");
		case "Failure":
			return Optional.of("FAILURE");
		case "Skipped":
			return Optional.of("SKIPPED");
		default:
			LogUtils.warn("Test " + identifier + " in XCResult bundle has unknown test status " + testStatus);
			return Optional.empty();
		}
	}
}
