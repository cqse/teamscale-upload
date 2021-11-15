package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedBooleanDeserializer;

/**
 * An object of type CodeCoverageInfo in the XCResult bundle summary output.
 * Represents a summary of the coverage information in the XCResult bundle.
 */
public class CodeCoverageInfo {

    /**
     * Reference to the contained coverage archive. Used to export
     * the coverage archive from the XCResult bundle.
     */
    public final XCResultObjectIdReference archiveRef;

    /**
     * True if the XCResult bundle contains test coverage information.
     */
    @JsonAdapter(WrappedBooleanDeserializer.class)
    public final boolean hasCoverageData;

    public CodeCoverageInfo(XCResultObjectIdReference archiveRef, boolean hasCoverageData) {
        this.archiveRef = archiveRef;
        this.hasCoverageData = hasCoverageData;
    }
}
