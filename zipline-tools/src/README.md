Zipline Tools
=============

This optional module contains development tools for building Zipline applications.

Sampling Profiler
-----------------

To better understand how CPU is used in a Zipline program, use the sampling profiler. It writes data
in [HPROF format] that is readable by [YourKit].

Note that this profiler only observes Kotlin/JS.

```kotlin
zipline.quickJs.startCpuSampling(File("zipline.hprof")).use {
  // ...use Zipline...
}
```

[HPROF format]: https://hg.openjdk.java.net/jdk6/jdk6/jdk/raw-file/tip/src/share/demo/jvmti/hprof/manual.html
[YourKit]: https://www.yourkit.com/
