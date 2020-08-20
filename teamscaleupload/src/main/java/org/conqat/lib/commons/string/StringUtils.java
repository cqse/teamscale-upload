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
package org.conqat.lib.commons.string;

import org.conqat.lib.commons.collections.CollectionUtils;
import org.conqat.lib.commons.collections.Pair;
import org.conqat.lib.commons.filesystem.FileSystemUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A utility class providing some advanced string functionality.
 */
public class StringUtils {

    /**
     * Matches all whitespace at the beginning of each line.
     * <p>
     * We deliberately don't use "\\s" here because this also matches new lines.
     * Instead we use "\\p{Zs}" which matches all unicode horizontal whitespace
     * characters.
     */
    private static final Pattern LEADING_WHITESPACE_PATTERN = Pattern.compile("^[\\t\\p{Zs}]+", Pattern.MULTILINE);

    /**
     * Line separator of the current platform.
     */
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /**
     * Line feed ("\n"), platform independent.
     */
    public static final String LINE_FEED = "\n";

    /**
     * The empty string. Use StringUtils.EMPTY_STRING instead of "" (our Java coding
     * guidelines)
     *
     * <ol>
     * <li>Use <code>StringUtils.EMPTY_STRING</code> instead of an empty string
     * literal (<code>""</code>) to make it clear that this is intentionally empty.
     * Using "" may leave the reader in doubt whether this is something unfinished
     * (e.g. "" used temporally until something meaningful is filled in) or
     * intentionally the empty String. Making it explicit removes this problem)</li>
     * <li>On most JVMs, using "" allocates a new String object per class in which
     * it used. Using EMPTY_STRING re-uses a single object and safes a few bytes.
     * Note</li>
     * <li>Makes " " and "" better distinguishable.</li>
     * </ol>
     */
    public static final String EMPTY_STRING = "";

    /**
     * A space.
     */
    public static final String SPACE = " ";

    /**
     * A space character.
     */
    public static final char SPACE_CHAR = ' ';

    /**
     * A tab character.
     */
    public static final String TAB = "\t";

    /**
     * Two spaces.
     */
    public static final String TWO_SPACES = "  ";

    /**
     * Dot.
     */
    public static final String DOT = ".";

    /**
     * Number formatter.
     */
    private static NumberFormat numberFormat = NumberFormat.getInstance();

    /**
     * Number formatter for percentages.
     */
    private static NumberFormat percentageFormat = NumberFormat.getPercentInstance();

    /**
     * Random number generator.
     */
    private static final Random random = new Random();

    /**
     * Char strings used to convert bytes to a hex string
     */
    private static final char[] HEX_CHARACTERS = "0123456789ABCDEF".toCharArray();

    /**
     * Characters that need to be escaped in markdown. use with
     * {@link StringUtils#escapeChars(String, List)} and
     * {@link StringUtils#unEscapeChars(String, List)}
     */
    public static final List<Character> MARKDOWN_ESCAPE_CHARACTERS = Arrays.asList('[', ']', '(', ')', '*', '#', '_',
            '~', '^', '+', '=', '>');

    /**
     * Characters that need to be escaped in Java. use with
     * {@link StringUtils#escapeChars(String, List)} and
     * {@link StringUtils#unEscapeChars(String, List)}
     */
    public static final List<Character> JAVA_ESCAPE_CHARACTERS = Arrays.asList('t', 'b', 'n', 'r', 'f', '\'', '\"',
            '\\');

    /**
     * Create a string of the given length and center the given string within it.
     * Left and right areas are filled by the character provided.
     *
     * @param string The input string.
     * @param length The length of the string to be returned.
     * @param c      The character to surround the input string with.
     * @return the new string or, if the string is longer than the specified length,
     * the original string.
     * @see #flushLeft(String, int, char)
     * @see #flushRight(String, int, char)
     */
    public static String center(String string, int length, char c) {
        if (string.length() >= length) {
            return string;
        }
        int strLen = string.length();
        int fillLen = (length - strLen) / 2;
        String leftFiller = fillString(fillLen, c);

        if ((length - strLen) % 2 != 0) {
            fillLen++;
        }

        String rightFiller = fillString(fillLen, c);

        return leftFiller + string + rightFiller;
    }

    /**
     * Compares two strings both of which may be <code>null</code>. A string which
     * is <code>null</code> is always smaller than the other string, except for both
     * strings being <code>null</code>.
     *
     * @param a The string which is compared to the second string.
     * @param b The string which is compared to the first string.
     * @return Returns 0 if both strings are <code>null</code>, -1 if only the first
     * string is <code>null</code>, and 1 if only the second string is
     * <code>null</code>. If both strings are not <code>null</code>, returns
     * the result of the usual string comparison.
     */
    public static int compare(String a, String b) {
        if (a == b) {
            return 0;
        }

        if (a == null) {
            return -1;
        }

        if (b == null) {
            return 1;
        }

        return a.compareTo(b);
    }

    /**
     * Concatenates all elements of an iterable using the
     * <code>toString()</code>-method.
     *
     * @param iterable the iterable
     * @return a concatenation, separated by spaces
     */
    public static String concat(Iterable<?> iterable) {
        return concat(iterable, SPACE);
    }

