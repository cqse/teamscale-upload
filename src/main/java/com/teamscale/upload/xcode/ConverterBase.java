package com.teamscale.upload.xcode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base class for class that converts specific file formats into other formats.
 */
/* package */ abstract class ConverterBase<T> {

	/**
	 * The installed and used XCode version. Can be determined by
	 * {@link XCodeVersion#determine()}.
	 *
	 * @implNote The version should be determined by the caller and passed to this
	 *           class (i.e., instead of determining it in this class). This is
	 *           because when multiple {@link XCResultConverter} are created, we
	 *           want to show warnings/errors related to the version determination
	 *           only once
	 */
	private final XCodeVersion xcodeVersion;

	/** The directory where intermediate results can be stored. */
	private final Path workingDirectory;

	/** The directory where the final conversion results are stored. */
	private Path outputDirectory;

	public ConverterBase(XCodeVersion xcodeVersion, Path workingDirectory) {
		this.xcodeVersion = xcodeVersion;
		this.workingDirectory = workingDirectory;
	}

	/** Converts a single file into the expected output format. */
	public abstract T convert(File file) throws ConversionException, IOException;

	/**
	 * Returns the path under which an output file with the given name should be
	 * stored. Does not create the file though.
	 */
	public Path getOutputFilePath(String name) throws IOException {
		if (outputDirectory == null) {
			outputDirectory = Files.createTempDirectory(getWorkingDirectory(), this.getClass().getSimpleName());
		}
		return outputDirectory.resolve(name);
	}

	/** Creates a file where the outputs of the converter can be written to. */
	public File createOutputFile(String name) throws IOException {
		return Files.createFile(getOutputFilePath(name)).toFile();
	}

	public XCodeVersion getXcodeVersion() {
		return xcodeVersion;
	}

	public Path getWorkingDirectory() {
		return workingDirectory;
	}
}
