package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.teamscale.upload.report.xcode.deserializers.WrappedArrayDeserializer;

import java.util.Arrays;

/**
 * Data object for the summary JSON output of the XCode xcresulttool executable.
 * An example invocation is {@code xcrun xcresulttool get --path path/to/some.xcresult --format json}.
 */
public class ActionsInvocationRecord {

    /**
     * List of {@link ActionRecord}s.
     */
    @SerializedName("actions")
    @JsonAdapter(WrappedArrayDeserializer.class)
    public final ActionRecord[] actions;

    public ActionsInvocationRecord(ActionRecord[] actions) {
        this.actions = actions;
    }

    public boolean hasCoverageData() {
        return Arrays.stream(actions).anyMatch(ActionRecord::hasCoverageData);
    }
}
