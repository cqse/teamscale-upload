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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.Function;

/**
 * This class offers utility methods for collections. In can be seen as an
 * extension to {@link Collections}.
 */
public class CollectionUtils {

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

}
