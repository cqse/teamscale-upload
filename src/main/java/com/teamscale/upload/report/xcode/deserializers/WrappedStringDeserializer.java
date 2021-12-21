package com.teamscale.upload.report.xcode.deserializers;

import com.google.gson.JsonElement;

/**
 * Variant of {@link WrappedValueDeserializerBase} for {@link String} values.
 */
public class WrappedStringDeserializer extends WrappedValueDeserializerBase<String> {

	@Override
	protected String convertValue(JsonElement value) {
		return value.getAsString();
	}
}
