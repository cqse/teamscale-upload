package com.teamscale.upload.report.xcode.deserializers;

import com.google.gson.JsonElement;

/**
 * Variant of {@link WrappedValueDeserializerBase} for {@link Double} values.
 */
public class WrappedDoubleDeserializer extends WrappedValueDeserializerBase<Double> {

	@Override
	protected Double convertValue(JsonElement value) {
		return value.getAsDouble();
	}
}
