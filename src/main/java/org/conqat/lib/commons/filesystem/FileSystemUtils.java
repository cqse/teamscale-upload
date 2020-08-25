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
package org.conqat.lib.commons.filesystem;

import org.conqat.lib.commons.collections.CollectionUtils;
import org.conqat.lib.commons.string.StringUtils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * File system utilities.
 */
public class FileSystemUtils {

    /**
     * Encoding for UTF-8.
     */
    public static final String UTF8_ENCODING = StandardCharsets.UTF_8.name();

    /**
     * The path to the directory used by Java to store temporary files
     */
    public static final String TEMP_DIR_PATH = System.getProperty("java.io.tmpdir");

    /**
     * Unix file path separator
     */
    public static final char UNIX_SEPARATOR = '/';

    /**
     * Windows file path separator
     */
    public static final char WINDOWS_SEPARATOR = '\\';

    /**
     * String containing the unit letters for units in the metric system (K for
     * kilo, M for mega, ...). Ordered by their power value (the order is
     * important).
     */
    public static final String METRIC_SYSTEM_UNITS = "KMGTPEZY";

    /**
     * Pattern matching the start of the data-size unit in a data-size string (the
     * first non-space char not belonging to the numeric value).
     */
    private static final Pattern DATA_SIZE_UNIT_START_PATTERN = Pattern.compile("[^\\d\\s.,]");


