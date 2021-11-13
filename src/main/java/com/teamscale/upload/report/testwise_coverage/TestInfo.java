package com.teamscale.upload.report.testwise_coverage;

import com.teamscale.upload.report.xcode.ActionTest;

import java.util.Optional;

/**
 * Generic container of all information about a specific test as written to the
 * report.
 * <p>
 * This class is a DTO used to serialize testwise coverage reports. Field
 * names and structure may not be changed.
 */
public class TestInfo {

    /**
     * Unique name of the test case by using a path like hierarchical description,
     * which can be shown in the UI.
     */
    public final String uniformPath;

    /**
     * Duration of the execution in seconds.
     */
    public final double duration;

    /**
     * The actual execution result state.
     */
    public final String result;

    public TestInfo(String uniformPath, double duration, String result) {
        this.uniformPath = uniformPath;
        this.duration = duration;
        this.result = result;
    }

    /**
     * Creates a {@link TestInfo} from the {@link ActionTest}. Returns {@link Optional#empty()} in case
     * the {@link ActionTest} doesn't represent an actual test.
     */
    public static Optional<TestInfo> create(ActionTest actionTest) {
        return actionTest.getTestExecutionResult()
                .map(result -> new TestInfo(actionTest.identifier, actionTest.duration, result));
    }
}
