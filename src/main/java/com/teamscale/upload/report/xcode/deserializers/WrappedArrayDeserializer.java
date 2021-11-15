package com.teamscale.upload.report.xcode.deserializers;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/**
 * Flattens an Array type object in the report during serialization. For example
 *
 * <pre>
 * {
 *   "_type": {
 *     "_name": "Array"
 *   },
 *   "_values": [ v1, v2, v3, ... ]
 * }
 * </pre>
 * <p>
 * is the same as just deserializing this Array like this without a special deserializer
 *
 * <pre>
 * [ v1, v2, v3, ... ]
 * </pre>
 */
public class WrappedArrayDeserializer<T> implements JsonDeserializer<T[]> {

    @Override
    public T[] deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)
            throws JsonParseException {
        JsonArray values = jsonElement.getAsJsonObject().getAsJsonArray("_values");
        return jsonDeserializationContext.deserialize(values, type);
    }
}
