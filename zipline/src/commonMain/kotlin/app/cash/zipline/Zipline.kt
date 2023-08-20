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

import kotlinx.serialization.modules.SerializersModule

expect abstract class Zipline {
  abstract val engineVersion: String

  /** Name of services that have been published with [set]. */
  abstract val serviceNames: Set<String>

  /** Names of services that can be consumed with [get]. */
  abstract val clientNames: Set<String>

  fun <T : Any> get(name: String, serializersModule: SerializersModule): T

  fun <T : Any> set(name: String, serializersModule: SerializersModule, instance: T)
}
