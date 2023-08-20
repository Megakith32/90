import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

kotlin {
  sourceSets.all {
    languageSettings {
      optIn("app.cash.zipline.EngineApi")
    }
  }
}

dependencies {
  implementation(kotlin("gradle-plugin-api"))
  implementation(projects.zipline)
  implementation(projects.ziplineBytecode)
  implementation(projects.ziplineLoader)
  implementation(libs.http4k.core)
  implementation(libs.http4k.server.jetty)
  implementation(libs.http4k.client.websocket)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okHttp.core)
  implementation(libs.okio.core)
  testImplementation(projects.ziplineLoaderTesting)
  testImplementation(libs.assertk)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.okHttp.mockWebServer)
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

  val compilerPlugin = projects.ziplineKotlinPlugin
  packageName("app.cash.zipline.gradle")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${libs.plugins.zipline.kotlin.get()}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${compilerPlugin.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${compilerPlugin.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${compilerPlugin.version}\"")
}

gradlePlugin {
  plugins {
    create("zipline") {
      id = "app.cash.zipline"
      displayName = "zipline"
      description = "Compiler plugin to generate bridges between platforms"
      implementationClass = "app.cash.zipline.gradle.ZiplinePlugin"
    }
  }
}

tasks {
  test {
    systemProperty("ziplineVersion", project.version)
    dependsOn(":zipline-bytecode:publishAllPublicationsToTestMavenRepository")
    dependsOn(":zipline-gradle-plugin:publishAllPublicationsToTestMavenRepository")
    dependsOn(":zipline-kotlin-plugin:publishAllPublicationsToTestMavenRepository")
    dependsOn(":zipline-loader:publishJvmPublicationToTestMavenRepository")
    dependsOn(":zipline-loader:publishKotlinMultiplatformPublicationToTestMavenRepository")
    dependsOn(":zipline:publishJsPublicationToTestMavenRepository")
    dependsOn(":zipline:publishJvmPublicationToTestMavenRepository")
    dependsOn(":zipline:publishKotlinMultiplatformPublicationToTestMavenRepository")
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    GradlePlugin(
      javadocJar = JavadocJar.Empty()
    )
  )
}
