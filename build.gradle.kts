plugins {
  kotlin("jvm")
  application

  // Documentation
  // id("org.jetbrains.dokka") version "1.5.31" // Adds: ./gradlew dokka{Gfm,Html,Javadoc,Jekyll}

  // Code Formatting
  id("io.gitlab.arturbosch.detekt").version("1.19.0") // Adds: ./gradlew detekt
  // id("org.cqfn.diktat.diktat-gradle-plugin") version "1.0.3" // Adds: ./gradlew diktatCheck
  id("org.jlleitschuh.gradle.ktlint") version "10.2.1" // Adds: ./gradlew ktlintCheck (requires disabling diktat)

  // Code Coverage
  id("jacoco") // Adds: ./gradlew jacocoTestReport
  id("org.jetbrains.kotlinx.kover") version "0.5.0" // Adds: ./gradlew koverMergedHtmlReport

  // Dependency Versions and Licenses
  id("com.github.ben-manes.versions") version "0.42.0" // Adds: ./gradlew dependencyUpdates
  id("com.github.jk1.dependency-license-report") version "2.1" // Adds: ./gradlew generateLicenseReport

  id("com.github.arcticlampyrid.gradle-git-version") version "1.0.4"
}

repositories {
  mavenCentral()
}

dependencies {
  // testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
  implementation("org.seleniumhq.selenium:selenium-java:4.7.2")

  // Testing
  testImplementation(kotlin("test"))

  // NOTE: these are sorted alphabetically

  // Logging (see also io.github.microutils:kotlin-logging-jvm)
  implementation("ch.qos.logback:logback-classic:1.2.6")

  // Command-line argument parsing
  implementation("com.github.ajalt.clikt:clikt:3.5.1")

  // Logging (see also ch.qos.logback:logback-classic)
  implementation("io.github.microutils:kotlin-logging-jvm:2.1.21")
}

tasks.test {
  useJUnitPlatform()
}

application {
  mainClass.set("org.michaeldadams.bibscrape.main.MainKt")
}

// version = "0.1.0" // Uncomment to manually set the version
//apply<GitVersionPlugin>()

// ////////////////////////////////////////////////////////////////
// Code Formatting

// https://github.com/jlleitschuh/ktlint-gradle/blob/master/plugin/src/main/kotlin/org/jlleitschuh/gradle/ktlint/KtlintExtension.kt
ktlint {
  verbose.set(true)
  ignoreFailures.set(true)
  enableExperimentalRules.set(true)
  disabledRules.set(
    setOf(
      "experimental:argument-list-wrapping",
      "no-wildcard-imports",
    )
  )
}

// // https://github.com/analysis-dev/diktat/blob/master/diktat-gradle-plugin/src/main/kotlin/org/cqfn/diktat/plugin/gradle/DiktatExtension.kt
// diktat {
//   ignoreFailures = true
// }

// https://github.com/detekt/detekt/blob/main/detekt-gradle-plugin/src/main/kotlin/io/gitlab/arturbosch/detekt/extensions/DetektExtension.kt
detekt {
  ignoreFailures = true
  buildUponDefaultConfig = true
  allRules = true
}

// ////////////////////////////////////////////////////////////////
// Generic Configuration

tasks.withType<Test> {
  // Use JUnit Platform for unit tests.
  useJUnitPlatform()

  this.testLogging {
    this.showStandardStreams = true
  }
}

// tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
//   dokkaSourceSets {
//     named("main") {
//       includes.from("Module.md")
//     }
//   }
// }
