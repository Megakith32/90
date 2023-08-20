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

import app.cash.zipline.internal.bridge.inboundChannelName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Confirm connectivity from Kotlin to a JavaScript implementation of CallChannel.
 *
 * This low-level test uses raw JavaScript to test connectivity. In practice this interface will
 * only ever be used by Kotlin/JS.
 */
class QuickJsInboundChannelTest {
  private val quickJs = QuickJs.create()

  @AfterTest fun tearDown() {
    quickJs.close()
  }

  @BeforeTest
  fun setUp() {
    quickJs.evaluate("""
      globalThis.$inboundChannelName = {};
      globalThis.$inboundChannelName.serviceNamesArray = function() {
      };
      globalThis.$inboundChannelName.call = function(jstring) {
      };
      globalThis.$inboundChannelName.disconnect = function(instanceName) {
      };
    """.trimIndent())
  }

  @Test
  fun callHappyPath() {
    quickJs.evaluate("""
      globalThis.$inboundChannelName.call = function(callJson) {
        return 'received call(' + callJson + ') and the call was successful!';
      };
    """.trimIndent())

    val inboundChannel = quickJs.getInboundChannel()
    val result = inboundChannel.call("firstArg")
    assertEquals("received call(firstArg) and the call was successful!", result)
  }

  @Test
  fun serviceNamesArrayHappyPath() {
    quickJs.evaluate("""
      var callLog = [];
      globalThis.$inboundChannelName.serviceNamesArray = function() {
        return ['service one', 'service two'];
      };
    """.trimIndent())

    val inboundChannel = quickJs.getInboundChannel()
    val result = inboundChannel.serviceNamesArray()
    assertContentEquals(
      arrayOf(
        "service one",
        "service two",
      ),
      result,
    )
  }

  @Test
  fun disconnectHappyPath() {
    quickJs.evaluate("""
      var callLog = "";
      globalThis.$inboundChannelName.call = function(callJson) {
        var result = callLog;
        callLog = "";
        return result;
      };
      globalThis.$inboundChannelName.disconnect = function(instanceName) {
        callLog += 'disconnect(' + instanceName + ')';
        return true;
      };
    """.trimIndent())

    val inboundChannel = quickJs.getInboundChannel()
    assertTrue(inboundChannel.disconnect("service one"))
    val result = inboundChannel.call("")
    assertEquals("disconnect(service one)", result)
  }

  @Test
  fun noInboundChannelThrows() {
    quickJs.evaluate("""
      delete globalThis.$inboundChannelName;
    """.trimIndent())

    val t = assertFailsWith<IllegalStateException> {
      quickJs.getInboundChannel()
    }
    assertEquals("A global JavaScript object called $inboundChannelName was not found. Try confirming that Zipline.get() has been called.", t.message)
  }

  @Test
  fun callFunctionAfterClosingQuickJsThrows() {
    val inboundChannel = quickJs.getInboundChannel()
    quickJs.close()

    val t = assertFailsWith<IllegalStateException> {
      inboundChannel.disconnect("service one")
    }
    assertEquals("QuickJs instance was closed", t.message)
  }
}