    /**
     * Concatenates all elements of an iterable using the
     * <code>toString()</code>-method, separating them with the given
     * <code>separator</code>.
     *
     * @param iterable  the iterable containing the strings
     * @param separator the separator to place between the strings, may be
     *                  <code>null</code>
     * @return a concatenation of the string in the iterable or <code>null</code> if
     * iterable was <code>null</code>. If the iterable is of size 0 the
     * empty string is returned.
     */
    public static String concat(Iterable<?> iterable, String separator) {
        if (iterable == null) {
            return null;
        }
        return concat(iterable.iterator(), separator);
    }

    /**
     * Concatenates all elements of an iterator using the
     * <code>toString()</code>-method, separating them with the given
     * <code>separator</code>.
     *
     * @param iterator  the {@link Iterator} containing the strings
     * @param separator the separator to place between the strings, may be
     *                  <code>null</code>
     * @return a concatenation of the string in the iterator or <code>null</code> if
     * iterator was <code>null</code>. If the iterator has no elements the
     * empty string is returned.
     */
    public static String concat(Iterator<?> iterator, String separator) {
        if (iterator == null) {
            return null;
        }
        if (!iterator.hasNext()) {
            return EMPTY_STRING;
        }

        if (separator == null) {
            separator = EMPTY_STRING;
        }

        StringBuilder builder = new StringBuilder();

        while (iterator.hasNext()) {
            builder.append(iterator.next());
            if (iterator.hasNext()) {
                builder.append(separator);
            }
        }

        return builder.toString();
    }

    /**
     * Concatenates all elements of an array using the <code>toString()</code>
     * -method.
     *
     * @param array the array containing the strings
     * @return a concatenation of the string separated by spaces
     */
    public static String concat(Object[] array) {
        return concat(array, SPACE);
    }

    /**
     * Concatenates all elements of an array using the <code>toString()</code>
     * -method, separating them with the given <code>separator</code>.
     *
     * @param array     the array
     * @param separator the separator to place between the strings, may be
     *                  <code>null</code>
     * @return a concatenation of the string in the array or <code>null</code> if
     * array was <code>null</code>. If array is of length 0 the empty string
     * is returned.
     */
    public static String concat(Object[] array, String separator) {
        if (array == null) {
            return null;
        }
        return concat(Arrays.asList(array), separator);
    }

    /**
     * Joins the elements in the given list with the given delimiter, but with a
     * different delimiter for the very last element.
     * <p>
     * This could, for example, be used if you want to create a String "a, b and c"
     * from a list containing "a", "b" and "c".
     */
    public static String joinDifferentLastDelimiter(List<String> items, String delimiter, String lastDelimiter) {
        int last = items.size() - 1;
        return String.join(lastDelimiter, String.join(delimiter, items.subList(0, last)), items.get(last));
    }

