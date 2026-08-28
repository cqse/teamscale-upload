package com.teamscale.upload;

import java.io.File;

/**
 * Arguments for an execution of the teamscale-upload executable. Implemented
 * once per command the tool offers.
 */
@FunctionalInterface
public interface UploadArguments {

	/**
	 * Assembles the command that invokes the given teamscale-upload executable.
	 */
	String[] toCommand(String executable);

	/**
	 * The file from which the teamscale-upload executable should draw its stdin, or
	 * null if stdin is not used.
	 */
	default File getStdinFile() {
		return null;
	}
}
