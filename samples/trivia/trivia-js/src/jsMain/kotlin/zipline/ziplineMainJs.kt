package zipline

import app.cash.zipline.samples.trivia.launchZipline

@OptIn(ExperimentalJsExport::class)
@JsExport
fun ziplineMain() {
  launchZipline()
}
