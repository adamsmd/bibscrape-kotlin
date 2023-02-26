// NOTE: Groups with comment headers are sorted alphabetically by group name (TODO)

// To see a complete list of tasks, use: ./gradlew tasks
plugins {
  kotlin("jvm")

  // Code Coverage
  id("jacoco") // Tasks: jacocoTestReport
  id("org.jetbrains.kotlinx.kover") // Tasks: koverMergedHtmlReport

  // Dependency Licenses
  id("com.github.jk1.dependency-license-report") // Tasks: generateLicenseReport

  // Dependency Versions
  id("com.github.ben-manes.versions") // Tasks: dependencyUpdates

  // Documentation
  id("org.jetbrains.dokka") // Tasks: dokka{Gfm,Html,Javadoc,Jekyll}

  // Linting
  id("io.gitlab.arturbosch.detekt") // Tasks: detekt
  // id("com.ncorti.ktfmt.gradle") // Tasks: ktfmtCheck (omitted because issues errors not warnings)
  id("org.cqfn.diktat.diktat-gradle-plugin") // Tasks: diktatCheck
  id("org.jlleitschuh.gradle.ktlint") // Tasks: ktlintCheck
  id("se.solrike.sonarlint") // Tasks: sonarlint{Main,Test}

  // TODO: Typesafe config
}

repositories {
  mavenCentral()
}

dependencies {
  // Linting
  sonarlintPlugins("org.sonarsource.kotlin:sonar-kotlin-plugin:2.12.0.1956")

  // Testing
  testImplementation(kotlin("test"))
}

// ////////////////////////////////////////////////////////////////
// Linting

detekt {
  allRules = true
  buildUponDefaultConfig = true
  ignoreFailures = true
}

diktat {
  ignoreFailures = true
}

ktlint {
  // Not using 0.48.0+ due to https://github.com/JLLeitschuh/ktlint-gradle/issues/622
  version.set("0.47.1")
  verbose.set(true)
  ignoreFailures.set(true)
  enableExperimentalRules.set(true) // TODO: vs .editorconfig
  disabledRules.set(
    setOf(
      "no-unit-return",
      "string-template",
    )
  )
}

tasks.register<se.solrike.sonarlint.SonarlintListRules>("sonarlintListRules") {
  description = "List sonarlint rules"
  group = "verification"
}

sonarlint {
  ignoreFailures.set(true)
  excludeRules.set(
    listOf(
      "kotlin:S1135", // Track uses of "TODO" tags
    )
  )
  includeRules.set(
    listOf(
      "kotlin:S105", // Tabulation characters should not be used
      "kotlin:S5867", // Unicode-aware versions of character classes should be preferred
    )
  )
}