    /**
     * Copy an input stream to an output stream. This does <em>not</em> close the
     * streams.
     *
     * @param input  input stream
     * @param output output stream
     * @return number of bytes copied
     * @throws IOException if an IO exception occurs.
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
     * Copy a file. This creates all necessary directories.
     */
    @SuppressWarnings("resource")
    public static void copyFile(File sourceFile, File targetFile) throws IOException {

        if (sourceFile.getAbsoluteFile().equals(targetFile.getAbsoluteFile())) {
            throw new IOException("Can not copy file onto itself: " + sourceFile);
        }

        ensureParentDirectoryExists(targetFile);

        FileChannel sourceChannel = null;
        FileChannel targetChannel = null;
        try {
            sourceChannel = new FileInputStream(sourceFile).getChannel();
            targetChannel = new FileOutputStream(targetFile).getChannel();
            sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);
        } finally {
            close(sourceChannel);
            close(targetChannel);
        }
    }

    /**
     * Copy a file. This creates all necessary directories.
     */
    public static void copyFile(String sourceFilename, String targetFilename) throws IOException {
        copyFile(new File(sourceFilename), new File(targetFilename));
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

    /**
     * Deletes the given file and throws an exception if this fails.
     *
     * @see File#delete()
     */
    public static void deleteFile(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete " + file);
        }
    }

    /**
     * Renames the given file and throws an exception if this fails.
     *
     * @see File#renameTo(File)
     */
    public static void renameFileTo(File file, File dest) throws IOException {
        if (!file.renameTo(dest)) {
            throw new IOException("Could not rename " + file + " to " + dest);
        }
    }

    /**
     * Creates a directory and throws an exception if this fails.
     *
     * @see File#mkdir()
     */
    public static void mkdir(File dir) throws IOException {
        if (!dir.mkdir()) {
            throw new IOException("Could not create directory " + dir);
        }
    }

    /**
     * Creates a directory and all required parent directories. Throws an exception
     * if this fails.
     *
     * @see File#mkdirs()
     */
    public static void mkdirs(File dir) throws IOException {
        if (dir.exists() && dir.isDirectory()) {
            // mkdirs will return false if the directory already exists, but in
            // that case we don't want to throw an exception
            return;
        }
        if (!dir.mkdirs()) {
            throw new IOException("Could not create directory " + dir);
        }
    }

    /**
     * Checks if a directory exists. If not it creates the directory and all
     * necessary parent directories.
     *
     * @throws IOException if directories couldn't be created.
     */
    public static void ensureDirectoryExists(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Couldn't create directory: " + directory);
        }
    }

    /**
     * Checks if the parent directory of a file exists. If not it creates the
     * directory and all necessary parent directories.
     *
     * @throws IOException if directories couldn't be created.
     */
    public static void ensureParentDirectoryExists(File file) throws IOException {
        ensureDirectoryExists(file.getCanonicalFile().getParentFile());
    }


    /**
     * Returns the parent path within the jar for a class file url. E.g. for the URL
     * "jar:file:/path/to/file.jar!/sub/folder/File.class" the method returns
     * "sub/folder/". If the url does already point to a directory it returns the
     * path of this directory.
     */
    private static String getJarUrlParentDirectoryPrefix(URL baseUrl) {
        // in JAR URLs we can rely on the separator being a slash
        String parentPath = StringUtils.getLastPart(baseUrl.getPath(), '!');
        parentPath = StringUtils.stripPrefix(parentPath, "/");
        if (parentPath.endsWith(".class")) {
            parentPath = StringUtils.stripSuffix(parentPath, StringUtils.getLastPart(parentPath, UNIX_SEPARATOR));
        } else {
            parentPath = StringUtils.ensureEndsWith(parentPath, String.valueOf(UNIX_SEPARATOR));
        }
        return parentPath;
    }


    /**
     * Returns whether the jar entry should be returned when searching for files
     * contained in the given path.
     */
    private static boolean shouldBeContainedInResult(JarEntry entry, String path, boolean recursive) {
        if (entry.isDirectory()) {
            return false;
        }
        String simpleName = StringUtils.getLastPart(entry.getName(), UNIX_SEPARATOR);
        String entryPath = StringUtils.stripSuffix(entry.getName(), simpleName);

        return !recursive && entryPath.equals(path) || (recursive && entryPath.startsWith(path));
    }




    /**
     * Returns the extension of the file.
     *
     * @return File extension, i.e. "java" for "FileSystemUtils.java", or
     * <code>null</code>, if the file has no extension (i.e. if a filename
     * contains no '.'), returns the empty string if the '.' is the
     * filename's last character.
     */
    public static String getFileExtension(File file) {
        return getFileExtension(file.getName());
    }

    /**
     * Returns the extension of the file at the given path.
     */
    public static String getFileExtension(String path) {
        int posLastDot = path.lastIndexOf('.');
        if (posLastDot < 0) {
            return null;
        }
        return path.substring(posLastDot + 1);
    }

    /**
     * Returns the name of the given file without extension. Example:
     * '/home/joe/data.dat' -> 'data'.
     */
    public static String getFilenameWithoutExtension(File file) {
        return getFilenameWithoutExtension(file.getName());
    }

    /**
     * Returns the name of the given file without extension. Example: 'data.dat' ->
     * 'data'.
     */
    public static String getFilenameWithoutExtension(String fileName) {
        return StringUtils.removeLastPart(fileName, '.');
    }

    /**
     * Returns the last path segment (i.e. file name or folder name) of a file path.
     */
    public static String getLastPathSegment(String filePath) {
        String[] split = getPathSegments(filePath);
        return split[split.length - 1];
    }

    /**
     * Returns the segments of a path.
     */
    public static String[] getPathSegments(String filePath) {
        return FileSystemUtils.normalizeSeparators(filePath).split(String.valueOf(UNIX_SEPARATOR));
    }

    /**
     * Constructs a file from a base file by appending several path elements.
     * Insertion of separators is performed automatically as needed. This is similar
     * to the constructor {@link File#File(File, String)} but allows to define
     * multiple child levels.
     *
     * @param pathElements list of elements. If this is empty, the parent is returned.
     * @return the new file.
     */
    public static File newFile(File parentFile, String... pathElements) {
        if (pathElements.length == 0) {
            return parentFile;
        }

        File child = new File(parentFile, pathElements[0]);

        String[] remainingElements = new String[pathElements.length - 1];
        System.arraycopy(pathElements, 1, remainingElements, 0, pathElements.length - 1);
        return newFile(child, remainingElements);
    }


    /**
     * Read file content into a byte array.
     */
    public static byte[] readFileBinary(String filePath) throws IOException {
        return readFileBinary(new File(filePath));
    }

    /**
     * Read file content into a byte array.
     */
    public static byte[] readFileBinary(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);

        byte[] buffer = new byte[(int) file.length()];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        FileChannel channel = in.getChannel();
        try {
            int readSum = 0;
            while (readSum < buffer.length) {
                int read = channel.read(byteBuffer);
                if (read < 0) {
                    throw new IOException("Reached EOF before entire file could be read!");
                }
                readSum += read;
            }
        } finally {
            close(channel);
            close(in);
        }

        return buffer;
    }





    /**
     * Extract entries of a zip file input stream to a directory. The input stream
     * is automatically closed by this method.
     */
    public static List<String> unzip(InputStream inputStream, File targetDirectory) throws IOException {
        List<String> extractedPaths = new ArrayList<>();
        try (ZipInputStream zipStream = new ZipInputStream(inputStream)) {
            while (true) {
                ZipEntry entry = zipStream.getNextEntry();
                if (entry == null) {
                    break;
                } else if (entry.isDirectory()) {
                    continue;
                }
                String fileName = entry.getName();

                File file = new File(targetDirectory, fileName);
                ensureParentDirectoryExists(file);

                try (OutputStream targetStream = new FileOutputStream(file)) {
                    copy(zipStream, targetStream);
                }
                extractedPaths.add(fileName);
            }
        }
        return extractedPaths;
    }

    /**
     * Write string to a file with the default encoding. This ensures all
     * directories exist.
     */
    public static void writeFile(File file, String content) throws IOException {
        writeFile(file, content, Charset.defaultCharset().name());
    }

    /**
     * Writes the given collection of String as lines into the specified file. This
     * method uses \n as a line separator.
     */
    public static void writeLines(File file, Collection<String> lines) throws IOException {
        writeFile(file, StringUtils.concat(lines, "\n"));
    }

    /**
     * Write string to a file with UTF8 encoding. This ensures all directories
     * exist.
     */
    public static void writeFileUTF8(File file, String content) throws IOException {
        writeFile(file, content, UTF8_ENCODING);
    }

    /**
     * Write string to a file. This ensures all directories exist.
     */
    public static void writeFile(File file, String content, String encoding) throws IOException {
        ensureParentDirectoryExists(file);
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), encoding);
            writer.write(content);
        } finally {
            FileSystemUtils.close(writer);
        }
    }


    /**
     * Writes the given bytes to the given file. Directories are created as needed.
     * The file is closed after writing.
     */
    public static void writeFileBinary(File file, byte[] bytes) throws IOException {
        ensureParentDirectoryExists(file);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(bytes);
        } finally {
            FileSystemUtils.close(out);
        }
    }

    /**
     * Finds all files and directories contained in the given directory and all
     * subdirectories matching the filter provided and put them into the result
     * collection. The given directory itself is not included in the result.
     * <p>
     * This method knows nothing about (symbolic and hard) links, so care should be
     * taken when traversing directories containing recursive links.
     *
     * @param directory the directory to start the search from.
     * @param result    the collection to add to all files found.
     * @param filter    the filter used to determine whether the result should be
     *                  included. If the filter is null, all files and directories are
     *                  included.
     */
    private static void listFilesRecursively(File directory, Collection<File> result, FileFilter filter) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                listFilesRecursively(file, result, filter);
            }
            if (filter == null || filter.accept(file)) {
                result.add(file);
            }
        }
    }


    /**
     * Read input stream into raw byte array.
     */
    public static byte[] readStreamBinary(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(input, out);
        return out.toByteArray();
    }


    /**
     * Reads properties from a properties file.
     */
    public static Properties readProperties(File propertiesFile) throws IOException {
        return readProperties(() -> new FileInputStream(propertiesFile));
    }


    /**
     * Reads properties from a properties stream.
     */
    public static Properties readProperties(
            CollectionUtils.SupplierWithException<? extends InputStream, IOException> streamSupplier)
            throws IOException {
        try (InputStream stream = streamSupplier.get()) {
            Properties props = new Properties();
            props.load(stream);
            return props;
        }
    }

    /**
     * Determines the root directory from a collection of files. The root directory
     * is the lowest common ancestor directory of the files in the directory tree.
     * <p>
     * This method does not require the input files to exist.
     *
     * @param files
     *            Collection of files for which root directory gets determined. This
     *            collection is required to contain at least 2 files. If it does
     *            not, an AssertionError is thrown.
     *
     * @throws AssertionError
     *             If less than two different files are provided whereas fully
     *             qualified canonical names are used for comparison.
     *
     * @throws IOException
     *             Since canonical paths are used for determination of the common
     *             root, and {@link File#getCanonicalPath()} can throw
     *             {@link IOException}s.
     *
     * @return Root directory, or null, if the files do not have a common root
     *         directory.
     */

    /**
     * Transparently creates a stream for decompression if the provided stream is
     * compressed. Otherwise the stream is just handed through. Currently the
     * following compression methods are supported:
     * <ul>
     * <li>GZIP via {@link GZIPInputStream}</li>
     * </ul>
     */
    public static InputStream autoDecompressStream(InputStream in) throws IOException {
        if (!in.markSupported()) {
            in = new BufferedInputStream(in);
        }
        in.mark(2);
        // check first two bytes for GZIP header
        boolean isGZIP = (in.read() & 0xff | (in.read() & 0xff) << 8) == GZIPInputStream.GZIP_MAGIC;
        in.reset();
        if (isGZIP) {
            return new GZIPInputStream(in);
        }
        return in;
    }

    /**
     * Closes the given ZIP file quietly, i.e. ignoring a potential IOException.
     * Additionally it is <code>null</code> safe.
     */
    public static void close(ZipFile zipFile) {
        if (zipFile == null) {
            return;
        }
        try {
            zipFile.close();
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * This method can be used to simplify the typical <code>finally</code> -block
     * of code dealing with streams and readers/writers. It checks if the provided
     * closeable is <code>null</code>. If not it closes it. If an exception is
     * thrown during the close operation it will be ignored.
     */
    public static void close(Closeable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }


    /**
     * Replace platform dependent separator char with forward slashes to create
     * system-independent paths.
     */
    public static String normalizeSeparators(String path) {
        return path.replace(File.separatorChar, UNIX_SEPARATOR);
    }

    /**
     * @return a path normalized by replacing all occurrences of Windows back-slash
     * separators (if present) with unix forward-slash separators. This is
     * in contrast to {@link #normalizeSeparators(String)} that replaces all
     * platform-dependent separators.
     */
    public static String normalizeSeparatorsPlatformIndependently(String path) {
        if (path.contains(String.valueOf(WINDOWS_SEPARATOR)) && !path.contains(String.valueOf(UNIX_SEPARATOR))) {
            return path.replace(WINDOWS_SEPARATOR, UNIX_SEPARATOR);
        }

        return path;
    }


    /**
     * Often file URLs are created the wrong way, i.e. without proper escaping
     * characters invalid in URLs. Unfortunately, the URL class allows this and the
     * Eclipse framework does it. See
     * http://weblogs.java.net/blog/2007/04/25/how-convert-javaneturl-javaiofile for
     * details.
     * <p>
     * This method attempts to fix this problem and create a file from it.
     *
     * @throws AssertionError if cleaning up fails.
     */
    private static File fromURL(String url) {

        // We cannot simply encode the URL this also encodes slashes and other
        // stuff. As a result, the file constructor throws an exception. As a
        // simple heuristic, we only fix the spaces.
        // The other route to go would be manually stripping of "file:" and
        // simply creating a file. However, this does not work if the URL was
        // created properly and contains URL escapes.

        url = url.replace(StringUtils.SPACE, "%20");
        try {
            return new File(new URI(url));
        } catch (URISyntaxException e) {
            throw new AssertionError("The assumption is that this method is capable of "
                    + "working with non-standard-compliant URLs, too. " + "Apparently it is not. Invalid URL: " + url
                    + ". Ex: " + e.getMessage(), e);
        }
    }

    /**
     * Returns whether a filename represents an absolute path.
     * <p>
     * This method returns the same result, independent on which operating system it
     * gets executed. In contrast, the behavior of {@link File#isAbsolute()} is
     * operating system specific.
     */
    public static boolean isAbsolutePath(String filename) {
        // Unix and MacOS: absolute path starts with slash or user home
        if (filename.startsWith("/") || filename.startsWith("~")) {
            return true;
        }
        // Windows and OS/2: absolute path start with letter and colon
        if (filename.length() > 2 && Character.isLetter(filename.charAt(0)) && filename.charAt(1) == ':') {
            return true;
        }
        // UNC paths (aka network shares): start with double backslash
        if (filename.startsWith("\\\\")) {
            return true;
        }

        return false;
    }

    /**
     * Reads bytes of data from the input stream into an array of bytes until the
     * array is full. This method blocks until input data is available, end of file
     * is detected, or an exception is thrown.
     * <p>
     * The reason for this method is that {@link InputStream#read(byte[])} may read
     * less than the requested number of bytes, while this method ensures the data
     * is complete.
     *
     * @param in   the stream to read from.
     * @param data the stream to read from.
     * @throws IOException  if reading the underlying stream causes an exception.
     * @throws EOFException if the end of file was reached before the requested data was
     *                      read.
     */
    public static void safeRead(InputStream in, byte[] data) throws IOException, EOFException {
        safeRead(in, data, 0, data.length);
    }

    /**
     * Reads <code>length</code> bytes of data from the input stream into an array
     * of bytes and stores it at position <code>offset</code>. This method blocks
     * until input data is available, end of file is detected, or an exception is
     * thrown.
     * <p>
     * The reason for this method is that {@link InputStream#read(byte[], int, int)}
     * may read less than the requested number of bytes, while this method ensures
     * the data is complete.
     *
     * @param in     the stream to read from.
     * @param data   the stream to read from.
     * @param offset the offset in the array where the first read byte is stored.
     * @param length the length of data read.
     * @throws IOException  if reading the underlying stream causes an exception.
     * @throws EOFException if the end of file was reached before the requested data was
     *                      read.
     */
    public static void safeRead(InputStream in, byte[] data, int offset, int length) throws IOException, EOFException {
        while (length > 0) {
            int read = in.read(data, offset, length);
            if (read < 0) {
                throw new EOFException("Reached end of file before completing read.");
            }
            offset += read;
            length -= read;
        }
    }

    /**
     * Obtains the system's temporary directory
     */
    public static File getTmpDir() {
        return new File(TEMP_DIR_PATH);
    }

    /**
     * Obtains the current user's home directory
     */
    public static File getUserHomeDir() {
        return new File(System.getProperty("user.home"));
    }

    /**
     * Obtains the current working directory. This is usually the directory in which
     * the current Java process was started.
     */
    public static File getWorkspaceDir() {
        if (Boolean.getBoolean("com.teamscale.dev-mode") || isJUnitTest()) {
            return getTmpDir();
        }
        return new File(System.getProperty("user.dir"));
    }

    private static boolean isJUnitTest() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether two files have the same content.
     */
    public static boolean contentsEqual(File file1, File file2) throws IOException {
        byte[] content1 = readFileBinary(file1);
        byte[] content2 = readFileBinary(file2);
        return Arrays.equals(content1, content2);
    }

    /**
     * Opens an {@link InputStream} for the entry with the given name in the given
     * JAR file
     */
    public static InputStream openJarFileEntry(JarFile jarFile, String entryName) throws IOException {
        JarEntry entry = jarFile.getJarEntry(entryName);
        if (entry == null) {
            throw new IOException("No entry '" + entryName + "' found in JAR file '" + jarFile + "'");
        }
        return jarFile.getInputStream(entry);
    }

    /**
     * Returns whether the given file is non-null, a plain file and is readable.
     */
    public static boolean isReadableFile(File... files) {
        for (File file : files) {
            if (file == null || !file.exists() || !file.isFile() || !file.canRead()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Concatenates all path parts into a single path with normalized separators.
     */
    public static String concatenatePaths(String firstParent, String... paths) {
        return FileSystemUtils.normalizeSeparators(Paths.get(firstParent, paths).toString());
    }

    /**
     * Removes the given path if it is an empty directory and recursively parent
     * directories if the only child was deleted.
     * <p>
     * If the given path points to file which does not exist, the parent directory
     * of that file is deleted.
     *
     * @throws IOException if an I/O error during deletion occurs.
     */
    public static void recursivelyRemoveDirectoryIfEmpty(File path) throws IOException {
        String[] children = path.list();
        if (children == null) {
            // path either points to a plain file or to a non-existent
            // path. In the first case, nothing should be done otherwise
            // deletion should continue with the parent path.
            if (path.exists()) {
                return;
            }
        } else if (children.length == 0) {
            deleteFile(path);
        } else {
            return;
        }
        recursivelyRemoveDirectoryIfEmpty(path.getParentFile());
    }

    /**
     * Returns a file that does not exist in the same directory of the given file.
     * It either returns the passed file (if not exiting) or appends a number
     * between the filename and the extension to ensure uniqueness, e.g.
     * path/to/file_1.ext if path/to/file.ext is passed but this file already
     * exists. The number is incremented until the file does not exist.
     */
    public static File getNonExistingFile(File file) {
        if (!file.exists()) {
            return file;
        }

        String extensionlessName = getFilenameWithoutExtension(file);
        int suffix = 0;
        String extension = FileSystemUtils.getFileExtension(file);

        do {
            file = new File(file.getParentFile(), extensionlessName + "_" + ++suffix + "." + extension);
        } while (file.exists());

        return file;
    }

    /**
     * Converts the given human readable data size to the corresponding number of
     * bytes. For example "1 KB" is converted to 1024. Also supports Si units ("1
     * KiB" is converted to 1000).
     * <p>
     * Commas are ignored and can be used as thousands separator. A dot is the
     * decimal separator. ("1.2KiB" is converted to 1200).
     * <p>
     * Method implementation based on stackoverflow
     * https://stackoverflow.com/a/45860167
     */
    public static long parseDataSize(String dataSize) {
        String dataSizeWithoutComma = dataSize.replaceAll(",", "");
        int unitBeginIndex = StringUtils.indexOfMatch(dataSizeWithoutComma, DATA_SIZE_UNIT_START_PATTERN);
        if (unitBeginIndex == -1) {
            return Long.parseLong(dataSizeWithoutComma);
        }
        double rawDataSize = Double.parseDouble(dataSizeWithoutComma.substring(0, unitBeginIndex));
        String unitString = dataSizeWithoutComma.substring(unitBeginIndex);
        int unitChar = unitString.charAt(0);
        int power = METRIC_SYSTEM_UNITS.indexOf(unitChar) + 1;
        boolean isSi = unitBeginIndex != -1 && unitString.length() >= 2 && unitString.charAt(1) == 'i';

        int factor = 1024;
        if (isSi) {
            factor = 1000;
            if (StringUtils.stripSuffix(unitString, "B").length() != 2) {
                throw new NumberFormatException("Malformed data size: " + dataSizeWithoutComma);
            }
        } else if (power == 0) {
            if (StringUtils.stripSuffix(unitString, "B").length() != 0) {
                throw new NumberFormatException("Malformed data size: " + dataSizeWithoutComma);
            }
        } else {
            if (StringUtils.stripSuffix(unitString, "B").length() != 1) {
                throw new NumberFormatException("Malformed data size: " + dataSizeWithoutComma);
            }
        }
        return (long) (rawDataSize * Math.pow(factor, power));
    }

    /**
     * Determines the last modified timestamp in a platform agnostic way.
     */
    public static long getLastModifiedTimestamp(File file) throws IOException {
        return Files.getLastModifiedTime(Paths.get(file.toURI())).toMillis();
    }

    /**
     * Returns a safe filename that can be used for downloads. Replaces everything
     * that is not a letter or number with "-"
     */
    public static String toSafeFilename(String name) {
        name = name.replaceAll("\\W+", "-");
        name = name.replaceAll("[-_]+", "-");
        return name;
    }



    /**
     * Replaces the file name of the given path with the given new extension.
     * Returns the newFileName if the file denoted by the uniform path does not
     * contain a '/'. This method assumes that folders are separated by '/' (uniform
     * paths).
     * <p>
     * Examples:
     * <ul>
     * <li><code>replaceFilePathFilenameWith("xx", "yy")</code> returns
     * <code>"yy"</code></li>
     * <li><code>replaceFilePathFilenameWith("xx/zz", "yy")</code> returns *
     * <code>"xx/yy"</code></li>
     * <li><code>replaceFilePathFilenameWith("xx/zz/", "yy")</code> returns *
     * <code>"xx/zz/yy"</code></li>
     * <li><code>replaceFilePathFilenameWith("", "yy")</code> returns *
     * <code>"yy"</code></li>
     * </ul>
     */
    public static String replaceFilePathFilenameWith(String uniformPath, String newFileName) {
        int folderSepIndex = uniformPath.lastIndexOf('/');

        if (uniformPath.endsWith("/")) {
            return uniformPath + newFileName;
        } else if (folderSepIndex == -1) {
            return newFileName;
        }
        return uniformPath.substring(0, folderSepIndex) + "/" + newFileName;
    }

}
