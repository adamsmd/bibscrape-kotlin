plugins {
  // Not 1.8.0 due to https://youtrack.jetbrains.com/issue/KT-54691/Kotlin-Gradle-Plugin-libraries-alignment-platform
  kotlin("jvm") version "1.7.10"
  `kotlin-dsl`
  `java-gradle-plugin`

  // id("com.ncorti.ktfmt.gradle") version "0.11.0" // Tasks: ktfmtCheck (omitted because issues errors not warnings)
  id("io.gitlab.arturbosch.detekt") version "1.22.0" // Tasks: detekt
  id("org.cqfn.diktat.diktat-gradle-plugin") version "1.2.4.1" // Tasks: diktatCheck
  id("org.jlleitschuh.gradle.ktlint") version "11.1.0" // Tasks: ktlintCheck
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
  version.set("0.47.1") // must match versions in gradle-plugins/build.gradle.kts
  verbose.set(true)
  ignoreFailures.set(true)
  enableExperimentalRules.set(true) // TODO: vs .editorconfig
  disabledRules.set(setOf("string-template"))
}
