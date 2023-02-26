// NOTE: Groups with comment headers are sorted alphabetically by group name

group = "org.michaeldadams.bibscrape"

plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  gradlePluginPortal()
  mavenCentral()
}

dependencies {
  // Code Coverage
  // implementation("org.jacoco:jacoco:") // Tasks: jacocoTestReport
  implementation("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:0.6.1")

  // Dependency Licenses
  implementation("com.github.jk1.dependency-license-report:com.github.jk1.dependency-license-report.gradle.plugin:2.1")

  // Dependency Versions
  implementation("com.github.ben-manes:gradle-versions-plugin:0.42.0")

  // Documentation
  implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.7.20")

  // Kotlin Plugin
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")

  // Linting
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.22.0")
  // implementation("com.ncorti.ktfmt.gradle:0.11.0")
  implementation("org.cqfn.diktat.diktat-gradle-plugin:org.cqfn.diktat.diktat-gradle-plugin.gradle.plugin:1.2.4.1")
  implementation("org.jlleitschuh.gradle.ktlint:org.jlleitschuh.gradle.ktlint.gradle.plugin:11.1.0")
  implementation("se.solrike.sonarlint:sonarlint-gradle-plugin:1.0.0-beta.8")
}

// TODO: sync with bibscrape-gradle-settings.gradle.kts
