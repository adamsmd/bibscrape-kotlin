plugins {
  kotlin("jvm") version "1.7.10"
  `kotlin-dsl`
  `java-gradle-plugin`

  // TODO: id("org.jlleitschuh.gradle.ktlint") version "11.1.0" // Tasks: ktlintCheck
}

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
