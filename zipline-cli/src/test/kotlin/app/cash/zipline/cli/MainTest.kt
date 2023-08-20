/*
 * Copyright (C) 2022 Square, Inc.
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
package app.cash.zipline.cli

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import picocli.CommandLine
import picocli.CommandLine.MissingParameterException

class MainTest {
  @Test fun downloadEmpty() {
    val exception = assertFailsWith<MissingParameterException> {
      fromArgs("download")
    }
    assertEquals("Missing required options: '--application-name=<applicationName>', '--manifest-url=<manifestUrl>', '--download-dir=<downloadDir>'", exception.message)
  }

  companion object {
    fun fromArgs(vararg args: String?): Main {
      return CommandLine.populateCommand(Main(), *args)
    }
  }
}
