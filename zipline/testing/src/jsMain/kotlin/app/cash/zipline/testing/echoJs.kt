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

import app.cash.zipline.Zipline

class JsEchoService(
  private val greeting: String
) : EchoService {
  override fun echo(request: EchoRequest): EchoResponse {
    return EchoResponse("$greeting from JavaScript, ${request.message}")
  }
}

private val zipline by lazy { Zipline.get() }

@JsExport
fun prepareJsBridges() {
  zipline.set<EchoService>("helloService", JsEchoService("hello"))
  zipline.set<EchoService>("yoService", JsEchoService("yo"))
}

@JsExport
fun callSupService(message: String): String {
  val supService = zipline.get<EchoService>("supService")
  val echoResponse = supService.echo(EchoRequest(message))
  return "JavaScript received '${echoResponse.message}' from the JVM"
}

class JsThrowingEchoService : EchoService {
  override fun echo(request: EchoRequest): EchoResponse {
    goBoom3()
  }
  private fun goBoom3(): Nothing {
    goBoom2()
  }
  private fun goBoom2(): Nothing {
    goBoom1()
  }
  private fun goBoom1(): Nothing {
    throw IllegalStateException("boom!")
  }
}

@JsExport
fun prepareThrowingJsBridges() {
  zipline.set<EchoService>("helloService", JsThrowingEchoService())
}
