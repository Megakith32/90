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

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * This exercises the code in [app.cash.zipline.internal.defineJs] to confirm that Zipline can load
 * modules in the standard forms that the Kotlin compiler produces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadJsModuleTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher)

  @Before fun setUp() = runBlocking {
  }

  @After fun tearDown() = runBlocking {
    zipline.close()
  }

  @Test fun factoryPopulatesExports() = runBlocking {
    val moduleJs = """
      (function (root, factory) {
        if (typeof define === 'function' && define.amd)
          define(['exports'], factory);
        else if (typeof exports === 'object')
          factory(module.exports);
        else
          root['example'] = factory(typeof this['example'] === 'undefined' ? {} : this['example']);
      }(this, function (_) {
        _.someValue = 4321;
      }));
      """.trimIndent()
    zipline.loadJsModule(moduleJs, "example")
    assertThat(zipline.quickJs.evaluate("JSON.stringify(require('example'))"))
      .isEqualTo("""{"someValue":4321}""")
  }

  @Test fun factoryReturnsExports() = runBlocking {
    val moduleJs = """
      (function webpackUniversalModuleDefinition(root, factory) {
        if(typeof exports === 'object' && typeof module === 'object')
          module.exports = factory();
        else if(typeof define === 'function' && define.amd)
          define([], factory);
        else if(typeof exports === 'object')
          exports["example"] = factory();
        else
          root["example"] = factory();
      })(this, function() {
        return {
          someValue: 1234
        };
      });
      """.trimIndent()
    zipline.loadJsModule(moduleJs, "example")
    assertThat(zipline.quickJs.evaluate("JSON.stringify(require('example'))"))
      .isEqualTo("""{"someValue":1234}""")
  }

  @Test fun loadModuleBytecode() = runBlocking {
    val moduleJs = """
      define(function () {
        return {
          bytecodeValue: 5678
        };
      });
      """.trimIndent()
    val bytecode = zipline.quickJs.compile(moduleJs, "example.js")
    zipline.loadJsModule(bytecode, "example")
    assertThat(zipline.quickJs.evaluate("JSON.stringify(require('example'))"))
      .isEqualTo("""{"bytecodeValue":5678}""")
  }
}
