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

import app.cash.zipline.testing.AdaptersRequest
import app.cash.zipline.testing.AdaptersRequestSerializersModule
import app.cash.zipline.testing.AdaptersResponse
import app.cash.zipline.testing.AdaptersResponseSerializersModule
import app.cash.zipline.testing.AdaptersSerializersModule
import app.cash.zipline.testing.AdaptersService
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerializersTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val zipline = Zipline.create(dispatcher, AdaptersSerializersModule)
  private val ziplineRequestOnly = Zipline.create(dispatcher, AdaptersRequestSerializersModule)
  private val ziplineResponseOnly = Zipline.create(dispatcher, AdaptersResponseSerializersModule)

  @Before fun setUp() = runBlocking {
    zipline.loadTestingJs()
    ziplineRequestOnly.loadTestingJs()
    ziplineResponseOnly.loadTestingJs()
  }

  @After fun tearDown() = runBlocking {
    zipline.close()
    ziplineRequestOnly.close()
    ziplineResponseOnly.close()
  }

  @Test fun missingGetReturnValueSerializerFailsFast() = runBlocking {
    assertThat(assertFailsWith<IllegalArgumentException> {
      ziplineRequestOnly.take<AdaptersService>("adaptersService")
    }).hasMessageThat().contains("Serializer for class 'AdaptersResponse' is not found.")
  }

  @Test fun missingGetParameterSerializerFailsFast() = runBlocking {
    assertThat(assertFailsWith<IllegalArgumentException> {
      ziplineResponseOnly.take<AdaptersService>("adaptersService")
    }).hasMessageThat().contains("Serializer for class 'AdaptersRequest' is not found.")
  }

  @Test fun presentGetSerializersSucceeds() = runBlocking {
    val service = zipline.take<AdaptersService>("adaptersService")
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareAdaptersJsBridges()")

    assertThat(service.echo(AdaptersRequest("Andrew")))
      .isEqualTo(AdaptersResponse("thank you for using your serializers, Andrew"))
  }

  @Test fun missingSetReturnValueSerializerFailsFast() = runBlocking {
    assertThat(assertFailsWith<IllegalArgumentException> {
      ziplineRequestOnly.bind<AdaptersService>(
        "adaptersService",
        JvmAdaptersService()
      )
    }).hasMessageThat().contains("Serializer for class 'AdaptersResponse' is not found.")
  }

  @Test fun missingSetParameterSerializerFailsFast() = runBlocking {
    assertThat(assertFailsWith<IllegalArgumentException> {
      ziplineResponseOnly.bind<AdaptersService>(
        "adaptersService",
        JvmAdaptersService()
      )
    }).hasMessageThat().contains("Serializer for class 'AdaptersRequest' is not found.")
  }

  @Test fun presentSetSerializersSucceeds() = runBlocking {
    zipline.bind<AdaptersService>(
      "adaptersService",
      JvmAdaptersService()
    )
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callAdaptersService()"))
      .isEqualTo("JavaScript received nice adapters, Jesse")
  }

  private class JvmAdaptersService : AdaptersService {
    override fun echo(request: AdaptersRequest): AdaptersResponse {
      return AdaptersResponse("nice adapters, ${request.message}")
    }
  }
}
