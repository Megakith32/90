import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("com.github.gmazzo.buildconfig")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(kotlin("compiler-embeddable"))
  compileOnly(kotlin("stdlib"))

  kapt(libs.auto.service.compiler)
  compileOnly(libs.auto.service.annotations)
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
  }

  packageName("app.cash.zipline.kotlin")
  buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${libs.plugins.zipline.kotlin.get()}\"")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(
      javadocJar = JavadocJar.Empty()
    )
  )
}
