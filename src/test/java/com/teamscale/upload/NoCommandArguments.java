package com.teamscale.upload;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Arguments that invoke the teamscale-upload executable without naming a
 * command, e.g. to request the main help screen or to check that an invocation
 * from before the commands were introduced is rejected.
 */
class NoCommandArguments implements UploadArguments {

	private final String[] arguments;

	NoCommandArguments(String... arguments) {
		this.arguments = arguments;
	}

	@Override
	public String[] toCommand(String executable) {
		List<String> command = new ArrayList<>(List.of(executable));
		command.addAll(List.of(arguments));
		return command.toArray(new String[0]);
	}

	@Override
	public File getStdinFile() {
		return null;
	}
}
