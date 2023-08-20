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
package app.cash.zipline.loader

import app.cash.turbine.test
import app.cash.zipline.Zipline
import app.cash.zipline.loader.internal.fetcher.LoadedManifest
import app.cash.zipline.loader.internal.getApplicationManifestFileName
import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.assertDownloadedToEmbeddedManifest
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createJs
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.createRelativeManifest
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.manifestUrl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.FileSystem
import okio.Path

@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderTest {
  private val tester = LoaderTester()

  private lateinit var loader: ZiplineLoader
  private lateinit var httpClient: FakeZiplineHttpClient
  private lateinit var embeddedFileSystem: FileSystem
  private lateinit var embeddedDir: Path

  private val testFixtures = LoaderTestFixtures()

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
    loader = tester.loader
    httpClient = tester.httpClient
    embeddedFileSystem = tester.embeddedFileSystem
    embeddedDir = tester.embeddedDir
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
  }

  @Test
  fun happyPath() = runBlocking {
    httpClient.filePathToByteString = mapOf(
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val zipline = loader.loadOrFail("test", testFixtures.manifest)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun loadManifestFromUrl() = runBlocking {
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestNoBaseUrlByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val zipline = loader.loadOnce("test", manifestUrl).zipline
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun loaderUsesCache() = runBlocking {
    // load, no cache hit, download
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestNoBaseUrlByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    val ziplineColdCache = loader.loadOnce("test", manifestUrl).zipline
    assertEquals(
      ziplineColdCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    ziplineColdCache.close()

    // load, cache hit, no download
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestNoBaseUrlByteString,
      // Note no actual alpha/bravo files are available on the network
    )
    val ziplineWarmedCache = loader.loadOnce("test", manifestUrl).zipline
    assertEquals(
      ziplineWarmedCache.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    ziplineWarmedCache.close()
  }

  @Test
  fun loaderUsesResourcesBeforeCacheButManifestOverNetwork() = runBlocking {
    // seed the resources FS with zipline files
    embeddedFileSystem.createDirectories(embeddedDir)
    embeddedFileSystem.write(embeddedDir / testFixtures.alphaSha256Hex) {
      write(testFixtures.alphaByteString)
    }
    embeddedFileSystem.write(embeddedDir / testFixtures.bravoSha256Hex) {
      write(testFixtures.bravoByteString)
    }

    // load, resources hit, no download via cache
    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestByteString,
      // Note no actual alpha/bravo files are available on the cache / network
    )
    val zipline = loader.loadOnce("test", manifestUrl).zipline
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun downloadToDirectoryThenLoadFromAsEmbedded() = runBlocking {
    val downloadFileSystem = systemFileSystem
    val downloadDir = tester.tempDir / "downloads"

    assertFalse(downloadFileSystem.exists(downloadDir / getApplicationManifestFileName("test")))
    assertFalse(downloadFileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertFalse(downloadFileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))

    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestNoBaseUrlByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    loader.download("test", downloadDir, downloadFileSystem, manifestUrl)

    assertDownloadedToEmbeddedManifest(
      testFixtures.manifest,
      downloadFileSystem.read(downloadDir / getApplicationManifestFileName("test")) {
        readByteString()
      })
    assertTrue(downloadFileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      downloadFileSystem.read(downloadDir / testFixtures.alphaSha256Hex) { readByteString() })
    assertTrue(downloadFileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))
    assertEquals(
      testFixtures.bravoByteString,
      downloadFileSystem.read(downloadDir / testFixtures.bravoSha256Hex) { readByteString() })

    // Load into Zipline.
    val zipline = loader.loadOrFail("test", testFixtures.manifest)
    assertEquals(
      zipline.quickJs.evaluate("globalThis.log", "assert.js"),
      """
      |alpha loaded
      |bravo loaded
      |""".trimMargin()
    )
    zipline.close()
  }

  @Test
  fun downloadToDirectory() = runBlocking {
    val fileSystem = systemFileSystem
    val downloadDir = tester.tempDir / "downloads"

    assertFalse(fileSystem.exists(downloadDir / getApplicationManifestFileName("test")))
    assertFalse(fileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertFalse(fileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))

    httpClient.filePathToByteString = mapOf(
      manifestUrl to testFixtures.manifestByteString,
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString
    )
    loader.download("test", downloadDir, fileSystem, testFixtures.embeddedLoadedManifest)

    // check that files have been downloaded to downloadDir as expected
    assertTrue(fileSystem.exists(downloadDir / getApplicationManifestFileName("test")))
    assertDownloadedToEmbeddedManifest(
      testFixtures.manifest,
      fileSystem.read(downloadDir / getApplicationManifestFileName("test")) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixtures.alphaSha256Hex))
    assertEquals(
      testFixtures.alphaByteString,
      fileSystem.read(downloadDir / testFixtures.alphaSha256Hex) { readByteString() })
    assertTrue(fileSystem.exists(downloadDir / testFixtures.bravoSha256Hex))
    assertEquals(
      testFixtures.bravoByteString,
      fileSystem.read(downloadDir / testFixtures.bravoSha256Hex) { readByteString() })
  }

  @Test
  fun loadContinuously() = runBlocking {
    val baseUrl = "https://example.com/files"
    val applicationName = "red"
    val appleZiplineFileByteString = testFixtures.createZiplineFile(createJs("apple"), "apple.js")
    val appleManifest = createRelativeManifest("apple", appleZiplineFileByteString.sha256())
    val appleManifestUrl = "$baseUrl/apple/${getApplicationManifestFileName(applicationName)}"

    val firetruckZiplineFileByteString =
      testFixtures.createZiplineFile(createJs("firetruck"), "firetruck.js")
    val firetruckManifest =
      createRelativeManifest("firetruck", firetruckZiplineFileByteString.sha256())
    val firetruckManifestUrl =
      "$baseUrl/firetruck/${getApplicationManifestFileName(applicationName)}"

    httpClient.filePathToByteString = mapOf(
      appleManifestUrl to appleManifest.manifestBytes,
      "$baseUrl/apple/apple.zipline" to appleZiplineFileByteString,
      firetruckManifestUrl to firetruckManifest.manifestBytes,
      "$baseUrl/firetruck/firetruck.zipline" to firetruckZiplineFileByteString,
    )

    val manifestUrlFlow = flowOf(appleManifestUrl, firetruckManifestUrl)
    loader.load(
      applicationName = "red",
      manifestUrlFlow = manifestUrlFlow,
      initializer = {},
    ).test {
      assertEquals(
        "apple",
        (awaitItem().zipline.quickJs.evaluate(
          "globalThis.log", "assert.js"
        ) as String).removeSuffix(
          " loaded\n"
        )
      )
      assertEquals(
        "firetruck",
        (awaitItem().zipline.quickJs.evaluate(
          "globalThis.log", "assert.js"
        ) as String).removeSuffix(
          " loaded\n"
        )
      )
      awaitComplete()
    }
  }

  private suspend fun ZiplineLoader.loadOrFail(
    applicationName: String,
    manifest: ZiplineManifest,
    initializer: (Zipline) -> Unit = {},
  ): Zipline {
    return loadFromManifest(
      applicationName = applicationName,
      loadedManifest = LoadedManifest(ByteString.EMPTY, manifest, 1L),
      initializer = initializer,
    )
  }
}
