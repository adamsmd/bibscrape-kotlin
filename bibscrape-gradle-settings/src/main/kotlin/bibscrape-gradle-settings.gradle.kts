// NOTE: Groups with comment headers are sorted alphabetically by group name (TODO)

// To see a complete list of tasks, use: ./gradlew tasks
plugins {
  kotlin("jvm")

  // Code Analysis
  id("io.gitlab.arturbosch.detekt") // Tasks: detekt

  // Code Coverage
  id("jacoco") // Tasks: jacocoTestReport
  id("org.jetbrains.kotlinx.kover") // Tasks: koverMergedHtmlReport

  // Code Style
  // id("com.ncorti.ktfmt.gradle") // Tasks: ktfmtCheck (omitted because issues errors not warnings)
  id("org.cqfn.diktat.diktat-gradle-plugin") // Tasks: diktatCheck
  id("org.jlleitschuh.gradle.ktlint") // Tasks: ktlintCheck

  // Dependency Licenses
  id("com.github.jk1.dependency-license-report") // Tasks: generateLicenseReport

  // Dependency Versions
  id("com.github.ben-manes.versions") // Tasks: dependencyUpdates

  // Documentation
  id("org.jetbrains.dokka") // Tasks: dokka{Gfm,Html,Javadoc,Jekyll}

  // TODO: Typesafe config
}

repositories {
  mavenCentral()
}

dependencies {
  // Testing
  testImplementation(kotlin("test"))
}

// ////////////////////////////////////////////////////////////////
// Code Analysis
detekt {
  allRules = true
  buildUponDefaultConfig = true
  ignoreFailures = true
}

// ////////////////////////////////////////////////////////////////
// Code Formatting

diktat {
  ignoreFailures = true
}

ktlint {
  // Not using 0.48.0+ due to https://github.com/JLLeitschuh/ktlint-gradle/issues/622
  version.set("0.47.1")
  verbose.set(true)
  ignoreFailures.set(true)
  enableExperimentalRules.set(true) // TODO: vs .editorconfig
  disabledRules.set(setOf("string-template"))
}
