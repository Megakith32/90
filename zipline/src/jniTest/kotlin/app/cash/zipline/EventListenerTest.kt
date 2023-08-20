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
package app.cash.zipline

import app.cash.zipline.testing.EchoRequest
import app.cash.zipline.testing.EchoResponse
import app.cash.zipline.testing.EchoService
import app.cash.zipline.testing.LoggingEventListener
import app.cash.zipline.testing.PotatoService
import app.cash.zipline.testing.SuspendingEchoService
import app.cash.zipline.testing.helloService
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * This test exercises event listeners using QuickJS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventListenerTest {
  private val dispatcher = TestCoroutineDispatcher()
  private val eventListener = LoggingEventListener()
  private val zipline = Zipline.create(dispatcher, eventListener = eventListener)
  private val uncaughtExceptionHandler = TestUncaughtExceptionHandler()

  @Before fun setUp() = runBlocking {
    zipline.loadTestingJs()
    uncaughtExceptionHandler.setUp()
  }

  @After fun tearDown() = runBlocking {
    zipline.close()
    uncaughtExceptionHandler.tearDown()
  }

  @Test fun jvmCallJsService() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    assertThat(zipline.helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))

    val name = "helloService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=Jake)]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=hello from JavaScript, Jake))")
  }

  @Test fun jsCallJvmService() = runBlocking {
    val jvmEchoService = object : EchoService {
      override fun echo(request: EchoRequest): EchoResponse {
        return EchoResponse("sup from the JVM, ${request.message}")
      }
    }
    zipline.bind<EchoService>("supService", jvmEchoService)

    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')"))
      .isEqualTo("JavaScript received 'sup from the JVM, homie' from the JVM")

    val name = "supService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=homie)]"
    assertThat(eventListener.take()).isEqualTo("bindService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=sup from the JVM, homie))")
  }

  @Test fun suspendingJvmCallJsService() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSuspendingJsBridges()")
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.unblockSuspendingJs()")

    val jsSuspendingEchoService = zipline.take<SuspendingEchoService>("jsSuspendingEchoService")

    jsSuspendingEchoService.suspendingEcho(EchoRequest("Jake"))

    val name = "jsSuspendingEchoService"
    val funName = "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=Jake)]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=hello from suspending JavaScript, Jake))")
  }

  @Test fun suspendingJsCallJvmService() = runBlocking {
    val jvmSuspendingEchoService = object : SuspendingEchoService {
      override suspend fun suspendingEcho(request: EchoRequest): EchoResponse {
        return EchoResponse("hello from the suspending JVM, ${request.message}")
      }
    }

    zipline.bind<SuspendingEchoService>(
      "jvmSuspendingEchoService",
      jvmSuspendingEchoService
    )

    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSuspendingEchoService('Eric')")
    assertThat(zipline.quickJs.evaluate("testing.app.cash.zipline.testing.suspendingEchoResult"))
      .isEqualTo("hello from the suspending JVM, Eric")

    val name = "jvmSuspendingEchoService"
    val funName = "suspend fun suspendingEcho(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=Eric)]"
    assertThat(eventListener.take()).isEqualTo("bindService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 3 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 3 $name $funName $request Success(EchoResponse(message=hello from the suspending JVM, Eric))")
  }

  @Test fun jvmCallIncompatibleJsService() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    assertThat(assertFailsWith<ZiplineApiMismatchException> {
      zipline.take<PotatoService>("helloService").echo()
    }).hasMessageThat().startsWith("""
      no such method (incompatible API versions?)
      	called function:
      		fun echo(): app.cash.zipline.testing.EchoResponse
      	available functions:
      		fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse
      		fun close(): kotlin.Unit
     		at
      """.trimIndent()
    )
    val name = "helloService"
    val funName = "fun echo(): app.cash.zipline.testing.EchoResponse"
    val request = "[]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException: no such method")
  }

  @Test fun jvmCallUnknownJsService() = runBlocking {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.initZipline()")

    assertThat(assertFailsWith<ZiplineApiMismatchException> {
      zipline.take<EchoService>("helloService").echo(EchoRequest("hello"))
    }).hasMessageThat().startsWith("""
        no such service (service closed?)
        	called service:
        		helloService
        	available services:
        		zipline/js
      """.trimIndent()
    )
    val name = "helloService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=hello)]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException: no such service")
  }

  @Test fun jsCallIncompatibleJvmService() = runBlocking {
    val jvmPotatoService = object : PotatoService {
      override fun echo(): EchoResponse {
        error("unexpected call")
      }
    }
    zipline.bind<PotatoService>("supService", jvmPotatoService)

    assertThat(assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')")
    }).hasMessageThat().startsWith("app.cash.zipline.ZiplineApiMismatchException: no such method")

    val name = "supService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[]"
    assertThat(eventListener.take()).isEqualTo("bindService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException: no such method")
  }

  @Test fun jsCallUnknownJvmService() = runBlocking {
    assertThat(assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')")
    }).hasMessageThat().startsWith("app.cash.zipline.ZiplineApiMismatchException: no such service")

    val name = "supService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[]"
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException: no such service")
  }
}