    /**
     * Concatenate two string arrays.
     */
    public static String[] concat(String[] array1, String[] array2) {
        String[] result = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    /**
     * Build a string with a specified length from a character.
     *
     * @param length The length of the string.
     * @param c      The character.
     * @return The string.
     */
    public static String fillString(int length, char c) {
        char[] characters = new char[length];

        Arrays.fill(characters, c);

        return new String(characters);
    }

    /**
     * Create a sting of the given length starting with the provided string.
     * Remaining characters are filled with the provided character.
     *
     * @param string The input string.
     * @param length The length of the string to be returned.
     * @param c      The character to fill the string.
     * @return the new string or, if the string is longer than the specified length,
     * the original string.
     * @see #flushRight(String, int, char)
     * @see #center(String, int, char)
     */
    public static String flushLeft(String string, int length, char c) {
        int gap = length - string.length();
        if (gap <= 0) {
            return string;
        }
        return string + StringUtils.fillString(gap, c);
    }

    /**
     * Create a sting of the given length ending with the provided string. Remaining
     * characters are filled with the provided character.
     *
     * @param string The input string.
     * @param length The length of the string to be returned.
     * @param c      The character to fill the string.
     * @return the new string or, if the string is longer than the specified length,
     * the original string.
     * @see #flushLeft(String, int, char)
     * @see #center(String, int, char)
     */
    public static String flushRight(String string, int length, char c) {
        int gap = length - string.length();
        if (gap <= 0) {
            return string;
        }
        return StringUtils.fillString(gap, c) + string;
    }

    /**
     * Format number
     */
    public static String format(Number number) {
        return numberFormat.format(number);
    }

    /**
     * Format as percentage.
     */
    public static String formatAsPercentage(Number number) {
        return percentageFormat.format(number);
    }

    /**
     * Returns the first n part of a string, separated by the given character.
     * <p>
     * E.g., getStringParts("edu.tum.cs", 2, '.') gives: "edu.tum".
     *
     * @param string     the base string
     * @param partNumber number of parts
     * @param separator  the separator character
     */
    public static String getFirstParts(String string, int partNumber, char separator) {

        if (partNumber < 0 || string == null) {
            return string;
        }

        int idx = 0;

        for (int i = 0; i < partNumber; i++) {
            idx = string.indexOf(separator, idx + 1);
            if (idx == -1) {
                return string;
            }
        }

        return string.substring(0, idx);
    }

    /**
     * Splits a key-value string and stores it in a hash map. The string must have
     * the following format:
     * <p>
     * <code>key=value[,key=value]*</code>
     * </p>
     * If the string is <code>null</code> <code>null</code> is returned.
     *
     * @param keyValueString with format described above
     * @return a hash map containing the key-values-pairs.
     */
    public static HashMap<String, String> getKeyValuePairs(String keyValueString) {
        if (keyValueString == null) {
            return null;
        }
        HashMap<String, String> result = new HashMap<>();
        if (keyValueString.trim().equals(EMPTY_STRING)) {
            return result;
        }

        String[] pairs = keyValueString.split(",");

        for (String pair : pairs) {
            int index = pair.indexOf('=');
            if (index < 0) {
                result.put(pair.trim(), null);
            } else {
                String key = pair.substring(0, index).trim();
                String value = pair.substring(index + 1).trim();
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Returns the first part of a String whose parts are separated by the given
     * character.
     * <p>
     * E.g., getFirstPart("foo@bar@acme", '@') gives "foo".
     *
     * @param string    the String
     * @param separator separation character
     * @return the first part of the String, or the original String if the
     * separation character is not found.
     */
    public static String getFirstPart(String string, String separator) {
        int idx = string.indexOf(separator);
        if (idx >= 0) {
            return string.substring(0, idx);
        }
        return string;
    }

    /**
     * Variant of {@link #getFirstPart(String, String)} which accepts a single char
     * as the separator.
     *
     * @see #getFirstPart(String, String)
     */
    public static String getFirstPart(String string, char separator) {
        return getFirstPart(string, String.valueOf(separator));
    }

    /**
     * Returns the last part of a String whose parts are separated by the given
     * String.
     * <p>
     * E.g., getLastPart("org.conqat##lib.commons.string##StringUtils", "##") gives
     * "StringUtils". If separator is the empty string, this method returns the
     * empty string.
     *
     * @param string    the String
     * @param separator separation String
     * @return the last part of the String, or the original String if the separation
     * String is not found.
     */
    public static String getLastPart(String string, String separator) {
        int idx = string.lastIndexOf(separator);
        if (idx >= 0) {
            return string.substring(idx + separator.length());
        }
        return string;
    }

    /**
     * Variant of {@link #getLastPart(String, String)} which accepts a single char
     * as the separator.
     *
     * @see #getLastPart(String, String)
     */
    public static String getLastPart(String string, char separator) {
        return getLastPart(string, String.valueOf(separator));
    }

    /**
     * Splits the string at the last occurence of {@code separator}. If the
     * separator does not occur, the second string in the returned pair is empty.
     * E.g. splitAtLast(foo.bar.baz, '.') gives ("foo.bar", "baz).
     */
    public static Pair<String, String> splitAtLast(String string, char separator) {
        int idx = string.lastIndexOf(separator);
        if (idx == -1) {
            return new Pair<>(string, "");
        }
        return new Pair<>(string.substring(0, idx), string.substring(idx + 1));
    }

    /**
     * Searches the elements of a string array for a string. Strings are trimmed.
     *
     * @param array  the array to search
     * @param string the search string
     * @return the index of the element where the string was found or
     * <code>-1</code> if string wasn't found.
     */
    public static int indexOf(String[] array, String string) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].trim().equals(string.trim())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if a string is empty (after trimming).
     *
     * @param text the string to check.
     * @return <code>true</code> if string is empty or <code>null</code>,
     * <code>false</code> otherwise.
     */
    public static boolean isEmpty(String text) {
        if (text == null) {
            return true;
        }
        return EMPTY_STRING.equals(text.trim());
    }

    /**
     * Checks if the given string contains at least one letter (checked with
     * {@link Character#isLetter(char)}).
     */
    public static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether s1 contains s2 ignoring case
     */
    public static boolean containsIgnoreCase(String s1, String s2) {
        return s1.toLowerCase().contains(s2.toLowerCase());
    }

    /**
     * Returns whether s contains all the given substrings.
     */
    public static boolean containsAll(String s, String... substrings) {
        for (String substring : substrings) {
            if (!s.contains(substring)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates a random string with a certain length. The string consists of
     * characters with ASCII code between 33 and 126.
     *
     * @param length the length of the random string
     * @return the random string
     */
    public static String randomString(int length) {
        return randomString(length, random);
    }

    /**
     * Performs the actal creation of the random string using the given randomizer.
     */
    public static String randomString(int length, Random random) {
        char[] characters = new char[length];
        for (int i = 0; i < length; i++) {
            characters[i] = (char) (random.nextInt(93) + 33);
        }
        return new String(characters);
    }

    /**
     * Generates an array of random strings.
     *
     * @param length       number of strings
     * @param stringLength length of each string
     * @return the randomly generated array.
     */
    public static String[] randomStringArray(int length, int stringLength) {
        String[] array = new String[length];
        for (int i = 0; i < length; i++) {
            array[i] = randomString(stringLength);
        }
        return array;
    }

    /**
     * Generates a pseudo random string with a certain length in a deterministic,
     * reproducable fashion.
     *
     * @param length the length of the pseudo-random string
     * @param seed   seed value for the random number generator used for the generation
     *               of the pseudo-random string. If the same seed value is used, the
     *               same pseudo-random string is generated.
     */
    public static String generateString(int length, int seed) {
        Random seededRandomizer = new Random(seed);
        return randomString(length, seededRandomizer);
    }

    /**
     * Generates an array of pseudo-random strings in a deterministic, reproducable
     * fashion.
     *
     * @param length       number of strings
     * @param stringLength length of each string
     * @param seed         seed value for the random number generator used for the generation
     *                     of the pseudo-random string. If the same seed value is used, the
     *                     same pseudo-random string array is generated.
     * @return the randomly generated array.
     */
    public static String[] generateStringArray(int length, int stringLength, int seed) {
        String[] array = new String[length];
        for (int i = 0; i < length; i++) {
            array[i] = generateString(stringLength, seed + i);
        }
        return array;
    }

    /**
     * Returns the beginning of a String, cutting off the last part which is
     * separated by the given character.
     * <p>
     * E.g., removeLastPart("org.conqat.lib.commons.string.StringUtils", '.') gives
     * "org.conqat.lib.commons.string".
     *
     * @param string    the String
     * @param separator separation character
     * @return the String without the last part, or the original string (i.e., the
     * same object) if the separation character is not found.
     */
    public static String removeLastPart(String string, char separator) {
        int idx = string.lastIndexOf(separator);
        if (idx == -1) {
            return string;
        }

        return string.substring(0, idx);
    }

    /**
     * Replaces all occurrences of keys of the given map in the given string with
     * the associated value in that map. The given map may be <code>null</code>, in
     * which case the original string is returned unchanged.
     * <p>
     * This method is semantically the same as calling
     * {@link String#replace(CharSequence, CharSequence)} for each of the entries in
     * the map, but may be significantly faster for many replacements performed on a
     * short string, since {@link String#replace(CharSequence, CharSequence)} uses
     * regular expressions internally and results in many String object allocations
     * when applied iteratively.
     * <p>
     * The order in which replacements are applied depends on the order of the map's
     * entry set.
     */
    public static String replaceFromMap(String string, Map<String, String> replacements) {
        if (replacements == null) {
            return string;
        }

        StringBuilder sb = new StringBuilder(string);
        for (Entry<String, String> entry : replacements.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            int start = sb.indexOf(key, 0);
            while (start > -1) {
                int end = start + key.length();
                int nextSearchStart = start + value.length();
                sb.replace(start, end, value);
                start = sb.indexOf(key, nextSearchStart);
            }
        }
        return sb.toString();
    }

    /**
     * Removes all occurrences of the specified strings from the given string.
     */
    public static String removeAll(String string, String... stringsToRemove) {
        if (stringsToRemove == null || stringsToRemove.length == 0) {
            return string;
        }

        StringBuilder sb = new StringBuilder(string);
        for (String key : stringsToRemove) {
            int start = sb.indexOf(key, 0);
            while (start > -1) {
                int end = start + key.length();
                sb.delete(start, end);
                start = sb.indexOf(key, start);
            }
        }
        return sb.toString();
    }



    /**
     * Returns the number of occurrences of the given character in the given string.
     */
    public static int countCharacter(String content, char character) {
        int count = 0;
        for (char c : content.toCharArray()) {
            if (c == character) {
                count++;
            }
        }
        return count;
    }


    /**
     * Prefixes a string with a prefix and separator if the prefix is not empty.
     */
    public static String addPrefix(String string, String separator, String prefix) {
        if (StringUtils.isEmpty(prefix)) {
            return string;
        }
        return prefix + separator + string;
    }

    /**
     * Suffixes a string with a suffix and separator if the suffix is not empty.
     */
    public static String addSuffix(String string, String separator, String suffix) {
        if (StringUtils.isEmpty(suffix)) {
            return string;
        }
        return string + separator + suffix;
    }

    /**
     * Remove prefix from a string.
     *
     * @param string the string
     * @param prefix the prefix
     * @return the string without the prefix or the original string if it does not
     * start with the prefix.
     */
    public static String stripPrefix(String string, String prefix) {
        if (string.startsWith(prefix)) {
            return string.substring(prefix.length());
        }
        return string;
    }

    /**
     * Remove prefix from a string. This ignores casing, i.e.<code>
     * stripPrefixIgnoreCase("C:/Programs/", "c:/programs/notepad.exe")</code> will
     * return <code>"notepad.exe"</code>.
     *
     * @param string the string
     * @param prefix the prefix
     * @return the string without the prefix or the original string if it does not
     * start with the prefix.
     */
    public static String stripPrefixIgnoreCase(String string, String prefix) {
        if (startsWithIgnoreCase(string, prefix)) {
            return string.substring(prefix.length());
        }
        return string;
    }

    /**
     * Remove suffix from a string.
     *
     * @param string the string
     * @param suffix the suffix
     * @return the string without the suffix or the original string if it does not
     * end with the suffix.
     */
    public static String stripSuffix(String string, String suffix) {
        if (string.endsWith(suffix)) {
            return string.substring(0, string.length() - suffix.length());
        }
        return string;
    }

    /**
     * Strips all digits from the given String.
     */
    public static String stripDigits(String string) {
        return string.replaceAll("[0-9]", EMPTY_STRING);
    }

    /**
     * Strips all trailing digits from the end of the given String.
     */
    public static String stripTrailingDigits(String string) {
        return string.replaceAll("\\d+$", EMPTY_STRING);
    }

    /**
     * Create string representation of a map.
     */
    public static String toString(Map<?, ?> map) {
        return toString(map, EMPTY_STRING);
    }

    /**
     * Create string representation of a map.
     *
     * @param map    the map
     * @param indent a line indent
     */
    public static String toString(Map<?, ?> map, String indent) {
        StringBuilder result = new StringBuilder();
        Iterator<?> keyIterator = map.keySet().iterator();

        while (keyIterator.hasNext()) {
            result.append(indent);
            Object key = keyIterator.next();
            result.append(key);
            result.append(" = ");
            result.append(map.get(key));
            if (keyIterator.hasNext()) {
                result.append(LINE_SEPARATOR);
            }
        }

        return result.toString();
    }

    /**
     * Convert stack trace of a {@link Throwable} to a string.
     */
    public static String obtainStackTrace(Throwable throwable) {
        StringWriter result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        throwable.printStackTrace(printWriter);
        FileSystemUtils.close(printWriter);
        FileSystemUtils.close(result);
        return result.toString();
    }

    /**
     * Test if a string starts with one of the provided prefixes. Returns
     * <code>false</code> if the list of prefixes is empty. This should only be used
     * for short lists of prefixes.
     */
    public static boolean startsWithOneOf(String string, String... prefixes) {
        for (String prefix : prefixes) {
            if (string.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test if a string starts with one of the provided prefixes. Returns
     * <code>false</code> if the list of prefixes is empty. This should only be used
     * for short lists of prefixes. The given list must not be null.
     */
    public static boolean startsWithOneOf(String string, Iterable<String> prefixes) {
        for (String prefix : prefixes) {
            if (string.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the given string starts with the prefix ignoring case, i.e.
     * <code>startsWithIgnoreCase("foobar", "Foo")</code> will return true.
     */
    public static boolean startsWithIgnoreCase(String string, String prefix) {
        return string.toLowerCase().startsWith(prefix.toLowerCase());
    }

    /**
     * Test if a string contains of the provided strings. Returns <code>false</code>
     * if the list of strings is empty. This should only be used for short lists of
     * strings.
     */
    public static boolean containsOneOf(String text, String... strings) {
        return containsOneOf(text, Arrays.asList(strings));
    }

    /**
     * Test if a string contains of the provided strings. Returns <code>false</code>
     * if the list of strings is empty. This should only be used for short lists of
     * strings.
     */
    public static boolean containsOneOf(String text, Iterable<String> strings) {
        for (String substring : strings) {
            if (text.contains(substring)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the given String ends with the specified suffix <b>ignoring
     * case</b>.
     */
    public static boolean endsWithIgnoreCase(String string, String suffix) {
        return string.toLowerCase().endsWith(suffix.toLowerCase());
    }

    /**
     * Test if a string ends with one of the provided suffixes. Returns
     * <code>false</code> if the list of prefixes is empty. This should only be used
     * for short lists of suffixes.
     */
    public static boolean endsWithOneOf(String string, String... suffixes) {
        for (String suffix : suffixes) {
            if (string.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Splits the given string into an array of {@link Character}s. This is mostly
     * used for testing purposes, if an array of certain objects is needed.
     */
    public static Character[] splitChars(String s) {
        Character[] result = new Character[s.length()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = s.charAt(i);
        }
        return result;
    }

    /**
     * Capitalize string.
     */
    public static String capitalize(String string) {
        if (StringUtils.isEmpty(string)) {
            return string;
        }
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    /**
     * Decapitalize string.
     */
    public static String decapitalize(String string) {
        if (StringUtils.isEmpty(string)) {
            return string;
        }
        return string.substring(0, 1).toLowerCase() + string.substring(1);
    }

    /**
     * This method splits the input string into words (delimited by whitespace) and
     * returns a string whose words are separated by single spaces and whose lines
     * are not longer than the given length (unless a very long word occurs)).
     */
    public static String wrapLongLines(String s, int maxLineLength) {
        String[] words = s.split("\\s+");

        StringBuilder sb = new StringBuilder();
        int lineLength = 0;
        for (String word : words) {
            if (word.length() == 0) {
                continue;
            }

            if (lineLength > 0) {
                if (lineLength + 1 + word.length() > maxLineLength) {
                    sb.append(LINE_SEPARATOR);
                    lineLength = 0;
                } else {
                    sb.append(SPACE);
                    lineLength += 1;
                }
            }
            sb.append(word);
            lineLength += word.length();
        }

        return sb.toString();
    }

    /**
     * Returns the longest common prefix of s and t
     */
    public static String longestCommonPrefix(String s, String t) {
        int n = Math.min(s.length(), t.length());
        for (int i = 0; i < n; i++) {
            if (s.charAt(i) != t.charAt(i)) {
                return s.substring(0, i);
            }
        }
        return s.substring(0, n);
    }

    /**
     * Returns the longest common suffix of s and t
     */
    public static String longestCommonSuffix(String s, String t) {
        return reverse(StringUtils.longestCommonPrefix(reverse(s), reverse(t)));
    }

    /**
     * Reverse a string
     */
    public static String reverse(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    /**
     * Removes whitespace from a string.
     */
    public static String removeWhitespace(String content) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (!Character.isWhitespace(c)) {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Removes all whitespace at the beginning of each line in the given string.
     */
    public static String removeWhitespaceAtBeginningOfLine(String content) {
        return LEADING_WHITESPACE_PATTERN.matcher(content).replaceAll(StringUtils.EMPTY_STRING);
    }

    /**
     * Creates a unique name which is not contained in the given set of names. If
     * possible the given base name is directly returned, otherwise it is extended
     * by a number.
     */
    public static String createUniqueName(String baseName, Set<String> usedNames) {
        String name = baseName;
        int i = 1;
        while (usedNames.contains(name)) {
            name = baseName + ++i;
        }
        return name;
    }

    /**
     * Transforms a string from camel-case to lower-case with hyphens (aka kebab
     * case).
     */
    public static String camelCaseToKebabCase(String s) {
        return stripPrefix(s.replaceAll("([A-Z][a-z])", "-$1").toLowerCase(), "-");
    }

    /**
     * Converts a dash-separated name (aka kebab case) to a camel-cased one.
     */
    public static String kebabCaseToCamelCase(String name) {
        StringBuilder builder = new StringBuilder();
        for (String part : name.split("-")) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() == 0) {
                builder.append(part);
            } else {
                builder.append(part.substring(0, 1).toUpperCase());
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    /**
     * Transforms a string from camel-case to upper-case with underscores.
     */
    public static String camelCaseToUnderscored(String s) {
        return stripPrefix(s.replaceAll("([A-Z][a-z])", "_$1").toUpperCase(), "_");
    }

    /**
     * Encodes a byte array as a hex string following the method described here:
     * http ://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-
     * string-in-java
     */
    public static String encodeAsHex(byte[] data) {
        char[] hexChars = new char[data.length * 2];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            hexChars[j * 2] = HEX_CHARACTERS[v >>> 4];
            hexChars[j * 2 + 1] = HEX_CHARACTERS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Decodes a byte array from a hex string.
     */
    public static byte[] decodeFromHex(String s) {
        byte[] result = new byte[s.length() / 2];
        for (int i = 0; i < result.length; ++i) {
            result[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return result;
    }

    /**
     * Format number with number formatter, if number formatter is
     * <code>null</code>, this uses {@link String#valueOf(double)}.
     */
    public static String format(double number, NumberFormat numberFormat) {
        if (numberFormat == null) {
            return String.valueOf(number);
        }
        return numberFormat.format(number);
    }

    /**
     * Regex replacement methods like
     * {@link Matcher#appendReplacement(StringBuffer, String)} or
     * {@link String#replaceAll(String, String)} treat dollar signs as group
     * references. This method escapes replacement strings so that dollar signs are
     * treated as literals.
     */
    public static String escapeRegexReplacementString(String replacement) {
        // this needs to be escape thrice as replaceAll also recognizes the
        // dollar sign
        return replacement.replaceAll("([$\\\\])", "\\\\$1");
    }

    /**
     * Converts a string to a (UTF-8) byte representation. This returns null on a
     * null input.
     */
    public static byte[] stringToBytes(String s) {
        if (s == null) {
            return null;
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Converts a (UTF-8) byte array to a string. This returns null on a null input.
     */
    public static String bytesToString(byte[] b) {
        if (b == null) {
            return null;
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    /**
     * Returns a list containing the string representations of the given collection
     * of objects. {@link String#valueOf} is used to convert each object.
     * <em>null</em> values are included, i.e., the resulting list is guaranteed to
     * have the size of the initial collection.
     */
    public static List<String> asStringList(Collection<?> objects) {
        List<String> result = new ArrayList<>();
        for (Object o : objects) {
            result.add(String.valueOf(o));
        }
        return result;
    }

    /**
     * Filters the given collection of strings by the given suffix, i.e. the
     * resulting list contains only those strings that end with this suffix.
     */
    public static List<String> filterBySuffix(String suffix, Collection<String> strings) {
        List<String> result = new ArrayList<>();
        for (String s : strings) {
            if (s.endsWith(suffix)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Converts the given objects into a string list by invoking
     * {@link Object#toString()} on each non-null element. For null entries in the
     * input, the output will contain a null entry as well.
     */
    public static <T> List<String> toStrings(Collection<T> objects) {
        List<String> strings = new ArrayList<>();
        for (T t : objects) {
            if (t == null) {
                strings.add(null);
            } else {
                strings.add(t.toString());
            }
        }
        return strings;
    }

    /**
     * Converts the given Object array into a String array by invoking toString on
     * each non-null element. For null entries in the input array, the output will
     * contain a null entry as well
     */
    public static String[] toStringArray(Object[] array) {
        return CollectionUtils.toArray(toStrings(Arrays.asList(array)), String.class);
    }

    /**
     * Converts the given String to an {@link InputStream} with UTF-8 encoding.
     */
    public static InputStream toInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Converts the given {@link InputStream} to a String with UTF-8 encoding.
     */
    public static String fromInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = inputStream.read(buffer);
        while (length != -1) {
            result.write(buffer, 0, length);
            length = inputStream.read(buffer);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Returns a list that contains all entries of the original list as lowercase
     * strings. Does not operate in-place!
     */
    public static List<String> lowercaseList(Collection<String> strings) {
        List<String> lowercaseList = new ArrayList<>();
        for (String string : strings) {
            lowercaseList.add(string.toLowerCase());
        }
        return lowercaseList;
    }

    /**
     * Returns the input string. Returns the provided default value in case the
     * input is null.
     */
    public static String defaultIfNull(String input, String defaultValue) {
        if (input == null) {
            return defaultValue;
        }
        return input;
    }

    /**
     * Returns the input string. Returns the provided default value in case the
     * input is null or the empty string.
     */
    public static String defaultIfNullOrEmpty(String input, String defaultValue) {
        if (isEmpty(input)) {
            return defaultValue;
        }
        return input;
    }

    /**
     * Returns the input string. Returns {@link #EMPTY_STRING} in case the input is
     * null.
     */
    public static String emptyIfNull(String input) {
        return defaultIfNull(input, EMPTY_STRING);
    }

    /**
     * Ensure that the given string ends with the given suffix, i.e. if it does not
     * have the given suffix, the returned string is <code>s + suffix</code>.
     */
    public static String ensureEndsWith(String s, String suffix) {
        if (!s.endsWith(suffix)) {
            return s + suffix;
        }
        return s;
    }

    /**
     * Ensure that the given string starts with the given prefix, i.e. if it does
     * not have the given prefix, it is prepended to the string.
     */
    public static String ensureStartsWith(String s, String prefix) {
        if (!s.startsWith(prefix)) {
            return prefix + s;
        }
        return s;
    }

    /**
     * Concatenates the list of string with delimiter and add escape character if
     * needed. For example following list { "asd,rtz", "rrr", "rrr" } with delimiter
     * as comma(,) will produce the following comma(,) delimited sting
     * "asd\\,rtz,rrr,rrr"
     */
    public static String concatWithEscapeCharacter(List<String> data, String delimiter) {
        return data.stream().map(a -> a.replace(delimiter, "\\" + delimiter)).reduce((a, b) -> a + delimiter + b)
                .orElse(EMPTY_STRING);
    }

    /**
     * Splits the delimited string with considering escaped delimiters. For example
     * following comma(,) delimited string "asd\\,rtz,rrr,rrr" will produce the list
     * { "asd,rtz", "rrr", "rrr" }
     */
    public static List<String> splitWithEscapeCharacter(String data, String delimiter) {
        if (isEmpty(data) || isEmpty(delimiter)) {
            return Collections.emptyList();
        }
        String regex = "(?<!\\\\)" + delimiter + "\\s*";
        return CollectionUtils.map(Arrays.asList(data.split(regex)),
                (part) -> part.trim().replace("\\" + delimiter, delimiter));
    }


    /**
     * Returns the replacement as often as possible. This is the equivalent of
     * {@link String#replaceAll(String, String)}, but accepting a {@link Pattern}
     * instead of a regex string.
     */
    public static String applyReplacement(String s, Pattern pattern, String replacement) {
        StringBuffer buffer = new StringBuffer();
        Matcher matcher = pattern.matcher(s);
        while (matcher.find()) {
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Returns {@code null} if the input string is empty (after trimming) or
     * {@code null}. Otherwise, the input is returned unaltered.
     */
    public static String nullIfEmpty(String input) {
        if (isEmpty(input)) {
            return null;
        }
        return input;
    }

    /**
     * Checks whether the parameter contains only number literals and (optionally)
     * starts with a '-' char. Returns false if the string is null or empty.
     */
    public static boolean isInteger(String string) {
        if (string == null || string.isEmpty()) {
            return false;
        }
        if (string.startsWith("-") && string.length() > 1) {
            string = string.substring(1);
        }
        for (char c : string.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the index of the first character in the given string that matches the
     * pattern. The pattern is applied to single characters, so it makes no sense to
     * supply patterns that would match on longer character sequences.
     */
    public static int indexOfMatch(String string, Pattern pattern) {
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (pattern.matcher(String.valueOf(c)).matches()) {
                return i;
            }
        }
        return -1; // no match at all
    }

    /**
     * Escapes the given chars in the content. Prepends a "\" before each occurrence
     * of the chars. Special whitespace chars ('\t','\b','\n','\r', and '\f') are
     * replaced by "\t",... . Existing occurrences of "\t",... are prepended with a
     * "\".
     */
    public static String escapeChars(String content, List<Character> chars) {
        // replace "\\t" in "foo\\tbar" to "foo\\\\tbar"
        Map<String, String> whitespaceEscapeMap = new HashMap<>();
        for (Character whitespaceChar : CollectionUtils.filter(chars, StringUtils::isJavaWhitespaceEscapeCharacter)) {
            String escapeSequence = StringUtils.getEscapeSequence(whitespaceChar);
            whitespaceEscapeMap.put(escapeSequence, "\\" + escapeSequence);
        }
        content = replaceFromMap(content, whitespaceEscapeMap);
        // replace "foo\tbar" to foo\\tbar"
        Map<String, String> escapeMap = chars.stream()
                .collect(Collectors.toMap(String::valueOf, StringUtils::getEscapeSequence));
        return replaceFromMap(content, escapeMap);
    }

    /**
     * Returns whether the given character is transformed to a whitespace character
     * by Java (e.g., \n).
     */
    private static boolean isJavaWhitespaceEscapeCharacter(Character character) {
        return character == '\t' || character == '\b' || character == '\n' || character == '\r' || character == '\f';
    }

    /**
     * Returns the Java escape sequence for the given character.
     */
    private static String getEscapeSequence(Character character) {
        switch (character) {
            case '\t':
                return "\\t";
            case '\b':
                return "\\b";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\f':
                return "\\f";
            default:
                // no Java whitespace transformation.
                return "\\" + String.valueOf(character);
        }
    }

    /**
     * Un-escapes the given chars in the content. Replaces each occurrence of \a
     * with "a" (if "a" is in chars). Whitespace escape sequences (\t, \b, \n, \r,
     * and \f) are replaced by their actual values ('\t',...) . Existing occurrences
     * of \\t,... are replaced with \t.
     */
    public static String unEscapeChars(String content, List<Character> chars) {
        Map<String, String> escapeMap = chars.stream()
                .collect(Collectors.toMap(StringUtils::getEscapeSequence, String::valueOf));
        // replace "\\x" in "foo\\xbar" to "fooxbar"
        content = replaceFromMap(content, escapeMap);
        // We might have replaced e.g., \\n with a real line break. We need to revert
        // this error in the next step.
        Map<String, String> whitespaceEscapeMap = new HashMap<>();
        for (Character whitespaceChar : CollectionUtils.filter(chars, StringUtils::isJavaWhitespaceEscapeCharacter)) {
            whitespaceEscapeMap.put("\\" + whitespaceChar, getEscapeSequence(whitespaceChar));
        }
        return replaceFromMap(content, whitespaceEscapeMap);
    }

    /**
     * Returns the beginning of the given String, retaining at most numberOfChars
     * characters. In case the String is shorter than or equals to numberOfChars,
     * the supplied String is returned unchanged. Otherwise the String is truncated
     * to numberOfChars characters and suffixed with ...
     */
    public static String abbreviate(String s, int numberOfChars) {
        if (s.length() <= numberOfChars) {
            return s;
        }
        return getFirstCharacters(s, numberOfChars) + "...";
    }

    /**
     * Returns the first N characters of the given String, retaining at most
     * numberOfChars characters. In case the String is shorter than or equals to
     * numberOfChars, the supplied String is returned unchanged.
     */
    public static String getFirstCharacters(String s, int numberOfChars) {
        if (s.length() <= numberOfChars) {
            return s;
        }
        return s.substring(0, numberOfChars);
    }

    /**
     * Returns the last N characters of the given String, retaining at most
     * numberOfChars characters. In case the String is shorter than or equals to
     * numberOfChars, the supplied String is returned unchanged.
     */
    public static String getLastCharacters(String s, int numberOfChars) {
        if (s.length() <= numberOfChars) {
            return s;
        }
        return s.substring(s.length() - numberOfChars);
    }

    /**
     * Converts the given String to a String where the first character is in upper
     * case and all other characters are in lower case
     */
    public static String toFirstUpper(String s) {
        if (isEmpty(s)) {
            return s;
        }
        char first = s.charAt(0);
        return Character.toUpperCase(first) + s.substring(1).toLowerCase();
    }

    /**
     * Returns the string itself, if count is 1. Otherwise returns the string with
     * appended "s".
     */
    public static String pluralize(String string, int count) {
        if (count == 1) {
            return string;
        }
        return string + "s";
    }

    /**
     * Adds a prefix and a suffix to the given string.
     */
    public static String surroundWith(String s, String prefix, String suffix) {
        return prefix + s + suffix;
    }

    /**
     * Compares the given value to all given strings.
     * <p>
     * This loops through the supplied string array. If the array is larger or you
     * already have the Strings in a Collection, use
     * <code>Collection.contains(..)</code>. Consider putting the arguments into a
     * Collection constant.
     *
     * @return <true> if one string equals the value.
     */
    public static boolean equalsOneOf(String value, String... strings) {
        for (String compareValue : strings) {
            if (value.equals(compareValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes double quotes from beginning and end (if present) and returns the new
     * string.
     */
    public static String removeDoubleQuotes(String string) {
        return stripPrefix(stripSuffix(string, "\""), "\"");
    }

    /**
     * Removes single quotes from beginning and end (if present) and returns the new
     * string.
     */
    public static String removeSingleQuotes(String string) {
        return stripPrefix(stripSuffix(string, "'"), "'");
    }

    /**
     * Repeats a {@link String}
     *
     * @param s     the {@link String} to repeat
     * @param times number of times the string gets repeated
     * @return the repeated {@link String}
     */
    public static String repeat(String s, int times) {
        return new String(new char[times]).replace("\0", s);
    }

    /**
     * {@link #toString()} with null check.
     *
     * @param value object to stringify
     * @return string representation or {@link StringUtils#EMPTY_STRING} if value is
     * null.
     */
    public static String safeToString(Object value) {
        if (value == null) {
            return StringUtils.EMPTY_STRING;
        }
        return value.toString();
    }

    /**
     * Returns a truncated string that contains only the first x lines of the given
     * text.
     */
    public static String retainHeadLines(String text, int numberOfLines) {
        if (text.isEmpty() || numberOfLines <= 1) {
            return "";
        }
        int charsBeforeCutLine = 0;
        for (int i = 0; i < numberOfLines; i++) {
            if (charsBeforeCutLine >= text.length()) {
                // numberOfLines is >= lines in text
                return text;
            }
            charsBeforeCutLine = text.indexOf("\n", charsBeforeCutLine) + 1;
        }
        return text.substring(0, charsBeforeCutLine - 1) + "\n";
    }
}
