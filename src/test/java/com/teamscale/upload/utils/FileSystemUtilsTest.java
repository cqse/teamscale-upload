package com.teamscale.upload.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link FileSystemUtils} */
class FileSystemUtilsTest {

	@ParameterizedTest
	@CsvSource({ "/foo/bar, alice", "/foo/bar, ../bar/alice", "/foo/bar, bob/alice" })
	void testEnsureFileIsBelowDirectoryDoesNotThrow(String directory, String file) {
		Path directoryPath = Path.of(directory);
		Path filePath = directoryPath.resolve(file);

		assertDoesNotThrow(() -> FileSystemUtils.ensureFileIsBelowDirectory(filePath.toFile(), directoryPath.toFile()));
	}

	@ParameterizedTest
	@CsvSource({ "/foo/bar, /alice", "/foo/bar, ../alice", "/foo/bar, ../bob/alice" })
	void testEnsureFileIsBelowDirectoryDoesThrow(String directory, String file) {
		Path directoryPath = Path.of(directory);
		Path filePath = directoryPath.resolve(file);

		assertThrowsExactly(IOException.class,
				() -> FileSystemUtils.ensureFileIsBelowDirectory(filePath.toFile(), directoryPath.toFile()));
	}
}