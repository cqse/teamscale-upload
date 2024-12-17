package com.teamscale.upload.xcode;

import java.io.IOException;

/** Functional interface that represents a conversion function. */
@FunctionalInterface
public interface ConversionFunction<T> {

	/** Runs the conversion function. */
	T run() throws ConversionException, IOException;
}
