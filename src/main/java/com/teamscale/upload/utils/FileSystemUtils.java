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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * File system utilities.
 */
public class FileSystemUtils {

    /**
     * Unix file path separator
     */
    public static final char UNIX_SEPARATOR = '/';

    /**
     * Replace platform dependent separator char with forward slashes to create
     * system-independent paths.
     */
    public static String normalizeSeparators(String path) {
        return path.replace(File.separatorChar, UNIX_SEPARATOR);
    }

    /**
     * Decompresses the contents of a Tar file to the destination folder. The Tar
     * file may also use Gzip but must indicate this with the *.gz extension.
     */
    public static void extractTarArchive(File tarArchive, File destination) throws IOException {
        InputStream inputStream = new FileInputStream(tarArchive);
        if (tarArchive.getName().endsWith(".gz") || tarArchive.getName().endsWith(".tgz")) {
            inputStream = new GzipCompressorInputStream(inputStream);
        }

        validateDestination(destination);

        try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(inputStream)) {
            TarArchiveEntry entry;
            while ((entry = tarArchiveInputStream.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File fileForEntry = new File(destination, entry.getName());
                File parent = fileForEntry.getParentFile();

                mkdirs(parent);

                try (FileOutputStream output = new FileOutputStream(fileForEntry)) {
                    IOUtils.copy(tarArchiveInputStream, output);
                }
            }
        }
    }

    private static void validateDestination(File destination) throws IOException {
        if (!destination.exists()) {
            if (!destination.mkdirs()) {
                throw new IOException("Unable to create temporary directory: " + destination);
            }
        }
        File[] files = destination.listFiles();
        if (files == null || files.length > 0) {
            throw new IOException("Temporary decompressed *.xcresult directory isn't empty: " + destination);
        }
    }

    /**
     * Creates the directory and all parent directories if they don't exist.
     */
    private static void mkdirs(File directory) throws IOException {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Unable to create directory for Tar archive entry: " + directory.getAbsolutePath());
            }
        }
    }

    /**
     * Returns the contents of the {@link InputStream} as a {@link String}.
     */
    public static String getInputAsString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        IOUtils.copy(inputStream, byteArrayOutputStream);
        return byteArrayOutputStream.toString();
    }
}
