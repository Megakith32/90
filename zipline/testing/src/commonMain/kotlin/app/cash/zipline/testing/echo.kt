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
package app.cash.zipline.testing

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

interface EchoService {
  fun echo(request: EchoRequest): EchoResponse
}

@Serializable
data class EchoRequest(
  val message: String
)

@Serializable
data class EchoResponse(
  val message: String
)

val EchoSerializersModule: SerializersModule = SerializersModule {
}
