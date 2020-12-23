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

import java.io.File;

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

}
