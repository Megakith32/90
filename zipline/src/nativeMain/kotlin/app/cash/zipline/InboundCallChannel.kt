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

import app.cash.zipline.internal.bridge.CallChannel
import app.cash.zipline.internal.bridge.inboundChannelName
import app.cash.zipline.quickjs.JS_FreeAtom
import app.cash.zipline.quickjs.JS_FreeValue
import app.cash.zipline.quickjs.JS_GetGlobalObject
import app.cash.zipline.quickjs.JS_GetPropertyStr
import app.cash.zipline.quickjs.JS_Invoke
import app.cash.zipline.quickjs.JS_NewAtom
import app.cash.zipline.quickjs.JS_NewString
import kotlinx.cinterop.memScoped

internal class InboundCallChannel(
  private val quickJs: QuickJs,
) : CallChannel {
  private val context = quickJs.context

  override fun serviceNamesArray(): Array<String> {
    quickJs.checkNotClosed()

    val globalThis = JS_GetGlobalObject(context)
    val inboundChannel = JS_GetPropertyStr(context, globalThis, inboundChannelName)
    val property = JS_NewAtom(context, "serviceNamesArray")

    val jsResult = JS_Invoke(context, inboundChannel, property, 0, null)
    @Suppress("UNCHECKED_CAST") // Our JS implementation returns an Array<String>.
    val kotlinResult = with(quickJs) { jsResult.toKotlinInstanceOrNull() } as Array<String>

    JS_FreeValue(context, jsResult)
    JS_FreeAtom(context, property)
    JS_FreeValue(context, inboundChannel)
    JS_FreeValue(context, globalThis)

    return kotlinResult
  }

  override fun call(callJson: String): String {
    quickJs.checkNotClosed()

    val globalThis = JS_GetGlobalObject(context)
    val inboundChannel = JS_GetPropertyStr(context, globalThis, inboundChannelName)
    val property = JS_NewAtom(context, "call")
    val arg0 = JS_NewString(context, callJson)

    val jsResult = memScoped {
      val args = allocArrayOf(arg0)
      JS_Invoke(context, inboundChannel, property, 1, args)
    }
    val kotlinResult = with(quickJs) { jsResult.toKotlinInstanceOrNull() } as String

    JS_FreeValue(context, jsResult)
    JS_FreeValue(context, arg0)
    JS_FreeAtom(context, property)
    JS_FreeValue(context, inboundChannel)
    JS_FreeValue(context, globalThis)

    return kotlinResult
  }

  override fun disconnect(instanceName: String): Boolean {
    quickJs.checkNotClosed()

    val globalThis = JS_GetGlobalObject(context)
    val inboundChannel = JS_GetPropertyStr(context, globalThis, inboundChannelName)
    val property = JS_NewAtom(context, "disconnect")
    val arg0 = JS_NewString(context, instanceName)

    val jsResult = memScoped {
      val args = allocArrayOf(arg0)
      JS_Invoke(context, inboundChannel, property, 1, args)
    }
    val kotlinResult = with(quickJs) { jsResult.toKotlinInstanceOrNull() } as Boolean

    JS_FreeValue(context, jsResult)
    JS_FreeAtom(context, property)
    JS_FreeValue(context, inboundChannel)
    JS_FreeValue(context, globalThis)

    return kotlinResult
  }
}
