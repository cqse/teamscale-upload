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

import java.util.Arrays;
import java.util.Iterator;

/**
 * A utility class providing some advanced string functionality.
 */
public class StringUtils {

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
	 * Concatenates all elements of an iterable using the
	 * <code>toString()</code>-method.
	 *
	 * @param iterable
	 *            the iterable
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
	 * @param iterable
	 *            the iterable containing the strings
	 * @param separator
	 *            the separator to place between the strings, may be
	 *            <code>null</code>
	 * @return a concatenation of the string in the iterable or <code>null</code> if
	 *         iterable was <code>null</code>. If the iterable is of size 0 the
	 *         empty string is returned.
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
	 * @param iterator
	 *            the {@link Iterator} containing the strings
	 * @param separator
	 *            the separator to place between the strings, may be
	 *            <code>null</code>
	 * @return a concatenation of the string in the iterator or <code>null</code> if
	 *         iterator was <code>null</code>. If the iterator has no elements the
	 *         empty string is returned.
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
	 * @param array
	 *            the array containing the strings
	 * @return a concatenation of the string separated by spaces
	 */
	public static String concat(Object[] array) {
		return concat(array, SPACE);
	}

	/**
	 * Concatenates all elements of an array using the <code>toString()</code>
	 * -method, separating them with the given <code>separator</code>.
	 *
	 * @param array
	 *            the array
	 * @param separator
	 *            the separator to place between the strings, may be
	 *            <code>null</code>
	 * @return a concatenation of the string in the array or <code>null</code> if
	 *         array was <code>null</code>. If array is of length 0 the empty string
	 *         is returned.
	 */
	public static String concat(Object[] array, String separator) {
		if (array == null) {
			return null;
		}
		return concat(Arrays.asList(array), separator);
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
	 * Remove suffix from a string.
	 *
	 * @param string
	 *            the string
	 * @param suffix
	 *            the suffix
	 * @return the string without the suffix or the original string if it does not
	 *         end with the suffix.
	 */
	public static String stripSuffix(String string, String suffix) {
		if (string.endsWith(suffix)) {
			return string.substring(0, string.length() - suffix.length());
		}
		return string;
	}

	/**
	 * Reverse a string
	 */
	public static String reverse(String s) {
		return new StringBuilder(s).reverse().toString();
	}

	/**
	 * Repeats a {@link String}
	 *
	 * @param s
	 *            the {@link String} to repeat
	 * @param times
	 *            number of times the string gets repeated
	 * @return the repeated {@link String}
	 */
	public static String repeat(String s, int times) {
		return new String(new char[times]).replace("\0", s);
	}

}
