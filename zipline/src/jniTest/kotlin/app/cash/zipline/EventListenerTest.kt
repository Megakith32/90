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
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * This test exercises event listeners using QuickJS.
 */
class EventListenerTest {
  @Rule @JvmField val ziplineTestRule = ZiplineTestRule()
  private val dispatcher = ziplineTestRule.dispatcher
  private val eventListener = LoggingEventListener()
  private val zipline = Zipline.create(dispatcher, eventListener = eventListener)

  @Before fun setUp(): Unit = runBlocking(dispatcher) {
    zipline.loadTestingJs()
    eventListener.takeAll() // Skip events created by loadTestingJs().
  }

  @After fun tearDown(): Unit = runBlocking(dispatcher) {
    zipline.close()
  }

  @Test fun jvmCallJsService() = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    val helloService = zipline.take<EchoService>("helloService")
    assertThat(helloService.echo(EchoRequest("Jake")))
      .isEqualTo(EchoResponse("hello from JavaScript, Jake"))

    val name = "helloService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[EchoRequest(message=Jake)]"
    assertThat(eventListener.take()).isEqualTo("takeService $name")
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=hello from JavaScript, Jake))")
  }

  @Test fun jsCallJvmService() = runBlocking(dispatcher) {
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

  @Test fun suspendingJvmCallJsService() = runBlocking(dispatcher) {
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

  @Test fun suspendingJsCallJvmService() = runBlocking(dispatcher) {
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
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).isEqualTo("callEnd 1 $name $funName $request Success(EchoResponse(message=hello from the suspending JVM, Eric))")
  }

  @Test fun jvmCallIncompatibleJsService() = runBlocking(dispatcher) {
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareJsBridges()")

    assertThat(assertFailsWith<ZiplineApiMismatchException> {
      zipline.take<PotatoService>("helloService").echo()
    }).hasMessageThat().startsWith("""
      no such method (incompatible API versions?)
      	called service:
      		helloService
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

  @Test fun jvmCallUnknownJsService() = runBlocking(dispatcher) {
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

  @Test fun jsCallIncompatibleJvmService() = runBlocking(dispatcher) {
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

  @Test fun jsCallUnknownJvmService() = runBlocking(dispatcher) {
    assertThat(assertFailsWith<QuickJsException> {
      zipline.quickJs.evaluate("testing.app.cash.zipline.testing.callSupService('homie')")
    }).hasMessageThat().startsWith("app.cash.zipline.ZiplineApiMismatchException: no such service")

    val name = "supService"
    val funName = "fun echo(app.cash.zipline.testing.EchoRequest): app.cash.zipline.testing.EchoResponse"
    val request = "[]"
    assertThat(eventListener.take()).isEqualTo("callStart 1 $name $funName $request")
    assertThat(eventListener.take()).startsWith("callEnd 1 $name $funName $request Failure(app.cash.zipline.ZiplineApiMismatchException: no such service")
  }

  @Test fun ziplineClosed() = runBlocking(dispatcher) {
    zipline.close()
    assertThat(eventListener.take()).isEqualTo("ziplineClosed")

    // Close is idempotent and doesn't repeat events.
    zipline.close()
    assertThat(eventListener.takeAll()).isEmpty()
  }

  /**
   * We had a bug where EventListeners that called [ZiplineService.toString] would trigger a crash
   * on a lateinit value in the suspend callback.
   */
  @Test fun serviceToStrings() = runBlocking(dispatcher) {
    val outboundServiceToString =
      "SuspendingEchoService\$Companion\$Adapter\$GeneratedOutboundService"
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.prepareSuspendingJsBridges()")
    zipline.quickJs.evaluate("testing.app.cash.zipline.testing.unblockSuspendingJs()")

    val service = zipline.take<SuspendingEchoService>("jsSuspendingEchoService")
    service.suspendingEcho(EchoRequest("Jake"))

    val event1 = eventListener.takeEntry(skipInternalServices = false)
    assertThat(event1.log).isEqualTo("takeService jsSuspendingEchoService")
    assertThat(event1.serviceToString)
      .contains(outboundServiceToString)

    val event2 = eventListener.takeEntry(skipInternalServices = false)
    assertThat(event2.log).startsWith("bindService zipline/host-1")
    assertThat(event2.serviceToString)
      .startsWith("SuspendCallback/Call(receiver=jsSuspendingEchoService")

    val event3 = eventListener.takeEntry(skipInternalServices = false)
    assertThat(event3.log).startsWith("callStart 1 jsSuspendingEchoService")
    assertThat(event3.serviceToString)
      .contains(outboundServiceToString)
  }
}
