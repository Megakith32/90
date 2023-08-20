import co.touchlab.cklib.gradle.CompileToBitcode.Language.C
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinNativeCompilation

plugins {
  kotlin("multiplatform")
  kotlin("plugin.serialization")
  id("com.android.library")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("co.touchlab.cklib")
}

abstract class VersionWriterTask : DefaultTask() {
  @InputFile
  val versionFile = project.file("native/quickjs/VERSION")

  @OutputDirectory
  val outputDir = project.layout.buildDirectory.file("generated/version/")

  @TaskAction
  fun stuff() {
    val version = versionFile.readText().trim()

    val outputFile = outputDir.get().asFile.resolve("app/cash/zipline/version.kt")
    outputFile.parentFile.mkdirs()
    outputFile.writeText("""
      |package app.cash.zipline
      |
      |internal const val quickJsVersion = "$version"
      |""".trimMargin())
  }
}
val versionWriterTaskProvider = tasks.register("writeVersion", VersionWriterTask::class)

val copyTestingJs = tasks.register<Copy>("copyTestingJs") {
  destinationDir = buildDir.resolve("generated/testingJs")
  if (true) {
    // Production, which is minified JavaScript.
    from(projectDir.resolve("testing/build/distributions/testing.js"))
    dependsOn(":zipline:testing:jsBrowserProductionWebpack")
  } else {
    // Development, which is not minified and has useful stack traces.
    from(projectDir.resolve("testing/build/developmentExecutable/testing.js"))
    dependsOn(":zipline:testing:jsBrowserDevelopmentWebpack")
  }
}

kotlin {
  android()
  jvm()

  js {
    nodejs()
  }

  linuxX64()
  macosX64()
  macosArm64()
  iosArm32()
  iosArm64()
  iosX64()
  iosSimulatorArm64()
  watchosArm32()
  watchosArm64()
  watchosSimulatorArm64()
  watchosX86()
  watchosX64()
  tvosArm64()
  tvosSimulatorArm64()
  tvosX64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(Dependencies.kotlinxCoroutines)
        api(Dependencies.kotlinxSerialization)
        implementation(Dependencies.kotlinxSerializationJson)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }

    val engineMain by creating {
      dependsOn(commonMain)
      kotlin.srcDir(versionWriterTaskProvider)
    }
    val engineTest by creating {
      dependsOn(commonTest)
    }

    val jniMain by creating {
      dependsOn(engineMain)
      dependencies {
        api(Dependencies.androidxAnnotation)
        api(Dependencies.kotlinReflect)
      }
    }
    val androidMain by getting {
      dependsOn(jniMain)
    }
    val jvmMain by getting {
      dependsOn(jniMain)
    }
    val jvmTest by getting {
      dependsOn(engineTest)
      kotlin.srcDir("src/jniTest/kotlin/")
      resources.srcDir(copyTestingJs)
      dependencies {
        implementation(Dependencies.truth)
        implementation(Dependencies.kotlinxCoroutinesTest)
        implementation(project(":zipline:testing"))
      }
    }

    val nativeMain by creating {
      dependsOn(engineMain)
    }
    val nativeTest by creating {
      dependsOn(engineTest)
    }

    val native32Main by creating {
      dependsOn(nativeMain)
    }
    val native32Test by creating {
      dependsOn(nativeTest)
    }

    val native64Main by creating {
      dependsOn(nativeMain)
    }
    val native64Test by creating {
      dependsOn(nativeTest)
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
      val main by compilations.getting
      main.defaultSourceSet.dependsOn(
        if (use64BitSource(this)) {
          native64Main
        } else {
          native32Main
        }
      )

      main.cinterops {
        create("quickjs") {
          header(file("native/quickjs/quickjs.h"))
          packageName("app.cash.zipline.quickjs")
        }
      }

      val test by compilations.getting
      test.defaultSourceSet.dependsOn(
        if (use64BitSource(this)) {
          native64Test
        } else {
          native32Test
        }
      )
    }

    targets.all {
      compilations.all {
        val pluginDependency = if (this is AbstractKotlinNativeCompilation) {
          project(":zipline-kotlin-plugin:hosted")
        } else {
          project(":zipline-kotlin-plugin")
        }
        // Naming logic from https://github.com/JetBrains/kotlin/blob/a0e6fb03f0288f0bff12be80c402d8a62b5b045a/libraries/tools/kotlin-gradle-plugin/src/main/kotlin/org/jetbrains/kotlin/gradle/plugin/KotlinTargetConfigurator.kt#L519-L520
        val pluginConfigurationName = PLUGIN_CLASSPATH_CONFIGURATION_NAME +
          target.disambiguationClassifier.orEmpty().capitalize() +
          compilationName.capitalize()
        project.dependencies.add(pluginConfigurationName, pluginDependency)
      }
    }
  }
}

