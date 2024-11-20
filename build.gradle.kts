import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

// NOTE: Groups with comment headers are sorted alphabetically by group name (TODO)

group = "org.michaeldadams.bibscrape"
// name is set in settings.gradle.kts because it is read-only here
// version = "0.1.0" // Uncomment to manually set the version (see GitVersionPlugin)
description = "Collect BibTeX information from publisher pages"

repositories {
  mavenCentral()
  maven { url = uri("https://dev.bibsonomy.org/maven2/") }
}

// ////////////////////////////////////////////////////////////////

// To see a complete list of tasks, use: ./gradlew tasks
plugins {
  kotlin("jvm") // version set by buildSrc/build.gradle.kts
  application // Provides "./gradlew installDist" then "./build/install/${name}/bin/${applicationName}"

  // Documentation
  id("org.jetbrains.dokka") version "1.9.20" // Adds: ./gradlew dokka{Gfm,Html,Javadoc,Jekyll}

  // Linting and Code Formatting
  id("com.saveourtool.diktat") version "2.0.0" // Adds: ./gradlew diktatCheck
  id("io.gitlab.arturbosch.detekt") version "1.23.7" // Adds: ./gradlew detekt
  id("org.jlleitschuh.gradle.ktlint") version "12.1.1" // Adds: ./gradlew ktlintCheck

  // Code Coverage
  id("jacoco") // version built into Gradle // Adds: ./gradlew jacocoTestReport
  id("org.jetbrains.kotlinx.kover") version "0.8.3" // Adds: ./gradlew koverMergedHtmlReport

  // Dependency Versions and Licenses
  id("com.github.ben-manes.versions") version "0.51.0" // Adds: ./gradlew dependencyUpdates
  id("com.github.jk1.dependency-license-report") version "2.9" // Adds: ./gradlew generateLicenseReport
}
apply<org.michaeldadams.gradle.GenerateBuildInformationPlugin>()
apply<org.michaeldadams.gradle.GitVersionPlugin>()
apply<org.michaeldadams.gradle.VersionTaskPlugin>()

// ////////////////////////////////////////////////////////////////

dependencies {
  // BibTeX
  // https://ftp.math.utah.edu/pub/tex/bib/
  // $ wget --recursive --timestamping --reject=html,dvi,ltx,pdf,ps.gz,ps.xz,sok,twx,db --reject='bib*' --reject='filehdr-*' --reject-regex='.*/(RCS|idx|toc)/.*' ftp://ftp.math.utah.edu/pub/tex/bib/
  implementation("org.bibsonomy:bibsonomy-bibtex-parser:3.9.4")

  // Linting and Code Formatting
  // detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7") // We use org.jlleitschuh.gradle.ktlint instead to use the newest ktlint
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:1.23.7")
  detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-ruleauthors:1.23.7")

  // Command-line argument parsing
  implementation("com.github.ajalt.clikt:clikt:3.5.1")

  // HTML parsing
  implementation("org.jsoup:jsoup:1.15.4")

  // ISBN
  implementation("com.github.ladutsko:isbn-core:1.1.1")
  implementation("org.glassfish.jaxb:jaxb-runtime:2.3.8") // Fixes: NoClassDefFoundError: javax/xml/bind/JAXBContext

  // Logging
  implementation("ch.qos.logback:logback-classic:1.4.5")
  // implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")

  // RIS
  implementation("ch.difty.kris:kris-core:0.4.3")

  // Test diffs
  implementation("io.github.java-diff-utils:java-diff-utils:4.12")

  // Test-report generation
  implementation("org.junit.platform:junit-platform-reporting:1.9.2")

  // WebDriver
  implementation("org.seleniumhq.selenium:selenium-java:4.8.1")
  // This line fixes a NoSuchMethodError that is due the version of io.netty depended on by browsermob-core
  implementation("org.seleniumhq.selenium:selenium-remote-driver:4.8.1")
  implementation("net.lightbody.bmp:browsermob-core:2.1.5")

  // TODO: commons-lang3: SystemUtils.IS_WINDOWS
  // TODO: commons-io: FileCleaningTracker
  // TODO: commons-configuration?
  // TODO: commons-exec

  // Testing
  testImplementation(kotlin("test"))
}

// ////////////////////////////////////////////////////////////////
// Application Setup and Meta-data
application {
  mainClass.set("org.michaeldadams.bibscrape.MainKt")
  applicationDefaultJvmArgs += listOf("-ea") // enable assertions
}

// ////////////////////////////////////////////////////////////////
// Linting and Code Formatting

// See https://github.com/saveourtool/diktat/blob/v2.0.0/diktat-gradle-plugin/src/main/kotlin/com/saveourtool/diktat/plugin/gradle/DiktatExtension.kt
//
// TODO: fix errors in stderr of diktatCheck:
//     line 1:3 no viable alternative at character '='
//     line 1:4 no viable alternative at character '='
//     line 1:5 no viable alternative at character '='
//     line 1:7 mismatched input 'null' expecting RPAREN
diktat {
  ignoreFailures = true
  // TODO: githubActions = true

  // See https://github.com/saveourtool/diktat/blob/v2.0.0/diktat-gradle-plugin/src/main/kotlin/com/saveourtool/diktat/plugin/gradle/extension/Reporters.kt
  reporters {
    plain()
    json()
    sarif()
    // gitHubActions()
    checkstyle()
    html()
  }
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

  // See https://github.com/JLLeitschuh/ktlint-gradle/blob/v12.1.1/plugin/src/adapter/kotlin/org/jlleitschuh/gradle/ktlint/reporter/ReporterType.kt
  reporters {
    reporter(ReporterType.PLAIN)
    reporter(ReporterType.PLAIN_GROUP_BY_FILE)
    reporter(ReporterType.CHECKSTYLE)
    reporter(ReporterType.JSON)
    reporter(ReporterType.SARIF)
    reporter(ReporterType.HTML)
  }
}

// ////////////////////////////////////////////////////////////////
// Generic Configuration

// TODO: tasks.check.dependsOn(diktatCheck)
tasks.withType<Test> {
  // Use JUnit Platform for unit tests.
  useJUnitPlatform()

  this.testLogging {
    this.showStandardStreams = true
  }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
  dokkaSourceSets {
    named("main") {
      includes.from("docs/Module.md")
    }
  }
}

listOf("runKtlintCheckOverMainSourceSet", "runKtlintCheckOverTestSourceSet").forEach { name ->
  tasks.named(name).configure {
    dependsOn("generateBuildInformation")
  }
}

// For why we have to fully qualify KotlinCompile see:
// https://stackoverflow.com/questions/55456176/unresolved-reference-compilekotlin-in-build-gradle-kts
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  // Avoid the warning: 'compileJava' task (current target is 11) and
  // 'compileKotlin' task (current target is 1.8) jvm target compatibility should
  // be set to the same Java version.
  kotlinOptions { jvmTarget = project.java.targetCompatibility.toString() }

  dependsOn("generateBuildInformation")
}
