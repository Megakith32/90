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

package app.cash.zipline.gradle

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

class ZiplinePluginTest {
  @Test
  fun productionBuilds() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:compileProductionExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/compileSync/js/main/productionExecutable/kotlinZipline",
    )
    assertThat(ziplineOut.resolve(manifestFileName).exists()).isTrue()
    assertThat(ziplineOut.resolve("basic-lib.zipline").exists()).isTrue()
  }

  /**
   * This is similar to the test above, confirming that our task tracks the JS compilation mode
   * and the corresponding directories.
   */
  @Test
  fun developmentBuilds() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:compileDevelopmentExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/compileSync/js/main/developmentExecutable/kotlinZipline",
    )
    assertThat(ziplineOut.resolve(manifestFileName).exists()).isTrue()
    assertThat(ziplineOut.resolve("basic-lib.zipline").exists()).isTrue()
  }

  /**
   * Run the Zipline compiler on the Webpack output. It's flattened into a single module and
   * minified.
   */
  @Test
  fun webpackBuild() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:jsBrowserProductionWebpackZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/dist/js/productionExecutableZipline",
    )
    assertThat(ziplineOut.listFiles()?.size).isEqualTo(2)
    assertThat(ziplineOut.resolve(manifestFileName).exists()).isTrue()
    assertThat(ziplineOut.resolve("lib.zipline").exists()).isTrue()
  }

  /**
   * This confirms these plugin features are working:
   *
   *  - IR rewriting in JS and JVM
   *  - Compiling to .zipline files and producing a manifest
   */
  @Test
  fun endToEnd() {
    val projectDir = File("src/test/projects/basic")

    val taskName = ":lib:launchGreetService"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output).contains("end-to-end call result: 'Hello, Jesse'")
  }

  @Test
  fun jvmOnlyProject() {
    val projectDir = File("src/test/projects/jvmOnly")

    val taskName = ":lib:bindAndTakeJvm"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
    assertThat(result.output).contains("Zipline Kotlin plugin did its job properly")
  }

  /**
   * Although Kotlin/JS looks like it supports multiple JS targets in a single project, the link
   * task doesn't include the target name in the output directory path. Linked targets are instead
   * disambiguated by their `.js` file name only.
   *
   * This test confirms that linking a single target works, though the whole thing is pretty
   * fragile.
   */
  @Test
  fun multipleJsTargets() {
    val projectDir = File("src/test/projects/multipleJsTargets")

    val taskName = ":lib:compileDevelopmentExecutableKotlinBlueZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/compileSync/blue/main/developmentExecutable/kotlinZipline",
    )
    val manifest = ziplineOut.resolve(manifestFileName)
    assertThat(manifest.exists()).isTrue()
    assertThat(manifest.readText())
      .containsMatch(Regex(""""version":"1.2.3""""))
    assertThat(ziplineOut.resolve("multipleJsTargets-lib-blue.zipline").exists()).isTrue()
  }

  @Test
  fun manifestSigning() {
    val projectDir = File("src/test/projects/signing")

    val taskName = ":lib:compileDevelopmentExecutableKotlinJsZipline"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)

    val ziplineOut = projectDir.resolve(
      "lib/build/compileSync/js/main/developmentExecutable/kotlinZipline",
    )
    val manifest = ziplineOut.resolve(manifestFileName)
    assertThat(manifest.readText())
      .containsMatch(Regex(""""signatures":\{"key1":"\w{128}","key2":"\w{128}"}"""))
  }

  @Test
  fun generateZiplineManifestKeyPairEd25519() {
    val projectDir = File("src/test/projects/basic")

    val result = createRunner(projectDir, "generateZiplineManifestKeyPairEd25519").build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(":lib:generateZiplineManifestKeyPairEd25519")!!.outcome)
    assertThat(result.output).containsMatch(
      Regex(
        """
        |      ALGORITHM: Ed25519
        |     PUBLIC KEY: [\da-f]{64}
        |    PRIVATE KEY: [\da-f]{64}
        |
        """.trimMargin(),
      ),
    )
  }

  @Test
  fun generateZiplineManifestKeyPairEcdsaP256() {
    val projectDir = File("src/test/projects/basic")

    val result = createRunner(projectDir, "generateZiplineManifestKeyPairEcdsaP256").build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(":lib:generateZiplineManifestKeyPairEcdsaP256")!!.outcome)
    // Expected lengths were determined experimentally!
    assertThat(result.output).containsMatch(
      Regex(
        """
        |      ALGORITHM: EcdsaP256
        |     PUBLIC KEY: [\da-f]{130}
        |    PRIVATE KEY: [\da-f]{134}
        |
        """.trimMargin(),
      ),
    )
  }

  @Test
  fun ziplineApiDumpDoesNothingOnApiMatch() {
    ziplineApiTaskDoesNothingOnApiMatch(":lib:ziplineApiDump")
  }

  @Test
  fun ziplineApiCheckDoesNothingOnApiMatch() {
    ziplineApiTaskDoesNothingOnApiMatch(":lib:ziplineApiCheck")
  }

  private fun ziplineApiTaskDoesNothingOnApiMatch(taskName: String) {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    val ziplineApiTomlContent = """
      |# This comment will be clobbered if this file is overwritten
      |# by the Gradle task.
      |
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |
      |  # This comment will also be clobbered on an unexpected update.
      |  "ipvircui",
      |]
      """.trimMargin()
    ziplineApiToml.writeText(ziplineApiTomlContent)

    try {
      createRunner(projectDir, "clean", taskName).build()
      assertThat(ziplineApiToml.readText())
        .isEqualTo(ziplineApiTomlContent)
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiCheckFailsOnDroppedApi() {
    ziplineApiTaskFailsOnDroppedApi(":lib:ziplineApiCheck")
  }

  @Test
  fun ziplineApiDumpFailsOnDroppedApi() {
    ziplineApiTaskFailsOnDroppedApi(":lib:ziplineApiDump")
  }

  @Test
  fun checkTaskIncludesZiplineApiCheck() {
    ziplineApiTaskFailsOnDroppedApi(":lib:check")
  }

  private fun ziplineApiTaskFailsOnDroppedApi(taskName: String) {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    // Expect an API that contains a function not offered.
    ziplineApiToml.writeText(
      """
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |
      |  # fun greet(kotlin.String): kotlin.String
      |  "ipvircui",
      |
      |  # fun hello(kotlin.String): kotlin.String
      |  "Cw62Cti7",
      |]
      |
      """.trimMargin(),
    )

    try {
      val result = createRunner(projectDir, "clean", taskName).buildAndFail()
      assertThat(result.output).contains(
        """
        |  Expected function Cw62Cti7 of app.cash.zipline.tests.GreetService not found:
        |    fun hello(kotlin.String): kotlin.String
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiDumpCreatesNewTomlFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.delete() // In case a previous execution crashed.

    try {
      val taskName = ":lib:ziplineApiDump"
      createRunner(projectDir, "clean", taskName).build()
      assertThat(ziplineApiToml.readText()).isEqualTo(
        """
        |[app.cash.zipline.tests.GreetService]
        |
        |functions = [
        |  # fun close(): kotlin.Unit
        |  "moYx+T3e",
        |
        |  # fun greet(kotlin.String): kotlin.String
        |  "ipvircui",
        |]
        |
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiCheckFailsOnMissingTomlFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.delete() // In case a previous execution crashed.

    val taskName = ":lib:ziplineApiCheck"
    val result = createRunner(projectDir, "clean", taskName).buildAndFail()
    assertThat(result.output).contains(
      """
      |Zipline API file is incomplete. Run :ziplineApiDump to update it.
      |  api/zipline-api.toml
      """.trimMargin(),
    )
  }

  @Test
  fun ziplineApiDumpUpdatesIncompleteFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    // Expect an API that doesn't declare 'greet'.
    ziplineApiToml.writeText(
      """
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |]
      |
      """.trimMargin(),
    )

    try {
      val taskName = ":lib:ziplineApiDump"
      createRunner(projectDir, "clean", taskName).build()

      // The task updates the file to include the 'greet' function.
      assertThat(ziplineApiToml.readText()).isEqualTo(
        """
        |[app.cash.zipline.tests.GreetService]
        |
        |functions = [
        |  # fun close(): kotlin.Unit
        |  "moYx+T3e",
        |
        |  # fun greet(kotlin.String): kotlin.String
        |  "ipvircui",
        |]
        |
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  @Test
  fun ziplineApiCheckFailsOnIncompleteFile() {
    val projectDir = File("src/test/projects/basic")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.parentFile.mkdirs()

    // Expect an API that doesn't declare 'greet'.
    ziplineApiToml.writeText(
      """
      |[app.cash.zipline.tests.GreetService]
      |
      |functions = [
      |  # fun close(): kotlin.Unit
      |  "moYx+T3e",
      |]
      |
      """.trimMargin(),
    )

    try {
      val taskName = ":lib:ziplineApiCheck"
      val result = createRunner(projectDir, "clean", taskName).buildAndFail()
      assertThat(result.output).contains(
        """
        |Zipline API file is incomplete. Run :ziplineApiDump to update it.
        |  api/zipline-api.toml
        """.trimMargin(),
      )
    } finally {
      ziplineApiToml.delete()
    }
  }

  /** Confirm ziplineApiDump tasks are available on non-JVM projects. */
  @Test
  fun ziplineApiDumpOnAndroidProject() {
    val projectDir = File("src/test/projects/android")
    val ziplineApiToml = projectDir.resolve("lib/api/zipline-api.toml")
    ziplineApiToml.delete() // In case a previous execution crashed.

    try {
      val taskName = ":lib:ziplineApiDump"
      createRunner(projectDir, "clean", taskName).build()
      assertThat(ziplineApiToml.exists()).isTrue()
    } finally {
      ziplineApiToml.delete()
    }
  }

  private fun createRunner(projectDir: File, vararg taskNames: String): GradleRunner {
    val gradleRoot = projectDir.resolve("gradle").also { it.mkdir() }
    File("../gradle/wrapper").copyRecursively(gradleRoot.resolve("wrapper"), true)
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments("--info", "--stacktrace", "--continue", *taskNames, versionProperty)
      .forwardOutput()
  }

  companion object {
    val SUCCESS_OUTCOMES = listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    val versionProperty = "-PziplineVersion=${System.getProperty("ziplineVersion")}"

    @Suppress("INVISIBLE_MEMBER") // Access :zipline internals.
    private val manifestFileName = app.cash.zipline.loader.internal.MANIFEST_FILE_NAME
  }
}
