/*-------------------------------------------------------------------------+
|                                                                          |
| Copyright 2005-2011 The ConQAT Project                                   |
|                                                                          |
| Licensed under the Apache License, Version 2.0 (the "License");          |
| you may not use this file except in compliance with the License.         |
| You may obtain a copy of the License at                                  |
|                                                                          |
|    http://www.apache.org/licenses/LICENSE-2.0                            |
|                                                                          |
| Unless required by applicable law or agreed to in writing, software      |
| distributed under the License is distributed on an "AS IS" BASIS,        |
| WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. |
| See the License for the specific language governing permissions and      |
| limitations under the License.                                           |
+-------------------------------------------------------------------------*/
package com.teamscale.upload.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;

/**
 * File system utilities.
 */
public class FileSystemUtils {

	/**
	 * Unix file path separator
	 */
	public static final char UNIX_SEPARATOR = '/';

	private static final List<String> TAR_FILE_EXTENSIONS = List.of(".tar", ".tar.gz", ".tgz");

	private static final List<String> GZIP_FILE_EXTENSIONS = List.of(".tar.gz", ".tgz");

	/**
	 * Replace platform dependent separator char with forward slashes to create
	 * system-independent paths.
	 */
	public static String normalizeSeparators(String path) {
		return path.replace(File.separatorChar, UNIX_SEPARATOR);
	}

	/**
	 * Returns true if the given file is a Tar file as indicated by possible file
	 * extensions.
	 */
	public static boolean isTarFile(File file) {
		if (!file.isFile()) {
			return false;
		}

		String fileName = file.getName();
		return TAR_FILE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
	}

	/**
	 * Strips the Tar file extension from the file name and returns the result.
	 */
	public static String stripTarExtension(String fileName) {
		for (String tarFileExtension : TAR_FILE_EXTENSIONS) {
			if (fileName.endsWith(tarFileExtension)) {
				return fileName.substring(0, fileName.length() - tarFileExtension.length());
			}
		}
		return fileName;
	}

	/**
	 * Decompresses the contents of a Tar file to the destination folder. The Tar
	 * file may also use Gzip but must indicate this with the *.tar.gz or *.tgz
	 * extension.
	 */
	public static void extractTarArchive(File tarArchive, File destination) throws IOException {
		InputStream inputStream = new FileInputStream(tarArchive);

		String tarArchiveName = tarArchive.getName();

		if (GZIP_FILE_EXTENSIONS.stream().anyMatch(tarArchiveName::endsWith)) {
			inputStream = new GzipCompressorInputStream(inputStream);
		}

		ensureEmptyDirectory(destination);

		try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(inputStream)) {
			TarArchiveEntry entry;
			while ((entry = tarArchiveInputStream.getNextTarEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				File fileForEntry = new File(destination, entry.getName());
				ensureFileIsBelowDirectory(fileForEntry, destination);

				File parent = fileForEntry.getParentFile();
				mkdirs(parent);

				try (FileOutputStream output = new FileOutputStream(fileForEntry)) {
					IOUtils.copy(tarArchiveInputStream, output);
				}
			}
		}
	}

	/**
	 * Ensures that the file represents an empty directory. Creates the directory if
	 * it doesn't exist yet.
	 *
	 * @throws IOException
	 *             In case directory is not empty or can't be created.
	 */
	private static void ensureEmptyDirectory(File directory) throws IOException {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new IOException("Unable to create directory: " + directory);
		}

		File[] files = directory.listFiles();

		if (files == null) {
			if (!directory.isDirectory()) {
				throw new IOException("Expected empty directory but it's a file instead: " + directory);
			}
			throw new IOException("Unexpected error when listing files in directory: " + directory);
		}
		if (files.length > 0) {
			throw new IOException("Expected directory to be empty: " + directory);
		}
	}

	/**
	 * Ensures that the given file is located under the given directory. Throws an
	 * {@link IOException} if not.
	 *
	 * @throws IOException
	 *             In case the file given as directory is not a directory, or if the
	 *             file is not located below the directory
	 */
	private static void ensureFileIsBelowDirectory(File file, File directory) throws IOException {
		if (!directory.isDirectory()) {
			throw new IOException("Expected directory but it's a file instead: " + directory);
		}

		final Path filePath = file.toPath().toAbsolutePath().normalize();
		final Path directoryPath = directory.toPath().toAbsolutePath().normalize();
		if (!filePath.startsWith(directoryPath)) {
			throw new IOException("Expected file '" + filePath + "' to be below directory '" + directoryPath + "'");
		}
	}

	/**
	 * Returns the contents of the {@link InputStream} as a {@link String}.
	 */
	public static String getInputAsString(InputStream inputStream) throws IOException {
		return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
	}

	/**
	 * Creates the directory and all parent directories if they don't exist.
	 */
	public static void mkdirs(File directory) throws IOException {
		if (!directory.exists()) {
			if (!directory.mkdirs()) {
				throw new IOException(
						"Unable to create directory for Tar archive entry: " + directory.getAbsolutePath());
			}
		}
	}

	/**
	 * Ensures that the file is an empty file. If the file already exist it is
	 * deleted and a new empty one is created.
	 */
	public static void ensureEmptyFile(File file) throws IOException {
		if (file.isDirectory()) {
			throw new IOException("Unable to create empty file because it is a directory: " + file);
		}
		if (file.exists() && !file.delete()) {
			throw new IOException("Unable to delete existing file: " + file);
		}
		if (!file.createNewFile()) {
			throw new IOException("Unable to create file empty file: " + file);
		}
	}
}
