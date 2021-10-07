package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.teamscale.upload.report.testwise_coverage.TestInfo;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;
import com.teamscale.upload.report.xcode.deserializers.WrappedDoubleDeserializer;
import com.teamscale.upload.report.xcode.deserializers.WrappedStringDeserializer;

/**
 * Format of an object of type ActionTest in the XCResult bundle summary output for a test reference.
 */
public class ActionTest {

    /**
     * The test identifier which is in the format of a uniform path. The identifier of a test
     * always includes the identifier of the parent test as a prefix.
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
     * The sub tests below this {@link ActionTest}s. This field is null for {@link ActionTest}s
     * representing actual test functions/methods and such tests map directly to the {@link TestInfo}
     * objects without the need to consider parent {@link ActionTest} objects.
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
     * Returns the Teamscale ETestExecutionResult value as a String corresponding to the {@link #testStatus}.
     */
    public String getTestExecutionResult() {
        switch (testStatus) {
            case "Success":
            case "Expected Failure":
                return "PASSED";
            case "Failure":
                return "FAILURE";
            case "Skipped":
                return "SKIPPED";
            default:
                throw new AssertionError(String.format("Unknown test status %s for test %s.", testStatus, identifier));
        }
    }
}
