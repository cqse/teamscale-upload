package com.teamscale.upload.xcode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.teamscale.upload.utils.FileSystemUtils;

/**
 * Converts a {@link FileSystemUtils#isTarFile(File) tar file} into the
 * {@value ConversionUtils#XCCOV_REPORT_FILE_EXTENSION} format.
 */
/* package */ class TarArchiveConverter extends ConverterBase {

	public TarArchiveConverter(XCodeVersion xcodeVersion, Path workingDirectory) {
		super(xcodeVersion, workingDirectory);
	}

	@Override
	public List<File> convert(File tarFile) throws ConversionException, IOException {
		File xcodeReport = extractTar(tarFile);

		if (ConversionUtils.isXccovArchive(xcodeReport)) {
			File convertedFile = new XccovArchiveConverter(getXcodeVersion(), getWorkingDirectory())
					.convert(xcodeReport);
			return Collections.singletonList(convertedFile);
		} else if (ConversionUtils.isXcresultBundle(xcodeReport)) {
			return new XCResultConverter(getXcodeVersion(), getWorkingDirectory()).convert(xcodeReport);
		}

		throw new ConversionException(
				"Report location must be an existing directory with a name that ends with '.xcresult' or "
						+ "'.xccovarchive'. The directory may be contained in a tar archive indicated by the file "
						+ "extensions '.tar', '.tar.gz' or '.tgz'." + tarFile);
	}

	private File extractTar(File tarFile) throws IOException {
		String tarNameWithoutExtension = FileSystemUtils.stripTarExtension(tarFile.getName());
		File destination = getWorkingDirectory().resolve(tarNameWithoutExtension).toFile();
		FileSystemUtils.extractTarArchive(tarFile, destination);
		return destination;
	}
}
