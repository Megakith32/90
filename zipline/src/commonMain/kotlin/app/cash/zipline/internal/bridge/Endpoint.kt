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
package app.cash.zipline.internal.bridge

import app.cash.zipline.EventListener
import app.cash.zipline.ZiplineService
import app.cash.zipline.internal.passByReferencePrefix
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * An outbound channel for delivering calls to the other platform, and an inbound channel for
 * receiving calls from the other platform.
 */
class Endpoint internal constructor(
  internal val scope: CoroutineScope,
  internal val userSerializersModule: SerializersModule,
  internal val eventListener: EventListener,
  internal val outboundChannel: CallChannel,
) {
  internal val inboundServices = mutableMapOf<String, InboundService<*>>()
  private var nextId = 1

  internal val incompleteContinuations = mutableSetOf<Continuation<*>>()

  val serviceNames: Set<String>
    get() = inboundServices.keys.toSet()

  val clientNames: Set<String>
    get() = outboundChannel.serviceNamesArray().toSet()

  /** This uses both Zipline-provided serializers and user-provided serializers. */
  internal val json: Json = Json {
    useArrayPolymorphism = true

    // For backwards-compatibility, allow new fields to be introduced.
    ignoreUnknownKeys = true

    // Because host and JS may disagree on default values, it's best to encode them.
    encodeDefaults = true

    // Support map keys whose values are arrays or objects.
    allowStructuredMapKeys = true

    serializersModule = SerializersModule {
      contextual(PassByReference::class, PassByReferenceSerializer(this@Endpoint))
      contextual(Throwable::class, ThrowableSerializer)
      include(userSerializersModule)
    }
  }

  internal val callCodec = CallCodec(this)

  internal val inboundChannel = object : CallChannel {
    override fun serviceNamesArray(): Array<String> {
      return serviceNames.toTypedArray()
    }

    override fun call(callJson: String): String {
      val internalCall = callCodec.decodeCall(callJson)
      val inboundService = internalCall.inboundService!!
      val externalCall = callCodec.lastInboundCall!!

      return when {
        internalCall.suspendCallback != null -> inboundService.callSuspending(
          internalCall,
          externalCall,
          internalCall.suspendCallback
        )
        else -> inboundService.call(
          internalCall,
          externalCall
        )
      }
    }

    override fun disconnect(instanceName: String): Boolean {
      return inboundServices.remove(instanceName) != null
    }
  }

  @Suppress("UNUSED_PARAMETER") // Parameters are used by the compiler plug-in.
  fun <T : ZiplineService> bind(name: String, instance: T) {
    error("unexpected call to Endpoint.bind: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> bind(
    name: String,
    service: T,
    adapter: ZiplineServiceAdapter<T>
  ) {
    eventListener.bindService(name, service)

    val functions = adapter.ziplineFunctions(json.serializersModule)
    inboundServices[name] = InboundService(service, this, functions)
  }

  @Suppress("UNUSED_PARAMETER") // Parameter is used by the compiler plug-in.
  fun <T : ZiplineService> take(name: String): T {
    error("unexpected call to Endpoint.take: is the Zipline plugin configured?")
  }

  @PublishedApi
  internal fun <T : ZiplineService> take(name: String, adapter: ZiplineServiceAdapter<T>): T {
    // Detect leaked old services when creating new services.
    detectLeaks()

    val functions = adapter.ziplineFunctions(json.serializersModule)
    val callHandler = OutboundCallHandler(name, this, functions)
    val result = adapter.outboundService(callHandler)
    eventListener.takeService(name, result)
    trackLeaks(eventListener, name, callHandler, result)
    return result
  }

  @PublishedApi
  internal fun remove(name: String): InboundService<*>? {
    return inboundServices.remove(name)
  }

  @PublishedApi
  internal fun remove(service: ZiplineService) {
    val i = inboundServices.values.iterator()
    while (i.hasNext()) {
      val inboundService = i.next()
      if (inboundService.service === service) {
        i.remove()
        return
      }
    }
  }

  internal fun generatePassByReferenceName(): String {
    return "$passByReferencePrefix${nextId++}"
  }
}