// The cinterop in QuickJS is slightly different between 32 and 64 bit.
// watchosArm64 reports as 64 bit but has 32 bit pointers.
fun use64BitSource(target:org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget): Boolean =
  target.name != "watchosArm64" && target.konanTarget.architecture.bitness == 64

cklib {
  create("quickjs") {
    language = C
    srcDirs = project.files(file("native/quickjs"))
    compilerArgs.addAll(
      listOf(
        "-DKONAN_MI_MALLOC=1",
        "-DCONFIG_VERSION=\"${quickJsVersion()}\"",
        "-Wno-unknown-pragmas",
        "-ftls-model=initial-exec",
        "-Wno-unused-function",
        "-Wno-error=atomic-alignment",
        "-Wno-sign-compare",
        "-Wno-unused-parameter" /* for windows 32*/
      )
    )
  }
}

android {
  compileSdkVersion(Ext.compileSdk)

  defaultConfig {
    minSdkVersion(18)
    multiDexEnabled = true

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += Ext.ndkAbiFilters
    }

    externalNativeBuild {
      cmake {
        arguments("-DANDROID_TOOLCHAIN=clang", "-DANDROID_STL=c++_static")
        cFlags("-fstrict-aliasing", "-DCONFIG_VERSION=\\\"${quickJsVersion()}\\\"")
        cppFlags("-fstrict-aliasing", "-DCONFIG_VERSION=\\\"${quickJsVersion()}\\\"")
      }
    }

    packagingOptions {
      // We get multiple copies of some license files via JNA, which is a transitive dependency of
      // kotlinx-coroutines-test. Don't fail the build on these duplicates.
      exclude("META-INF/AL2.0")
      exclude("META-INF/LGPL2.1")
    }
  }

  sourceSets {
    getByName("main") {
      manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    getByName("androidTest") {
      java.srcDirs("src/engineTest/kotlin/", "src/jniTest/kotlin/")
      resources.srcDir("src/androidInstrumentationTest/resources/")
      resources.srcDir(copyTestingJs)
    }
  }

  // The above `resources.srcDir(copyTestingJs)` code is supposed to automatically add a task
  // dependency, but it doesn't. So we add it ourselves using this nonsense.
  afterEvaluate {
    libraryVariants.onEach { libraryVariant ->
      libraryVariant.testVariant?.processJavaResourcesProvider?.configure {
        dependsOn(copyTestingJs)
      }
    }
  }

  buildTypes {
    val release by getting {
      externalNativeBuild {
        cmake {
          arguments("-DCMAKE_BUILD_TYPE=MinSizeRel")
          cFlags("-g0", "-Os", "-fomit-frame-pointer", "-DNDEBUG", "-fvisibility=hidden")
          cppFlags("-g0", "-Os", "-fomit-frame-pointer", "-DNDEBUG", "-fvisibility=hidden")
        }
      }
    }
    val debug by getting {
      externalNativeBuild {
        cmake {
          cFlags("-g", "-DDEBUG", "-DDUMP_LEAKS")
          cppFlags("-g", "-DDEBUG", "-DDUMP_LEAKS")
        }
      }
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/androidMain/CMakeLists.txt")
    }
  }
}

dependencies {
  androidTestImplementation(Dependencies.junit)
  androidTestImplementation(Dependencies.androidxTestRunner)
  androidTestImplementation(Dependencies.truth)
  androidTestImplementation(Dependencies.kotlinxCoroutinesTest)
  androidTestImplementation(project(":zipline:testing"))
}

fun quickJsVersion(): String {
  return File(projectDir, "native/quickjs/VERSION").readText().trim()
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(
      javadocJar = JavadocJar.Dokka("dokkaGfm")
    )
  )
}
