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
package app.cash.zipline.testing

import app.cash.zipline.Zipline
import kotlinx.coroutines.delay

class JsSchedulerService : SchedulerService {
  override suspend fun schedule(delayMillis: Long, callback: SchedulerService.Callback): String {
    delay(delayMillis)
    return callback()
      .also {
        callback.close()
      }
  }
}

private val zipline by lazy { Zipline.get() }

@JsExport
fun prepareSchedulerService() {
  zipline.bind<SchedulerService>("schedulerService", JsSchedulerService())
}
