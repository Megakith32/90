/*
 * Copyright (C) 2022 Block, Inc.
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

import app.cash.zipline.loader.testing.LoaderTestFixtures
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.alphaUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.bravoUrl
import app.cash.zipline.loader.testing.LoaderTestFixtures.Companion.manifestUrl
import app.cash.zipline.loader.testing.SampleKeys
import app.cash.zipline.testing.LoggingEventListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

/**
 * This test just confirms we actually use the [ManifestVerifier] when it is configured. It assumes
 * all the interesting edge cases in that class are tested independently.
 */
@Suppress("UnstableApiUsage")
@ExperimentalCoroutinesApi
class ZiplineLoaderSigningTest {
  private val eventListener = LoggingEventListener()
  private val tester = LoaderTester(
    eventListener = eventListener,
    manifestVerifier = ManifestVerifier.Builder()
      .addEd25519("key1", SampleKeys.key1Public)
      .build()
  )

  private val testFixtures = LoaderTestFixtures()

  @BeforeTest
  fun setUp() {
    tester.beforeTest()
  }

  @AfterTest
  fun tearDown() {
    tester.afterTest()
  }

  @Test
  fun signatureVerifiesAndChecksumsMatch(): Unit = runBlocking {
    val signer = ManifestSigner.Builder()
      .addEd25519("key1", SampleKeys.key1Private)
      .build()
    val manifest = signer.sign(testFixtures.manifest)

    tester.httpClient.filePathToByteString = mapOf(
      manifestUrl to Json.encodeToString(manifest).encodeUtf8(),
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString,
    )
    val zipline = tester.loader.loadOnce("test", manifestUrl).zipline
    zipline.close()
  }

  /**
   * Note that checksum verification is essential for signing to be effective. This is because we
   * sign only the manifest and not the .zipline files it includes.
   */
  @Test
  fun checksumDoesNotMatch(): Unit = runBlocking {
    val signer = ManifestSigner.Builder()
      .addEd25519("key1", SampleKeys.key1Private)
      .build()
    val manifestWithBadChecksum = testFixtures.manifest.copy(
      modules = testFixtures.manifest.modules.mapValues { (key, value) ->
        when (key) {
          "bravo" -> value.copy(sha256 = "wrong content for SHA-256".encodeUtf8().sha256())
          else -> value
        }
      }
    )
    val manifest = signer.sign(manifestWithBadChecksum)

    tester.httpClient.filePathToByteString = mapOf(
      manifestUrl to Json.encodeToString(manifest).encodeUtf8(),
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.alphaByteString,
    )
    assertFailsWith<IllegalStateException> {
      tester.loader.loadOnce("test", manifestUrl)
    }
    assertEquals(
      "checksum mismatch for bravo",
      eventListener.takeException().message,
    )
  }

  @Test
  fun signatureDoesNotVerify(): Unit = runBlocking {
    val signer = ManifestSigner.Builder()
      .addEd25519("key1", SampleKeys.key2Private) // Wrong bytes for this key!
      .build()
    val manifest = signer.sign(testFixtures.manifest)

    tester.httpClient.filePathToByteString = mapOf(
      manifestUrl to Json.encodeToString(manifest).encodeUtf8(),
      alphaUrl to testFixtures.alphaByteString,
      bravoUrl to testFixtures.bravoByteString,
    )
    assertFailsWith<IllegalStateException> {
      tester.loader.loadOnce("test", manifestUrl)
    }
    assertEquals(
      "manifest signature for key key1 did not verify!",
      eventListener.takeException().message,
    )
  }
}
