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

import app.cash.zipline.internal.CURRENT_MODULE_ID
import app.cash.zipline.internal.Console
import app.cash.zipline.internal.CoroutineEventLoop
import app.cash.zipline.internal.DEFINE_JS
import app.cash.zipline.internal.EventLoop
import app.cash.zipline.internal.HostConsole
import app.cash.zipline.internal.JsPlatform
import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.Endpoint
import app.cash.zipline.internal.bridge.InboundBridge
import app.cash.zipline.internal.bridge.OutboundBridge
import app.cash.zipline.internal.bridge.ZiplineServiceAdapter
import app.cash.zipline.internal.consoleName
import app.cash.zipline.internal.eventLoopName
import app.cash.zipline.internal.jsPlatformName
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

actual class Zipline private constructor(
  val quickJs: QuickJs,
  private val scope: CoroutineScope,
) {
  private val endpoint = Endpoint(
    scope = scope,
    outboundChannel = object : CallChannel {
      /** Lazily fetch the channel to call into JS. */
      private val jsInboundBridge: CallChannel by lazy(mode = LazyThreadSafetyMode.NONE) {
        quickJs.getInboundChannel()
      }

      override fun serviceNamesArray(): Array<String> {
        return jsInboundBridge.serviceNamesArray()
      }

      override fun invoke(
        instanceName: String,
        funName: String,
        encodedArguments: Array<String>
      ): Array<String> {
        check(scope.isActive) { "Zipline closed" }
        return jsInboundBridge.invoke(instanceName, funName, encodedArguments)
      }

      override fun invokeSuspending(
        instanceName: String,
        funName: String,
        encodedArguments: Array<String>,
        callbackName: String
      ) {
        check(scope.isActive) { "Zipline closed" }
        return jsInboundBridge.invokeSuspending(instanceName, funName, encodedArguments, callbackName)
      }

      override fun disconnect(instanceName: String): Boolean {
        return jsInboundBridge.disconnect(instanceName)
      }
    }
  )

  actual val serializersModule: SerializersModule
    get() = endpoint.userSerializersModule!!

  actual val serviceNames: Set<String>
    get() = endpoint.serviceNames

  actual val clientNames: Set<String>
    get() = endpoint.clientNames

  init {
    // Eagerly publish the channel so they can call us.
    quickJs.initOutboundChannel(endpoint.inboundChannel)

    endpoint.set<Console>(
      name = consoleName,
      instance = HostConsole,
    )

    // Connect platforms using our newly-bootstrapped channels.
    val jsPlatform = endpoint.get<JsPlatform>(
      name = jsPlatformName,
    )
    val eventLoop = CoroutineEventLoop(scope, jsPlatform)
    endpoint.set<EventLoop>(
      name = eventLoopName,
      instance = eventLoop,
    )
  }

  actual fun <T : Any> get(name: String): T {
    error("unexpected call to Zipline.get: is the Zipline plugin configured?")
  }

  actual fun <T : ZiplineService> getService(name: String): T {
    error("unexpected call to Zipline.getService: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> get(name: String, bridge: OutboundBridge<T>): T {
    check(scope.isActive) { "closed" }
    return endpoint.get(name, bridge)
  }

  @PublishedApi
  internal fun <T : ZiplineService> getService(name: String, adapter: ZiplineServiceAdapter<T>): T {
    check(scope.isActive) { "closed" }
    return endpoint.getService(name, adapter)
  }

  actual fun <T : Any> set(name: String, instance: T) {
    error("unexpected call to Zipline.set: is the Zipline plugin configured?")
  }

  actual fun <T : ZiplineService> setService(name: String, instance: T) {
    error("unexpected call to Zipline.setService: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : Any> set(name: String, bridge: InboundBridge<T>) {
    check(scope.isActive) { "closed" }
    endpoint.set(name, bridge)
  }

  @PublishedApi
  internal fun <T : ZiplineService> setService(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>,
  ) {
    check(scope.isActive) { "closed" }
    endpoint.setService(name, service, adapter)
  }

  /**
   * Release resources held by this instance. It is an error to do any of the following after
   * calling close:
   *
   *  * Call [get] or [set].
   *  * Accessing [quickJs].
   *  * Accessing the objects returned from [get].
   */
  fun close() {
    scope.cancel()
    quickJs.close()

    // Don't wait for a JS continuation to resume, it never will. Canceling `scope` doesn't do this
    // because each continuation is in its caller's scope.
    for (continuation in endpoint.incompleteContinuations) {
      continuation.resumeWithException(CancellationException("Zipline closed"))
    }
    endpoint.incompleteContinuations.clear()
  }

  fun loadJsModule(script: String, id: String) {
    quickJs.evaluate("globalThis.$CURRENT_MODULE_ID = '$id';")
    quickJs.evaluate(script, id)
    quickJs.evaluate("delete globalThis.$CURRENT_MODULE_ID;")
  }

  fun loadJsModule(bytecode: ByteArray, id: String) {
    quickJs.evaluate("globalThis.$CURRENT_MODULE_ID = '$id';")
    quickJs.execute(bytecode)
    quickJs.evaluate("delete globalThis.$CURRENT_MODULE_ID;")
  }

  companion object {
    fun create(
      dispatcher: CoroutineDispatcher,
      serializersModule: SerializersModule = EmptySerializersModule
    ): Zipline {
      val quickJs = QuickJs.create()
      // TODO(jwilson): figure out a 512 KiB limit caused intermittent stack overflow failures.
      quickJs.maxStackSize = 0L
      quickJs.evaluate(DEFINE_JS, "define.js")

      val scope = CoroutineScope(dispatcher)
      return Zipline(quickJs, scope)
        .apply {
          endpoint.userSerializersModule = serializersModule
        }
    }
  }
}
