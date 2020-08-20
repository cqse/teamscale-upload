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
package org.conqat.lib.commons.collections;

import org.conqat.lib.commons.string.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class offers utility methods for collections. In can be seen as an
 * extension to {@link Collections}.
 */
public class CollectionUtils {

    /**
     * Delimiter used in strings to separate list values.
     */
    public static final String MULTI_VALUE_DELIMITER = ",";

    /**
     * Create a hashed set from an array.
     *
     * @param <T>      type
     * @param elements elements in the set.
     * @return the set.
     * @see Arrays#asList(Object[])
     */
    @SafeVarargs
    public static <T> HashSet<T> asHashSet(T... elements) {
        HashSet<T> result = new HashSet<>(elements.length);
        Collections.addAll(result, elements);
        return result;
    }

    /**
     * Sorts the specified list into ascending order, according to the <i>natural
     * ordering</i> of its elements.
     * <p>
     * All elements in the list must implement the Comparable interface.
     * Furthermore, all elements in the list must be mutually comparable (that is,
     * e1.compareTo(e2) must not throw a <code>ClassCastException</code> for any
     * elements e1 and e2 in the list).
     * <p>
     * This method does not modify the original collection.
     */
    public static <T extends Comparable<? super T>> ArrayList<T> sort(Collection<T> collection) {
        ArrayList<T> list = new ArrayList<>(collection);
        Collections.sort(list);
        return list;
    }

