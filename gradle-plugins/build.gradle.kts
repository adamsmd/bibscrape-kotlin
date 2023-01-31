plugins {
  // Not 1.8.0 due to https://youtrack.jetbrains.com/issue/KT-54691/Kotlin-Gradle-Plugin-libraries-alignment-platform
  kotlin("jvm") version "1.8.0"
  `kotlin-dsl`
  `java-gradle-plugin`
  id("common-settings")
}

// TODO: :gradlePlugins:clean :gradlePlugins:check
repositories {
  mavenCentral()
}

dependencies {
  implementation("org.eclipse.jgit:org.eclipse.jgit:6.4.0.202211300538-r")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")

  // Must match version in ../build.gradle.kts
  implementation("com.pinterest.ktlint:ktlint-ruleset-standard:0.47.1")
  implementation("com.pinterest.ktlint:ktlint-core:0.47.1")
}

gradlePlugin {
  plugins.register("git-version") {
    id = "git-version"
    implementationClass = "org.michaeldadams.bibscrape.GitVersionPlugin"
  }
}
