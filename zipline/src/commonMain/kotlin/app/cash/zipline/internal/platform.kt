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
package app.cash.zipline.internal

import app.cash.zipline.ZiplineService
import kotlinx.serialization.Serializable

const val ziplineInternalPrefix = "zipline/"
internal expect val passByReferencePrefix: String
internal const val eventLoopName = "${ziplineInternalPrefix}event_loop"
internal const val httpClientName = "${ziplineInternalPrefix}http_client"
internal const val consoleName = "${ziplineInternalPrefix}console"
internal const val eventListenerName = "${ziplineInternalPrefix}event_listener"
internal const val jsPlatformName = "${ziplineInternalPrefix}js"

internal interface EventLoop : ZiplineService {
  fun setTimeout(timeoutId: Int, delayMillis: Int)
  fun clearTimeout(timeoutId: Int)
}

internal interface HttpClient : ZiplineService {
  suspend fun execute(request: Request): Response

  @Serializable
  data class Request(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
  )
  @Serializable
  data class Response(
    val status: Short,
    val statusText: String,
    val headers: Map<String, String>,
  )
}

internal interface Console : ZiplineService {
  /** @param level one of `log`, `info`, `warn`, or `error`. */
  fun log(level: String, message: String, throwable: Throwable?)
}

internal interface JsPlatform : ZiplineService {
  fun runJob(timeoutId: Int)
}

/**
 * Forward select events to the host event listener. We don't need to bridge all functions because
 * the peer already sees its side of those calls.
 */
internal interface EventListenerService : ZiplineService {
  fun serviceLeaked(name: String)
}
