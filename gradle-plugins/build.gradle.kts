plugins {
  // Not 1.8.0 due to https://youtrack.jetbrains.com/issue/KT-54691/Kotlin-Gradle-Plugin-libraries-alignment-platform
  id("common-settings")
  `java-gradle-plugin`
}

dependencies {
  // Note that ktlint must match the version in common-settings.gradle.kts
  implementation("com.pinterest.ktlint:ktlint-ruleset-standard:0.47.1")
  implementation("com.pinterest.ktlint:ktlint-core:0.47.1")
  implementation("org.eclipse.jgit:org.eclipse.jgit:6.4.0.202211300538-r")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")
}

gradlePlugin {
  plugins.register("git-version") {
    id = "git-version"
    implementationClass = "org.michaeldadams.bibscrape.GitVersionPlugin"
  }
}
