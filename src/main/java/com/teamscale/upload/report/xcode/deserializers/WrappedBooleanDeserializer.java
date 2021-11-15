package com.teamscale.upload.report.xcode.deserializers;

import com.google.gson.JsonElement;

/**
 * Variant of {@link WrappedValueDeserializerBase} for {@link Boolean} values.
 */
public class WrappedBooleanDeserializer extends WrappedValueDeserializerBase<Boolean> {

    @Override
    protected Boolean convertValue(JsonElement value) {
        return value.getAsBoolean();
    }
}
