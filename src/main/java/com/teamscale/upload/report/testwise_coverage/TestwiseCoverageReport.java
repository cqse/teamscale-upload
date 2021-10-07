package com.teamscale.upload.report.testwise_coverage;

import java.util.List;

/**
 * Container for coverage produced by multiple tests.
 * <p>
 * This class is a DTO used to deserialize testwise coverage reports. Field
 * names and structure may not be changed.
 */
public class TestwiseCoverageReport {

    /**
     * Version number for the testwise coverage report. Defaults to version 1, which
     * represents the old testwise coverage report format. The current version is 2.
     * This field is only present in coverage reports of version 2+.
     */
    public final int version = 2;

    /**
     * The tests contained in the report. They are independent of an executable
     * unit. Present in reports of all versions.
     */
    public final List<TestInfo> tests;

    public TestwiseCoverageReport( List<TestInfo> tests) {
        this.tests = tests;
    }
}
