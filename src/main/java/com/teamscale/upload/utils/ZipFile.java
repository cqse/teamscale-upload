/*
 * Copyright (c) CQSE GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.teamscale.upload.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/** Wrapper around Apache Compress ZIP file that exposes the filename */
public class ZipFile extends org.apache.commons.compress.archivers.zip.ZipFile {

	/** The filename */
	private String name;

	public ZipFile(File f) throws IOException {
		super(f);
		this.name = f.getName();
	}

	public ZipFile(String name) throws IOException {
		super(name);
		this.name = name;
	}

	public ZipFile(String name, String encoding) throws IOException {
		super(name, encoding);
		this.name = name;
	}

	public ZipFile(File f, String encoding) throws IOException {
		super(f, encoding);
		this.name = f.getName();
	}

	public ZipFile(File f, String encoding, boolean useUnicodeExtraFields) throws IOException {
		super(f, encoding, useUnicodeExtraFields);
		this.name = f.getName();
	}

	public ZipFile(File f, Charset charset) throws IOException {
		this(f, charset.toString());
	}

	public String getName() {
		return name;
	}
}
