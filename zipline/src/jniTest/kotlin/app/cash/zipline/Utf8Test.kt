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

import app.cash.zipline.testing.Formatter
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * This test attempts to transmit strings through each of our supported mechanisms and confirms that
 * each doesn't mangle content. We've had bugs where non-ASCII characters weren't encoded properly.
 */
class Utf8Test {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)
  private val quickjs = zipline.quickJs

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun nonAsciiInInputAndOutput(): Unit = runBlocking(dispatcher) {
    assertEquals("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)",
        quickjs.evaluate("var s = \"a\uD83D\uDC1Dcdefg\"; '(' + s + ', ' + s + ')';"))
  }

  @Test fun nonAsciiInFileName(): Unit = runBlocking(dispatcher) {
    val t = assertFailsWith<QuickJsException> {
      quickjs.evaluate("""
        |f1();
        |
        |function f1() {
        |  nope();
        |}
        |""".trimMargin(), "a\uD83D\uDC1Dcdefg.js")
      quickjs.evaluate("formatter.format();")
    }
    assertEquals("JavaScript.f1(a\uD83D\uDC1Dcdefg.js:4)", t.stackTrace[0].toString())
  }

  @Test fun nonAsciiInboundCalls(): Unit = runBlocking(dispatcher) {
    quickjs.evaluate("testing.app.cash.zipline.testing.prepareNonAsciiInputAndOutput()")
    val formatter = zipline.get<Formatter>("formatter")
    assertEquals("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)", formatter.format("a\uD83D\uDC1Dcdefg"))
  }

  @Test fun nonAsciiOutboundCalls(): Unit = runBlocking(dispatcher) {
    zipline.set<Formatter>("formatter", object : Formatter {
      override fun format(message: String): String {
        return "($message, $message)"
      }
    })
    assertEquals("(a\uD83D\uDC1Dcdefg, a\uD83D\uDC1Dcdefg)",
        quickjs.evaluate("testing.app.cash.zipline.testing.callFormatter('a\uD83D\uDC1Dcdefg');"))
  }

  @Test fun nonAsciiInExceptionThrownInJs(): Unit = runBlocking(dispatcher) {
    quickjs.evaluate("testing.app.cash.zipline.testing.prepareNonAsciiThrower()")
    val formatter = zipline.get<Formatter>("formatter")
    val t = assertFailsWith<Exception> {
      formatter.format("")
    }
    assertThat(t).hasMessageThat().contains("a\uD83D\uDC1Dcdefg")
  }

  @Test fun nonAsciiInExceptionThrownInJava(): Unit = runBlocking(dispatcher) {
    zipline.set<Formatter>("formatter", object : Formatter {
      override fun format(message: String): String {
        throw RuntimeException("a\uD83D\uDC1Dcdefg")
      }
    })
    val t = assertFailsWith<RuntimeException> {
      quickjs.evaluate("testing.app.cash.zipline.testing.callFormatter('');")
    }
    assertEquals("java.lang.RuntimeException: a\uD83D\uDC1Dcdefg", t.message)
  }
}
