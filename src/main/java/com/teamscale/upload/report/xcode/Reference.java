package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedStringDeserializer;

/**
 * Format of an object of type Reference in the XCResult bundle summary output.
 */
public class Reference {

    /**
     * The id of the reference that can be passed to the xcresulttool to print a
     * JSON summary for the referenced object.
     */
    @JsonAdapter(WrappedStringDeserializer.class)
    public final String id;

    public Reference(String id) {
        this.id = id;
    }
}
