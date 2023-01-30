// NOTE: Groups with comment headers are sorted alphabetically by group name (TODO)

group = "org.michaeldadams.bibscrape" // TODO: "org.michaeldadams"?
description = "Collect BibTeX information from publisher pages"

// To see a complete list of tasks, use: ./gradlew tasks
plugins {
  kotlin("jvm") version "1.8.0"
  application // To run, do "./gradlew installDist" then "./build/install/bibscrape/bin/bibscrape"

  id("git-version")

  // Code Analysis
  id("io.gitlab.arturbosch.detekt") version "1.22.0" // Tasks: detekt

  // Code Coverage
  id("jacoco") // Tasks: jacocoTestReport
  id("org.jetbrains.kotlinx.kover") version "0.6.1" // Tasks: koverMergedHtmlReport

  // Code Style
  // id("com.ncorti.ktfmt.gradle") version "0.11.0" // Tasks: ktfmtCheck (omitted because issues errors not warnings)
  id("org.cqfn.diktat.diktat-gradle-plugin") version "1.2.4.1" // Tasks: diktatCheck
  id("org.jlleitschuh.gradle.ktlint") version "11.1.0" // Tasks: ktlintCheck

  // Dependency Licenses
  id("com.github.jk1.dependency-license-report") version "2.1" // Tasks: generateLicenseReport

  // Dependency Versions
  id("com.github.ben-manes.versions") version "0.44.0" // Tasks: dependencyUpdates

  // Documentation
  id("org.jetbrains.dokka") version "1.7.20" // Tasks: dokka{Gfm,Html,Javadoc,Jekyll}

  // TODO: Typesafe config
}

repositories {
  mavenCentral()
  maven { url = uri("https://dev.bibsonomy.org/maven2/") }
}

dependencies {
  // BibTeX
  // https://ftp.math.utah.edu/pub/tex/bib/
  // $ wget --recursive --timestamping --reject=html,dvi,ltx,pdf,ps.gz,ps.xz,sok,twx,db --reject='bib*' --reject='filehdr-*' --reject-regex='.*/(RCS|idx|toc)/.*' ftp://ftp.math.utah.edu/pub/tex/bib/
  implementation("org.bibsonomy:bibsonomy-bibtex-parser:3.9.4")

  // Command-line argument parsing
  implementation("com.github.ajalt.clikt:clikt:3.5.1")

  // ISBN
  implementation("com.github.ladutsko:isbn-core:1.1.1")

  // Logging
  implementation("ch.qos.logback:logback-classic:1.4.5")
  // implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")

  // RIS
  implementation("ch.difty.kris:kris-core:0.4.1")
  implementation("ch.difty.kris:kris-io:0.4.1")

  // Testing
  testImplementation(kotlin("test:1.8.0"))

  // WebDriver
  // implementation("org.seleniumhq.selenium:selenium-java:4.8.0") // depends on version of io.netty incompatible with browsermob-core
  implementation("org.seleniumhq.selenium:selenium-api:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-devtools-v107:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-devtools-v108:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-devtools-v109:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-devtools-v85:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-edge-driver:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-firefox-driver:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-ie-driver:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-remote-driver:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-safari-driver:4.8.0")
  implementation("org.seleniumhq.selenium:selenium-support:4.8.0")

  implementation("net.lightbody.bmp:browsermob-core:2.1.5")
}

// ////////////////////////////////////////////////////////////////
// Main
application {
  mainClass.set("org.michaeldadams.bibscrape.MainKt")
}

// ////////////////////////////////////////////////////////////////
// Testing
tasks.withType<Test> {
  useJUnitPlatform()

  this.testLogging {
    this.showStandardStreams = true
  }
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
  version.set("0.47.1") // must match versions in gradle-plugins/build.gradle.kts
  verbose.set(true)
  ignoreFailures.set(true)
  enableExperimentalRules.set(true) // TODO: vs .editorconfig
  disabledRules.set(setOf("string-template"))
}

// ////////////////////////////////////////////////////////////////
// Documentation
tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
  dokkaSourceSets {
    named("main") {
      includes.from("Module.md")
    }
  }
}

// ////////////////////////////////////////////////////////////////
// Task Dependencies

tasks.check {
  dependsOn(gradle.includedBuild("gradle-plugins").task(":clean"))
  dependsOn(gradle.includedBuild("gradle-plugins").task(":check"))
  dependsOn(tasks.clean)
}

// For why we have to fully qualify KotlinCompile see:
// https://stackoverflow.com/questions/55456176/unresolved-reference-compilekotlin-in-build-gradle-kts
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  // Avoid the warning: 'compileJava' task (current target is 11) and
  // 'compileKotlin' task (current target is 1.8) jvm target compatibility should
  // be set to the same Java version.
  kotlinOptions { jvmTarget = project.java.targetCompatibility.toString() }
}
