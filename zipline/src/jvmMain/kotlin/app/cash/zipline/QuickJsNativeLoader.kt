/*
 * Copyright (C) 2021 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Locale.US

@Suppress("UnsafeDynamicallyLoadedCode") // Only loading from our own JAR contents.
internal fun loadNativeLibrary() {
  val osName = System.getProperty("os.name").lowercase(US)
  val nativeLibraryJarPath = if (osName.contains("linux")) {
    "/libquickjs.so"
  } else if (osName.contains("mac")) {
    "/libquickjs.dylib"
  } else {
    throw IllegalStateException("Unsupported OS: $osName")
  }
  val nativeLibraryUrl = QuickJs::class.java.getResource(nativeLibraryJarPath)
      ?: throw IllegalStateException("Unable to read $nativeLibraryJarPath from JAR")
  val nativeLibraryFile: Path
  try {
    nativeLibraryFile = Files.createTempFile("quickjs", null)

    // File-based deleteOnExit() uses a special internal shutdown hook that always runs last.
    nativeLibraryFile.toFile().deleteOnExit()
    nativeLibraryUrl.openStream().use { nativeLibrary ->
      Files.copy(nativeLibrary, nativeLibraryFile, REPLACE_EXISTING)
    }
  } catch (e: IOException) {
    throw RuntimeException("Unable to extract native library from JAR", e)
  }
  System.load(nativeLibraryFile.toAbsolutePath().toString())
}
