package com.teamscale.upload.report.xcode;

import com.google.gson.annotations.JsonAdapter;
import com.teamscale.upload.report.xcode.deserializers.WrappedStringDeserializer;

/**
 * An object of type Reference in the XCResult bundle summary output. Used to
 * reference other objects in the XCResult bundle via an id.
 */
public class XCResultObjectIdReference {

	/**
	 * The id of the reference that can be passed to the xcresulttool to export the
	 * object data.
	 */
	@JsonAdapter(WrappedStringDeserializer.class)
	public final String id;

	public XCResultObjectIdReference(String id) {
		this.id = id;
	}
}
