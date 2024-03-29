package com.teamscale.upload.xcode;

/**
 * Data class for the result of a {@link ConversionTask}.
 */
class ConversionResult {

	/**
	 * The source file for which the coverage was converted.
	 */
	final String sourceFile;

	/**
	 * The raw output of the xccov conversion tool.
	 */
	final String result;

	public ConversionResult(String sourceFile, String result) {
		this.sourceFile = sourceFile;
		this.result = result;
	}
}
