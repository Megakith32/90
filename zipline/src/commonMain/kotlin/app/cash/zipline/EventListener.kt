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

/**
 * Listener for metrics and debugging.
 *
 * All event methods must execute fast, without external locking, cannot throw exceptions, attempt
 * to mutate the event parameters, or be re-entrant back into the client. Any IO - writing to files
 * or network should be done asynchronously.
 */
abstract class EventListener {
  /** Invoked when something calls [Zipline.bind], or a service is sent via an API. */
  open fun bindService(zipline: Zipline, name: String, service: ZiplineService) {
  }

  /** Invoked when something calls [Zipline.take], or a service is received via an API. */
  open fun takeService(zipline: Zipline, name: String, service: ZiplineService) {
  }

  /**
   * Invoked when a service function is called. This may be invoked for either suspending or
   * non-suspending functions.
   *
   * @return any object. This value will be passed back to [callEnd] when the call is completed. The
   *   base function always returns null.
   */
  open fun callStart(zipline: Zipline, call: Call): Any? {
    return null
  }

  /**
   * Invoked when a service function call completes.
   *
   * @param startValue the value returned by [callStart] for the start of this call. This is null
   *   unless [callStart] is overridden to return something else.
   */
  open fun callEnd(zipline: Zipline, call: Call, result: CallResult, startValue: Any?) {
  }

  /**
   * Invoked when a service is garbage collected without being closed.
   *
   * Note that this method may be invoked after [ziplineClosed].
   */
  open fun serviceLeaked(zipline: Zipline, name: String) {
  }

  /**
   * Invoked when an application load starts.
   *
   * @return any object. This value will be passed back to [applicationLoadEnd] or
   *   [applicationLoadFailed] when the load is completed. The base function always returns null.
   */
  open fun applicationLoadStart(
    applicationName: String,
    manifestUrl: String?,
  ): Any? {
    return null
  }

  /**
   * Invoked when an application load was skipped because the code is unchanged.
   *
   * @param startValue the value returned by [applicationLoadStart] for the start of this call. This
   *   is null unless [applicationLoadStart] is overridden to return something else.
   */
  open fun applicationLoadSkipped(
    applicationName: String,
    manifestUrl: String,
    startValue: Any?,
  ) {
  }

  /**
   * Invoked when an application load succeeds.
   *
   * @param startValue the value returned by [applicationLoadStart] for the start of this call. This
   *   is null unless [applicationLoadStart] is overridden to return something else.
   */
  open fun applicationLoadSuccess(
    applicationName: String,
    manifestUrl: String?,
    zipline: Zipline,
    startValue: Any?,
  ) {
  }

  /**
   * Invoked when an application load fails.
   *
   * @param startValue the value returned by [applicationLoadStart] for the start of this call. This
   *   is null unless [applicationLoadStart] is overridden to return something else.
   */
  open fun applicationLoadFailed(
    applicationName: String,
    manifestUrl: String?,
    exception: Exception,
    startValue: Any?,
  ) {
  }

  /** Invoked when a network download starts */
  open fun downloadStart(
    applicationName: String,
    url: String,
  ): Any? {
    return null
  }

  /**
   * Invoked when a network download succeeds.
   *
   * @param startValue the value returned by [downloadStart] for the start of this call. This is
   *   null unless [downloadStart] is overridden to return something else.
   */
  open fun downloadEnd(
    applicationName: String,
    url: String,
    startValue: Any?,
  ) {
  }

  /**
   * Invoked when a network download fails.
   *
   * @param startValue the value returned by [downloadStart] for the start of this call. This is
   *   null unless [downloadStart] is overridden to return something else.
   */
  open fun downloadFailed(
    applicationName: String,
    url: String,
    exception: Exception,
    startValue: Any?,
  ) {
  }

  /**
   * Invoked when the manifest couldn't be decoded as JSON. For example, this might occur if there's
   * a captive portal on the network.
   */
  open fun manifestParseFailed(
    applicationName: String,
    url: String?,
    exception: Exception,
  ) {
  }

  /**
   * Invoked when a module load starts. This is the process of loading code into QuickJS.
   *
   * @return any object. This value will be passed back to [moduleLoadEnd] when the call is
   *   completed. The base function always returns null.
   */
  open fun moduleLoadStart(zipline: Zipline, moduleId: String): Any? {
    return null
  }

  /**
   * Invoked when a module load completes.
   *
   * @param startValue the value returned by [moduleLoadStart] for the start of this call. This is
   *   null unless [moduleLoadStart] is overridden to return something else.
   */
  open fun moduleLoadEnd(zipline: Zipline, moduleId: String, startValue: Any?) {
  }

  /**
   * Invoked when a Zipline is created, before any application code is loaded.
   */
  open fun ziplineCreated(zipline: Zipline) {
  }

  /**
   * Invoked when a Zipline is closed. Unless otherwise noted, other methods on this interface will
   * not be invoked after this.
   */
  open fun ziplineClosed(zipline: Zipline) {
  }

  companion object {
    val NONE: EventListener = object : EventListener() {
    }
  }
}
