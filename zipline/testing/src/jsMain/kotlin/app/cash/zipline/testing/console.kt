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

@JsExport
fun consoleLogAllLevels() {
  console.info("1. this is message 1 of 5. Its level is 'info'.")
  console.log("2. this message has level 'log'.")
  console.warn("3. this message has level 'warn'.")
  console.error("4. this message has level 'error'.")
  console.info("5. this is the last message")
}

@JsExport
fun consoleLogWithArguments() {
  console.info("this message for %s is a %d out of %d", "Jesse", 8, 10)
}
