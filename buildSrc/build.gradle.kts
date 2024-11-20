// NOTE: Groups with comment headers are sorted alphabetically by group name

repositories {
  gradlePluginPortal()
  mavenCentral()
}

plugins {
  kotlin("jvm") version "2.0.20"
  `kotlin-dsl` // version built into gradle

  id("com.github.ben-manes.versions") version "0.51.0" // Adds: ./gradlew -p buildSrc dependencyUpdates
  id("com.saveourtool.diktat") version "2.0.0" // Adds: ./gradlew -p buildSrc diktatCheck
  id("io.gitlab.arturbosch.detekt") version "1.23.7" // Adds: ./gradlew -p buildSrc detekt
  id("org.jlleitschuh.gradle.ktlint") version "12.1.1" // Adds: ./gradlew -p buildSrc ktlintCheck
}

dependencies {
  // Documentation
  // implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.7.20")

  // Kotlin Plugin
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin") // version set by kotlin("jvm")

  // Linting
  // implementation("com.ncorti.ktfmt.gradle:0.11.0")
  // implementation("se.solrike.sonarlint:sonarlint-gradle-plugin:1.0.0-beta.8")

  // Note that ktlint must match the version in bibscrape-gradle-settings.gradle.kts
  // Used by defined rules
  implementation("com.pinterest.ktlint:ktlint-ruleset-standard:1.4.1")
  implementation("com.pinterest.ktlint:ktlint-cli-ruleset-core:1.4.1")
  implementation("com.pinterest.ktlint:ktlint-rule-engine-core:1.4.1")

  // Git API (for `GitVersionsPlugin.kt`)
  implementation("org.eclipse.jgit:org.eclipse.jgit:7.0.0.202409031743-r")
}

// ////////////////////////////////////////////////////////////////
// Code Formatting

// See https://github.com/saveourtool/diktat/blob/v2.0.0/diktat-gradle-plugin/src/main/kotlin/com/saveourtool/diktat/plugin/gradle/DiktatExtension.kt
diktat {
  ignoreFailures = true
  diktatConfigFile = File("../diktat-analysis.yml")
}

// See https://github.com/detekt/detekt/blob/v1.23.7/detekt-gradle-plugin/src/main/kotlin/io/gitlab/arturbosch/detekt/extensions/DetektExtension.kt
detekt {
  ignoreFailures = true
  allRules = true
  buildUponDefaultConfig = true
}

// See https://github.com/JLLeitschuh/ktlint-gradle/blob/v12.1.1/plugin/src/main/kotlin/org/jlleitschuh/gradle/ktlint/KtlintExtension.kt
ktlint {
  version = "1.4.1"
  verbose = true
  ignoreFailures = true
  enableExperimentalRules = true
}
