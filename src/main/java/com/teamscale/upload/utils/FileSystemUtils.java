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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;

import com.google.common.annotations.VisibleForTesting;

/**
 * File system utilities.
 */
public class FileSystemUtils {

	/** The path to the directory used by Java to store temporary files */
	public static final String TEMP_DIR_PATH = System.getProperty("java.io.tmpdir");
	/**
	 * Unix file path separator
	 */
	public static final char UNIX_SEPARATOR = '/';

	private static final List<String> TAR_FILE_EXTENSIONS = List.of(".tar", ".tar.gz", ".tgz");

	private static final List<String> GZIP_FILE_EXTENSIONS = List.of(".tar.gz", ".tgz");

	/**
	 * Mask for a posix file-mode that states that any execute bit is set (owner, group, or other).
	 * Given a unix-mode int <code>unixMode</code>, <code>(unixMode & UNIX_EXEC_MASK) != 0</code> determines whether
	 * one of the execute permissions is set.
	 * <p>
	 * Underscores are inserted for readability (separating the read-write-execute flag groups).
	 */
	private static final int UNIX_EXEC_MASK = 0b001_001_001;

	/**
	 * Expected result for <code>unixMode & UNIX_EXEC_MASK</code> if only the owner of the file has execute permissions.
	 * <p>
	 * Underscores are inserted for readability (separating the read-write-execute flag groups).
	 */
	private static final int UNIX_EXEC_MASK_OWNER = 0b001_000_000;

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
	 * Extract entries of a ZipFile to a directory when the ZipFile is created
	 * externally. Note that this does not close the ZipFile, so the caller has to
	 * take care of this.
	 * <p>
	 * We use the apache commons ZipArchiveEntry instead of the java standard library
	 * ZipEntry since the apache commons variant allows access to the flags on files in the zip.
	 * In particular executable flags on shell scripts.
	 */
	public static List<String> unzip(ZipFile zip, File targetDirectory) throws IOException {
		Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
		List<String> extractedPaths = new ArrayList<>();

		while (entries.hasMoreElements()) {
			ZipArchiveEntry entry = entries.nextElement();
			if (entry.isDirectory()) {
				continue;
			}
			String fileName = entry.getName();
			try (InputStream entryStream = zip.getInputStream(entry)) {
				File file = new File(targetDirectory, fileName);
				ensureDirectoryExists(file.getParentFile());
				try (FileOutputStream outputStream = new FileOutputStream(file)) {
					copy(entryStream, outputStream);
				}
				adoptUnixExecuteFilePermission(file, entry.getUnixMode());
			}
			extractedPaths.add(fileName);
		}
		return extractedPaths;
	}

	/**
	 * Applies the given posix execute-file permission setting on the given file.
	 * If only the file owner has execute permission, then this limitation is preserved.
	 * <p>
	 * For example, unix mode <code>Integer.toBinaryString(unixMode) == "1000000111101101"</code>
	 * corresponds to <code>-rwxr-xr-x</code> in ls output. Each enabled bit is represented by a 1.
	 * In this case all users (owner/group/other) have execute permissions.
	 *
	 * @param file the file to which we should apply the given execute-bit settings
	 * @param unixMode binary representation of the posix file mode
	 *                    (each bit states enablement/disablement of a flag, order is like in the output of ls).
	 */
	private static void adoptUnixExecuteFilePermission(File file, int unixMode) {
		if (unixMode <= 0) {
			return;
		}
		if ((unixMode & UNIX_EXEC_MASK) != 0) {
			boolean ownerOnly = (unixMode & UNIX_EXEC_MASK) == UNIX_EXEC_MASK_OWNER;
			file.setExecutable(true, ownerOnly);
		}
	}

	/**
	 * Copy an input stream to an output stream. This does <em>not</em> close the
	 * streams.
	 *
	 * @param input
	 *            input stream
	 * @param output
	 *            output stream
	 * @return number of bytes copied
	 * @throws IOException
	 *             if an IO exception occurs.
	 */
	public static int copy(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[1024];
		int size = 0;
		int len;
		while ((len = input.read(buffer)) > 0) {
			output.write(buffer, 0, len);
			size += len;
		}
		return size;
	}
	/**
	 * Checks if a directory exists and is writable. If not it creates the directory
	 * and all necessary parent directories.
	 *
	 * @throws IOException
	 *             if directories couldn't be created.
	 */
	public static void ensureDirectoryExists(File directory) throws IOException {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new IOException("Couldn't create directory: " + directory);
		}
		if (directory.exists() && directory.canWrite()) {
			return;
		}
		// Something is wrong. Either the directory does not exist yet, or it is not
		// writable (yet?). We had a case on a Windows OS where the directory was not
		// writable in a very small fraction of the calls. We assume this was because
		// the directory was not "ready" yet although mkdirs returned.
		Instant start = Instant.now();
		while ((!directory.exists() || !directory.canWrite()) && start.until(Instant.now(), ChronoUnit.MILLIS) < 100) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// just continue
			}
		}
		if (!directory.exists()) {
			throw new IOException("Temp directory " + directory + " could not be created.");
		}
		if (!directory.canWrite()) {
			throw new IOException("Temp directory " + directory + " exists, but is not writable.");
		}
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
	 *             In case 	the file is not located below the directory
	 */
	@VisibleForTesting
	static void ensureFileIsBelowDirectory(File file, File directory) throws IOException {
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
	/**
	 * Recursively delete directories and files. This method ignores the return
	 * value of delete(), i.e. if anything fails, some files might still exist.
	 */
	public static void deleteRecursively(File directory) {

		if (directory == null) {
			throw new IllegalArgumentException("Directory may not be null.");
		}

		File[] filesInDirectory = directory.listFiles();
		if (filesInDirectory == null) {
			if (!directory.exists()) {
				// If filesInDirectory is null, that could have two reasons: Either
				// directory.isInvalid() is true, or there is a low-level IO error that is not
				// wrapped in an exception. We can't precisely distinguish the cases. But
				// directory.exists() checks directory.isInvalid(), and if the directory does
				// not exist, our job is actually done.
				return;
			}
			throw new IllegalArgumentException(directory.getAbsolutePath() + " is not a valid directory.");
		}

		for (File entry : filesInDirectory) {
			if (entry.isDirectory()) {
				deleteRecursively(entry);
			}
			entry.delete();
		}
		directory.delete();
	}
}
