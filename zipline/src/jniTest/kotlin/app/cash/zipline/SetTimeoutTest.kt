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

import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SetTimeoutTest {
  @Rule @JvmField val ziplineTestRule = ZiplineTestRule()
  private val dispatcher = ziplineTestRule.dispatcher
  private val zipline = Zipline.create(dispatcher)

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")
  }

  @After fun tearDown() = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun happyPath() = runBlocking(dispatcher) {
    zipline.quickJs.evaluate(
      """
      var greeting = 'hello';

      var sayGoodbye = function() {
        greeting = 'goodbye';
      };

      setTimeout(sayGoodbye, 100);
      """
    )

    assertEquals("hello", zipline.quickJs.evaluate("greeting"))
    delay(200L)
    assertEquals("goodbye", zipline.quickJs.evaluate("greeting"))
  }

  @Test fun ziplineCloseSilentlyCancelsQueuedTasks(): Unit = runBlocking(dispatcher) {
    zipline.quickJs.evaluate(
      """
      var doNothing = function() {
      };

      setTimeout(doNothing, 100);
      """
    )

    zipline.close()
    delay(200L)
  }
}