    /**
     * Returns a list that contains the elements of the specified list in reversed
     * order.
     * <p>
     * This method does not modify the original collection.
     */
    public static <T> ArrayList<T> reverse(Collection<T> list) {
        ArrayList<T> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Returns all values, which occur multiple times in the list.
     * <p>
     * It doesn't tell you how often it occurs, just that it is more than once.
     */
    public static <T> Set<T> getDuplicates(List<T> values) {
        Set<T> duplicates = new HashSet<>();
        Set<T> temp = new HashSet<>();
        for (T key : values) {
            if (!temp.add(key)) {
                duplicates.add(key);
            }
        }
        return duplicates;
    }

    /**
     * Applies the mapper {@link Function} to all items in the collection and
     * returns the resulting {@link List}.
     * <p>
     * This method does not modify the original collection.
     */
    public static <T, R> List<R> map(Collection<T> list, Function<? super T, ? extends R> mapper) {
        List<R> result = new ArrayList<>(list.size());
        for (T entry : list) {
            result.add(mapper.apply(entry));
        }
        return result;
    }

    /**
     * Applies the mapper {@link Function} to all items in the collection and
     * returns the resulting {@link Set} only containing distinct mapped values.
     * <p>
     * This method does not modify the original collection.
     */
    public static <T, R> Set<R> mapToSet(Collection<T> list, Function<? super T, ? extends R> mapper) {
        Set<R> result = new HashSet<>();
        for (T entry : list) {
            result.add(mapper.apply(entry));
        }
        return result;
    }

    /**
     * Applies the key and value mapper {@link Function}s to all keys/values in the
     * collection and returns the resulting {@link Map}.
     * <p>
     * This method does not modify the original collection.
     */
    public static <K1, V1, K2, V2> Map<K2, V2> map(Map<K1, V1> map, Function<K1, ? extends K2> keyMapper,
                                                   Function<V1, ? extends V2> valueMapper) {
        Map<K2, V2> result = new HashMap<>(map.size());
        map.forEach((key, value) -> result.put(keyMapper.apply(key), valueMapper.apply(value)));
        return result;
    }

    /**
     * Applies the mapper {@link Function} to all items in the array and returns the
     * resulting {@link List}.
     * <p>
     * This method does not modify the original array.
     */
    public static <T, R> List<R> map(T[] array, Function<? super T, ? extends R> mapper) {
        return map(Arrays.asList(array), mapper);
    }

    /**
     * Applies the mapper {@link Function} to all items in the collection, but only
     * adds the result item to the return list if it is not already in the list.
     * Returns the resulting {@link List}.
     * <p>
     * This method does not modify the original collection.
     */
    public static <T, R> List<R> mapDistinct(Collection<T> list, Function<? super T, ? extends R> mapper) {
        Set<R> encounteredItems = new HashSet<>();
        List<R> result = new ArrayList<>();
        for (T entry : list) {
            R resultItem = mapper.apply(entry);
            if (encounteredItems.add(resultItem)) {
                result.add(resultItem);
            }
        }
        return result;
    }

    /**
     * Applies the mapper {@link FunctionWithException} to all items in the list and
     * returns the resulting {@link List}.
     * <p>
     * This method does not modify the original collection.
     *
     * @throws E if the mapper function throws this exception for any of the
     *           elements of the original list.
     */
    public static <T, R, E extends Exception> List<R> mapWithException(Collection<T> list,
                                                                       FunctionWithException<? super T, ? extends R, ? extends E> mapper) throws E {
        List<R> result = new ArrayList<>(list.size());
        for (T entry : list) {
            result.add(mapper.apply(entry));
        }
        return result;
    }

    /**
     * Applies the mapper {@link FunctionWithException} to all items in the array
     * and returns the resulting {@link List}.
     * <p>
     * This method does not modify the original array.
     *
     * @throws E if the mapper function throws this exception for any of the
     *           elements of the original array.
     */
    public static <T, R, E extends Exception> List<R> mapWithException(T[] array,
                                                                       FunctionWithException<? super T, ? extends R, ? extends E> mapper) throws E {
        return mapWithException(Arrays.asList(array), mapper);
    }

    /**
     * Returns a new List containing only the elements at the given indices.
     */
    public static <T> ArrayList<T> getIndices(List<T> list, List<Integer> indices) {
        ArrayList<T> filteredList = new ArrayList<>();
        for (int index : indices) {
            filteredList.add(list.get(index));
        }
        return filteredList;
    }

    /**
     * Returns a new List containing all elements of the given list except for those
     * with the given indices.
     */
    public static <T> List<T> removeIndicesFrom(List<T> elements, Set<Integer> indices) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            if (!indices.contains(i)) {
                result.add(elements.get(i));
            }
        }
        return result;
    }

    /**
     * Returns the indices of null elements in the given list. The returned list is
     * strictly ordered (returnedList.get(x) is greater than returnedList.get(x-1)).
     */
    public static List<Integer> getNullIndices(List<?> list) {
        List<Integer> nullIndices = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                nullIndices.add(i);
            }
        }
        return nullIndices;
    }

    /**
     * Returns whether the given integer is a valid index in the given list (i.e.,
     * >=0 and <list.size()).
     */
    public static boolean isValidIndex(int index, List<?> list) {
        return index >= 0 && index < list.size();
    }

    /**
     * This interface is the same as {@link Supplier}, except it allows throwing a
     * checked exception.
     */
    @FunctionalInterface
    public interface SupplierWithException<T, E extends Exception> {

        /**
         * Returns the supplied value. May throw a declared exception.
         */
        T get() throws E;
    }

    /**
     * This interface is the same as {@link Function}, except it allows throwing a
     * checked exception.
     */
    @FunctionalInterface
    public interface FunctionWithException<T, R, E extends Exception> {

        /**
         * Applies the function to the given argument. May throw the declared exception.
         */
        R apply(T t) throws E;

    }

    /**
     * This interface is the same as {@link Consumer}, except it allows throwing a
     * checked exception.
     */
    @FunctionalInterface
    public interface ConsumerWithException<T, E extends Exception> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         */
        void accept(T t) throws E;

    }

    /**
     * This interface is the same as {@link Consumer}, except it allows throwing two
     * checked exceptions.
     */
    @FunctionalInterface
    public interface ConsumerWithTwoExceptions<T, E1 extends Exception, E2 extends Exception> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         */
        void accept(T t) throws E1, E2;

    }

    /**
     * This interface is the same as {@link BiConsumer}, except it allows throwing a
     * checked exceptions.
     */
    @FunctionalInterface
    public interface BiConsumerWithException<S, T, E extends Exception> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         */
        void accept(S s, T t) throws E;

    }

    /**
     * This interface is the same as {@link BiConsumer}, except it allows throwing
     * two checked exceptions.
     */
    @FunctionalInterface
    public interface BiConsumerWithTwoExceptions<S, T, E1 extends Exception, E2 extends Exception> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         */
        void accept(S s, T t) throws E1, E2;

    }

    /**
     * This interface is the same as {@link BiFunction}, except it allows throwing a
     * checked exception.
     */
    @FunctionalInterface
    public interface BiFunctionWithException<T, U, R, E extends Exception> {
        /**
         * Returns the supplied value. May throw a declared exception.
         */
        R apply(T t, U u) throws E;
    }

    /**
     * Filters the collection by testing all items against the {@link Predicate} and
     * returning a {@link List} of those for which it returns <code>true</code>.
     *
     * <p>
     * This method does not modify the original collection.
     */
    public static <T> List<T> filter(Collection<T> collection, Predicate<? super T> filter) {
        ArrayList<T> result = new ArrayList<>();
        for (T entry : collection) {
            if (filter.test(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Filters the collection by testing all items against the given predicate and
     * returning a {@link List} of those for which it returns <code>true</code>.
     * Propagates checked exceptions of the given predicate.
     *
     * <p>
     * This method does not modify the original collection.
     */
    public static <T, E extends Exception> List<T> filterWithException(Collection<T> collection,
                                                                       FunctionWithException<? super T, Boolean, E> filter) throws E {
        ArrayList<T> result = new ArrayList<>();
        for (T entry : collection) {
            if (filter.apply(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Filters the collection by testing all items against the {@link Predicate} and
     * returning a {@link Set} of those for which it returns <code>true</code>.
     *
     * <p>
     * This method does not modify the original collection.
     */
    public static <T> Set<T> filterToSet(Collection<T> collection, Predicate<? super T> filter) {
        Set<T> result = new HashSet<>();
        for (T entry : collection) {
            if (filter.test(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Applies the mapper to all items in the list, for which the filter
     * {@link Predicate} returns <code>true</code> and returns the results as a
     * {@link List}.
     * <p>
     * This method does not modify the original collection.
     */
    public static <T, R> List<R> filterAndMap(Collection<T> list, Predicate<? super T> filter,
                                              Function<? super T, ? extends R> mapper) {
        List<R> result = new ArrayList<>();
        for (T entry : list) {
            if (filter.test(entry)) {
                result.add(mapper.apply(entry));
            }
        }
        return result;
    }

    /**
     * Returns a list that contains all elements of the specified list <B>except</B>
     * the element at the specified index. Removes the index from the new List.
     * <p>
     * This method does not modify the original list.
     */
    public static <T> List<T> remove(List<T> list, int index) {
        ArrayList<T> result = new ArrayList<>(list);
        result.remove(index);
        return result;
    }

    /**
     * Sorts the specified list according to the order induced by the specified
     * comparator.
     * <p>
     * All elements in the list must implement the Comparable interface.
     * Furthermore, all elements in the list must be mutually comparable (that is,
     * e1.compareTo(e2) must not throw a <code>ClassCastException</code> for any
     * elements e1 and e2 in the list).
     * <p>
     * This method does not modify the original collection.
     */
    public static <T> List<T> sort(Collection<T> collection, Comparator<? super T> comparator) {
        ArrayList<T> list = new ArrayList<>(collection);
        list.sort(comparator);
        return list;
    }

    /**
     * Returns one object from an {@link Iterable} or <code>null</code> if the
     * iterable is empty.
     */
    public static <T> T getAny(Iterable<T> iterable) {
        Iterator<T> iterator = iterable.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        return iterator.next();
    }

    /**
     * Convert collection to array. This is a bit cleaner version of
     * {@link Collection#toArray(Object[])}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Collection<? extends T> collection, Class<T> type) {
        T[] result = (T[]) java.lang.reflect.Array.newInstance(type, collection.size());

        Iterator<? extends T> it = collection.iterator();
        for (int i = 0; i < collection.size(); i++) {
            result[i] = it.next();
        }

        return result;
    }

    /**
     * Copy an array. This is a shortcut for {@link Arrays#copyOf(Object[], int)}
     * that does not require to specify the length.
     */
    public static <T> T[] copyArray(T[] original) {
        return Arrays.copyOf(original, original.length);
    }

    /**
     * Compute list of unordered pairs for all elements contained in a collection.
     */
    public static <T> List<ImmutablePair<T, T>> computeUnorderedPairs(Collection<T> collection) {
        List<T> elements = new ArrayList<>(collection);
        List<ImmutablePair<T, T>> pairs = new ArrayList<>();

        int size = elements.size();
        for (int firstIndex = 0; firstIndex < size; firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < size; secondIndex++) {
                pairs.add(new ImmutablePair<>(elements.get(firstIndex), elements.get(secondIndex)));
            }
        }
        return pairs;
    }

    /**
     * Returns the last element in list or <code>null</code>, if list is empty.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getLast(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        if (list instanceof Deque<?>) {
            return ((Deque<T>) list).getLast();
        }
        return list.get(list.size() - 1);
    }

    /**
     * Returns the sublist of all but the first element in list or
     * <code>null</code>, if list is empty.
     */
    public static <T> List<T> getRest(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.subList(1, list.size());
    }


    /**
     * Returns a list implementation that allows for efficient random access.
     * <p>
     * If the passed collection already supports random access, it gets returned
     * directly. Otherwise a list that supports random access is returned with the
     * same content as the passed list.
     */
    public static <T> List<T> asRandomAccessList(Collection<T> list) {
        // It is not guaranteed that implementations of RandomAccess also
        // implement List. Hence, we check for both.
        if (list instanceof List<?> && list instanceof RandomAccess) {
            return (List<T>) list;
        }

        return new ArrayList<>(list);
    }

    @SafeVarargs
    private static <T, C extends Collection<T>> C unionCollection(Supplier<C> collectionSupplier,
                                                                  Collection<T> collection1, Collection<T>... furtherCollections) {
        C result = collectionSupplier.get();
        if (collection1 != null) {
            result.addAll(collection1);
        }
        for (Collection<T> collection : furtherCollections) {
            if (collection != null) {
                result.addAll(collection);
            }
        }
        return result;
    }

    /**
     * Return a set containing the union of all provided collections. We use a
     * {@link HashSet}, i.e. the elements should support hashing.
     * <p>
     * We use two separate arguments to ensure on the interface level that at least
     * one collection is provided. This is transparent for the caller.
     * <p>
     * All arguments can be null. The result will always be non-null and will be an
     * empty set if all arguments are null.
     */
    @SafeVarargs
    public static <T> HashSet<T> unionSet(Collection<T> collection1, Collection<T>... furtherCollections) {
        return unionCollection(HashSet::new, collection1, furtherCollections);
    }

    /**
     * Return a set containing the union of all provided {@link EnumSet}s.
     * <p>
     * We use two separate arguments to ensure on the interface level that at least
     * one collection is provided. This is transparent for the caller.
     * <p>
     * None of the arguments may be null.
     */
    @SafeVarargs
    public static <T extends Enum<T>> EnumSet<T> enumUnionSet(EnumSet<T> initialSet, EnumSet<T>... otherSets) {
        EnumSet<T> union = EnumSet.copyOf(initialSet);
        for (EnumSet<T> other : otherSets) {
            union.addAll(other);
        }
        return union;
    }

    /**
     * Return a set containing the union of all provided collections. We use a
     * {@link ArrayList} and the result preserves duplicates between and within the
     * collections.
     * <p>
     * We use two separate arguments to ensure on the interface level that at least
     * one collection is provided. This is transparent for the caller.
     * <p>
     * All arguments can be null. The result will always be non-null and will be an
     * empty set if all arguments are null.
     */
    @SafeVarargs
    public static <T> ArrayList<T> unionList(Collection<T> collection1, Collection<T>... furtherCollections) {
        return unionCollection(ArrayList::new, collection1, furtherCollections);
    }

    /**
     * Return a set containing the union of all elements of the provided sets. We
     * use a {@link HashSet}, i.e. the elements should support hashing.
     */
    public static <T> HashSet<T> unionSetAll(Collection<? extends Collection<T>> sets) {
        HashSet<T> result = new HashSet<>();
        for (Collection<T> set : sets) {
            result.addAll(set);
        }
        return result;
    }

    /**
     * Creates a new set only containing those elements of the given collection that
     * are not in elementsToRemove. Substracts elementsToRemove from collection.
     * Both collections may be <code>null</code>.
     */
    public static <T> Set<T> subtract(Collection<T> collection, Collection<T> elementsToRemove) {
        Set<T> result = new HashSet<>();
        if (collection != null) {
            result.addAll(collection);
        }
        if (elementsToRemove != null) {
            result.removeAll(elementsToRemove);
        }
        return result;
    }

    /**
     * Adds all elements of collection2 to collection1. collection2 may be
     * <code>null</code>, in which case nothing happens.
     */
    public static <T> void addAllSafe(Collection<T> collection1, Collection<T> collection2) {
        if (collection2 != null) {
            collection1.addAll(collection2);
        }
    }

    /**
     * Return a set containing the intersection of all provided collections. We use
     * a {@link HashSet}, i.e. the elements should support hashing.
     * <p>
     * We use two separate arguments to ensure on the interface level that at least
     * one collection is provided. This is transparent for the caller.
     */
    @SafeVarargs
    public static <T> HashSet<T> intersectionSet(Collection<T> collection1, Collection<T>... furtherCollections) {
        HashSet<T> result = new HashSet<>(collection1);
        for (Collection<T> collection : furtherCollections) {
            if (collection instanceof Set) {
                result.retainAll(collection);
            } else {
                // if the collection is not already a set, it will be
                // significantly faster to first build a set, to speed up the
                // containment query in the following call.
                result.retainAll(new HashSet<>(collection));
            }
        }
        return result;
    }

    /**
     * Returns the set-theoretic difference between the first and the additional
     * collections, i.e. a set containing all elements that occur in the first, but
     * not in any of the other collections. We use a {@link HashSet}, so the
     * elements should support hashing.
     */
    @SafeVarargs
    public static <T> HashSet<T> differenceSet(Collection<T> collection1,
                                               Collection<? extends T>... furtherCollections) {
        HashSet<T> result = new HashSet<>(collection1);
        for (Collection<? extends T> collection : furtherCollections) {
            if (collection instanceof Set) {
                result.removeAll(collection);
            } else {
                // if the collection is not already a set, it will be
                // significantly faster to first build a set, to speed up the
                // containment query in the following call.
                result.removeAll(new HashSet<>(collection));
            }
        }
        return result;
    }

    /**
     * Checks whether collection is null or empty
     */
    public static boolean isNullOrEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Checks whether map is null or empty
     */
    public static boolean isNullOrEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Truncates the given list by removing elements from the end such that
     * numElements entries remain. If the list has less than numElements entries, it
     * remains unchanged. Thus, this method ensures that the size of the list is <=
     * numElements.
     */
    public static void truncateEnd(List<?> list, int numElements) {
        if (list.size() > numElements) {
            list.subList(numElements, list.size()).clear();
        }
    }

    /**
     * Obtain all permutations of the provided elements.
     */
    public static <T> List<List<T>> getAllPermutations(@SuppressWarnings("unchecked") T... elements) {
        List<List<T>> result = new ArrayList<>();
        permute(Arrays.asList(elements), 0, result);
        return result;
    }

    /**
     * Recursively creates permutations.
     */
    private static <T> void permute(List<T> list, int index, List<List<T>> result) {
        for (int i = index; i < list.size(); i++) {
            Collections.swap(list, i, index);
            permute(list, index + 1, result);
            Collections.swap(list, index, i);
        }
        if (index == list.size() - 1) {
            result.add(new ArrayList<>(list));
        }
    }

    /**
     * Returns the power set of the given input list. Note that elements are treated
     * as unique, i.e. we do not really use set semantics here. Also note that the
     * returned list has 2^n elements for n input elements, so the input list should
     * not be too large.
     */
    public static <T> List<List<T>> getPowerSet(List<T> input) {
        return getPowerSet(input, 0);
    }

    /**
     * Returns the power set of the given input list, only considering elements at
     * or after index <code>start</code>.
     */
    private static <T> List<List<T>> getPowerSet(List<T> input, int start) {
        ArrayList<List<T>> result = new ArrayList<>();
        if (start >= input.size()) {
            result.add(new ArrayList<>());
        } else {
            T element = input.get(start);
            for (List<T> list : getPowerSet(input, start + 1)) {
                List<T> copy = new ArrayList<>();
                copy.add(element);
                copy.addAll(list);

                result.add(list);
                result.add(copy);
            }
        }
        return result;
    }


    /**
     * Returns the input map, or uses the given supplier to create a new one.
     */
    public static <K, V, M extends Map<K, V>> M emptyIfNull(M input, Supplier<M> supplier) {
        return Optional.ofNullable(input).orElseGet(supplier);
    }

    /**
     * Removes an element from the array and returns the new array.
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] removeElementFromArray(T element, T[] array) {
        ArrayList<T> result = new ArrayList<>(Arrays.asList(array));
        result.remove(element);
        return toArray(result, (Class<T>) element.getClass());
    }

    /**
     * Returns a new list containing only the non-null elements of the given list.
     */
    public static <T> List<T> filterNullEntries(List<T> list) {
        return filter(list, Objects::nonNull);
    }

    /**
     * Returns a new list containing only the non-null elements of the given array.
     */
    public static <T> List<T> filterNullEntries(T[] array) {
        return filterNullEntries(Arrays.asList(array));
    }

    /**
     * Concatenate two arrays to a new one. See also:
     * http://stackoverflow.com/a/80503/205903
     */
    public static <T> T[] concatenateArrays(T[] a, T[] b) {
        int length1 = a.length;
        int length2 = b.length;

        @SuppressWarnings("unchecked")
        T[] newArray = (T[]) Array.newInstance(a.getClass().getComponentType(), length1 + length2);
        System.arraycopy(a, 0, newArray, 0, length1);
        System.arraycopy(b, 0, newArray, length1, length2);

        return newArray;
    }

    /**
     * Returns if any element in the collection matches the predicate.
     */
    public static <T> boolean anyMatch(Collection<? extends T> collection, Predicate<T> predicate) {
        for (T element : collection) {
            if (predicate.test(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns if all elements in the collection match the predicate.
     */
    public static <T> boolean allMatch(Collection<? extends T> collection, Predicate<T> predicate) {
        for (T element : collection) {
            if (!predicate.test(element)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Maps all entries of the given Collection to their string representations.
     * This method always returns a list. The order of elements in the returned list
     * corresponds to the order of element returned by input.stream(), i.e., if
     * input is a List then the order is stable, if input is a set then the order is
     * random.
     */
    public static List<String> toStringSet(Collection<?> input) {
        return input.stream().map(Object::toString).collect(Collectors.toList());
    }

    /**
     * Returns a {@link Comparator} that compares lists. Shorter lists are
     * considered "smaller" than longer lists. If lists have the same length,
     * elements are compared using their compareTo methods until one is found that
     * is does not return 0 when compared to its counterpart. If list lengths are
     * equal and all elements return 0 on comparison, the returned
     * {@link Comparator} returns 0.
     */
    public static <L extends List<T>, T extends Comparable<T>> Comparator<L> getListComparator() {
        return getListComparator(T::compareTo);
    }

    /**
     * Returns a {@link Comparator} that compares lists. Shorter lists are
     * considered "smaller" than longer lists. If lists have the same length,
     * elements are compared using the given elementComparator until one is found
     * that is does not return 0 when compared to its counterpart. If list lengths
     * are equal and all elements return 0 on comparison, the returned
     * {@link Comparator} returns 0.
     */
    public static <L extends List<T>, T> Comparator<L> getListComparator(Comparator<T> elementComparator) {
        return (o1, o2) -> {
            if (o1.size() != o2.size()) {
                return o1.size() - o2.size();
            }
            for (int i = 0; i < o1.size(); i++) {
                int currentComparison = elementComparator.compare(o1.get(i), o2.get(i));
                if (currentComparison != 0) {
                    return currentComparison;
                }
            }
            return 0;
        };
    }

    /**
     * Returns a {@link Comparator} that compares {@link Pair}s of
     * {@link Comparable}s. First compares the first elements and if their
     * comparison returns 0, compares the second elements. If their comparison also
     * return 0, this {@link Comparator} returns 0.
     */
    public static <P extends Pair<T, S>, T extends Comparable<T>, S extends Comparable<S>> Comparator<P> getPairComparator() {
        return (o1, o2) -> {
            int firstComparison = o1.getFirst().compareTo(o2.getFirst());
            if (firstComparison != 0) {
                return firstComparison;
            }
            return o1.getSecond().compareTo(o2.getSecond());
        };
    }

    /**
     * Returns an empty {@link Consumer} that does nothing.
     */
    public static <T> Consumer<T> emptyConsumer() {
        return x -> {
            // empty
        };
    }

    /**
     * Splits a {@link String} representing a list of values to a list of lines and
     * forwards it to {@link CollectionUtils#parseMultiValueStringToList(List)}.
     */
    public static List<String> parseMultiValueStringToList(String valueList) {
        List<String> lines = StringUtils.splitWithEscapeCharacter(valueList, "\\n");
        return parseMultiValueStringToList(lines);
    }

    /**
     * Parses a {@link List} of {@link String} representing lines of values to a
     * list of the single values. The value entries must be separated by
     * {@value #MULTI_VALUE_DELIMITER}.
     */
    public static List<String> parseMultiValueStringToList(List<String> valueListLines) {
        List<String> results = new ArrayList<>();
        for (String line : valueListLines) {
            List<String> result = StringUtils.splitWithEscapeCharacter(line, MULTI_VALUE_DELIMITER);
            for (String value : result) {
                if (StringUtils.isEmpty(value)) {
                    throw new IllegalArgumentException(
                            "Found duplicate comma (empty value) in list: " + valueListLines);
                }
            }
            results.addAll(result);
        }
        return results;
    }


    /**
     * Returns the element index of the first element in the list that matches the
     * given predicate. Returns -1 if no element matches the predicate.
     */
    public static <T> int indexOfFirstMatch(List<T> list, Predicate<T> predicate) {
        int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            if (predicate.test(list.get(i))) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Wrapper for {@link List#subList(int, int)} with only a from parameter.
     * <p>
     * Returns a view of the portion of this list between the specified
     * <tt>fromIndex</tt>, inclusive, and the end of the list. The returned list is
     * backed by this list, so non-structural changes in the returned list are
     * reflected in this list, and vice-versa.
     *
     * @param fromIndex low endpoint (inclusive) of the subList
     * @return a view of the specified range within this list
     * @throws IndexOutOfBoundsException for an illegal endpoint index value
     *                                   (<tt>fromIndex &lt; 0 || fromIndex &gt; size</tt>)
     */
    public static <T> List<T> subListFrom(List<T> list, int fromIndex) {
        return list.subList(fromIndex, list.size());
    }


    /**
     * Returns a {@link Predicate} that performs the test of {@code predicate} on
     * the key of a {@link Map.Entry}.
     */
    public static <T> Predicate<Map.Entry<T, ?>> onKey(Predicate<? super T> predicate) {
        return entry -> predicate.test(entry.getKey());
    }

    /**
     * Returns a {@link Predicate} that performs the test of {@code predicate} on
     * the value of a {@link Map.Entry}.
     */
    public static <T> Predicate<Map.Entry<?, T>> onValue(Predicate<? super T> predicate) {
        return entry -> predicate.test(entry.getValue());
    }
}
