plugins {
  kotlin("jvm") version "1.8.0"
  // `kotlin-dsl`

  id("com.github.ben-manes.versions") version "0.42.0" // Adds: ./gradlew -p buildSrc dependencyUpdates
  id("io.gitlab.arturbosch.detekt").version("1.19.0") // Adds: ./gradlew -p buildSrc detekt
  // id("org.cqfn.diktat.diktat-gradle-plugin") version "1.0.3" // Adds: ./gradlew -p buildSrc diktatCheck
  id("org.jlleitschuh.gradle.ktlint") version "10.2.1" // Adds: ./gradlew -p buildSrc ktlintCheck (requires disabling diktat)
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
  implementation("org.eclipse.jgit:org.eclipse.jgit:6.4.0.202211300538-r")
}

// ////////////////////////////////////////////////////////////////
// Code Formatting

// https://github.com/jlleitschuh/ktlint-gradle/blob/master/plugin/src/main/kotlin/org/jlleitschuh/gradle/ktlint/KtlintExtension.kt
ktlint {
  verbose.set(true)
  ignoreFailures.set(true)
  enableExperimentalRules.set(true)
  disabledRules.set(emptySet())
}

// // https://github.com/analysis-dev/diktat/blob/master/diktat-gradle-plugin/src/main/kotlin/org/cqfn/diktat/plugin/gradle/DiktatExtension.kt
// diktat {
//   ignoreFailures = true
//   diktatConfigFile = File("../diktat-analysis.yml")
// }

// https://github.com/detekt/detekt/blob/main/detekt-gradle-plugin/src/main/kotlin/io/gitlab/arturbosch/detekt/extensions/DetektExtension.kt
detekt {
  ignoreFailures = true
  buildUponDefaultConfig = true
  allRules = true
}
