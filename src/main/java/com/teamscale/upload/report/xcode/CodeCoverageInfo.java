package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedBooleanDeserializer;

/**
 * Data object for summary of the test coverage information in an {@link ActionResult}.
 */
public class CodeCoverageInfo {

    /** Reference to the  */
    public final Reference archiveRef;

    /**
     * True if the XCResult bundle contains test converage information.
     */
    @JsonAdapter(WrappedBooleanDeserializer.class)
    public final boolean hasCoverageData;

    public CodeCoverageInfo(Reference archiveRef, boolean hasCoverageData) {
        this.archiveRef = archiveRef;
        this.hasCoverageData = hasCoverageData;
    }
}
