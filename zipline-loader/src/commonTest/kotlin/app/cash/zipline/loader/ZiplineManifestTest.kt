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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class ZiplineManifestTest {
  @Test
  fun manifestSortsModulesOnCreate() {
    val unsorted = ZiplineManifest.create(
      mainModuleId = "",
      mainFunction = "",
      modules = mapOf(
        "bravo" to ZiplineModule(
          url = "/bravo.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf("alpha"),
        ),
        "alpha" to ZiplineModule(
          url = "/alpha.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf(),
        ),
      )
    )
    val sorted = ZiplineManifest.create(
      mainModuleId = "",
      mainFunction = "",
      modules = mapOf(
        "alpha" to ZiplineModule(
          url = "/alpha.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf(),
        ),
        "bravo" to ZiplineModule(
          url = "/bravo.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf("alpha"),
        ),
      )
    )
    assertEquals(sorted, unsorted)
  }

  @Test
  fun manifestChecksThatModulesAreSortedIfClassIsCopied() {
    val empty = ZiplineManifest.create(
      mainModuleId = "",
      mainFunction = "",
      modules = mapOf()
    )
    val unsortedException = assertFailsWith<IllegalArgumentException> {
      empty.copy(
        mainModuleId = "./alpha.js",
        mainFunction = "zipline.main()",
        modules = mapOf(
          "bravo" to ZiplineModule(
            url = "/bravo.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("alpha"),
          ),
          "alpha" to ZiplineModule(
            url = "/alpha.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf(),
          ),

          )
      )
    }
    assertEquals(
      "Modules are not topologically sorted and can not be loaded",
      unsortedException.message
    )
  }

  @Test
  fun failsOnCreateWhenCyclicalDependencies() {
    val selfDependencyException = assertFailsWith<IllegalArgumentException> {
      ZiplineManifest.create(
        mainModuleId = "",
        mainFunction = "",
        modules = mapOf(
          "alpha" to ZiplineModule(
            url = "/alpha.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("alpha"),
          ),
        )
      )
    }
    assertEquals(
      "No topological ordering is possible for [alpha]",
      selfDependencyException.message
    )

    val cyclicalException = assertFailsWith<IllegalArgumentException> {
      ZiplineManifest.create(
        mainModuleId = "",
        mainFunction = "",
        modules = mapOf(
          "alpha" to ZiplineModule(
            url = "/alpha.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("bravo"),
          ),
          "bravo" to ZiplineModule(
            url = "/bravo.zipline",
            sha256 = "abc123".encodeUtf8(),
            dependsOnIds = listOf("alpha"),
          ),
        )
      )
    }
    assertEquals(
      "No topological ordering is possible for [alpha, bravo]",
      cyclicalException.message
    )
  }

  @Test
  fun serializesToJson() {
    val original = ZiplineManifest.create(
      mainModuleId = "",
      mainFunction = "",
      modules = mapOf(
        "alpha" to ZiplineModule(
          url = "/alpha.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf(),
        ),
        "bravo" to ZiplineModule(
          url = "/bravo.zipline",
          sha256 = "abc123".encodeUtf8(),
          dependsOnIds = listOf("alpha"),
        ),
      )
    )

    val serialized = Json { prettyPrint = true }.encodeToString(original)
    assertEquals(
        """
        |{
        |    "applicationId": "",
        |    "prepareFunction": "",
        |    "modules": {
        |        "alpha": {
        |            "url": "/alpha.zipline",
        |            "sha256": "616263313233"
        |        },
        |        "bravo": {
        |            "url": "/bravo.zipline",
        |            "sha256": "616263313233",
        |            "dependsOnIds": [
        |                "alpha"
        |            ]
        |        }
        |    }
        |}
      """.trimMargin(),
      serialized
    )

    val parsed = Json.decodeFromString<ZiplineManifest>(serialized)
    assertEquals(original, parsed)
  }
}
