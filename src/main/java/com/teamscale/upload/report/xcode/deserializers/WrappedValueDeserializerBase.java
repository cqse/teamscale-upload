package com.teamscale.upload.report.xcode.deserializers;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/**
 * Flattens a value object to just it's value. For example
 *
 * <pre>
 * {
 *   "_type": {
 *     "_name": "String"
 *   },
 *   "_value": "foo"
 * }
 * </pre>
 * <p>
 * is the same as just deserializing the value string, bool etc.
 *
 * <pre>
 * "foo"
 * </pre>
 */
public abstract class WrappedValueDeserializerBase<T> implements JsonDeserializer<T> {

    @Override
    public T deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        return convertValue(jsonElement.getAsJsonObject().get("_value"));
    }

    /**
     * Converts the value {@link JsonElement} to the actual value.
     */
    protected abstract T convertValue(JsonElement value);
}
