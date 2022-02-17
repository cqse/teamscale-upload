package com.teamscale.upload.report.xcode;

/**
 * An object of type ActionResult in the summary JSON output of the XCode
 * xcresulttool executable.
 */
public class ActionResult {

	/**
	 * The {@link CodeCoverageInfo}.
	 */
	public final CodeCoverageInfo coverage;

	public ActionResult(CodeCoverageInfo coverage) {
		this.coverage = coverage;
	}
}
