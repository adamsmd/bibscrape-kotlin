plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
  implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:5.0.12")
  testImplementation("junit:junit:4.13")
  // Code Analysis
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.22.0") // Tasks: detekt

  // Code Coverage
  // implementation("org.jacoco:jacoco:") // Tasks: jacocoTestReport
  implementation("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:0.6.1") // Tasks: koverMergedHtmlReport

  // Code Style
  // implementation("com.ncorti.ktfmt.gradle:0.11.0") // Tasks: ktfmtCheck (omitted because issues errors not warnings)
  implementation("org.cqfn.diktat.diktat-gradle-plugin:org.cqfn.diktat.diktat-gradle-plugin.gradle.plugin:1.2.4.1") // Tasks: diktatCheck
  implementation("org.jlleitschuh.gradle.ktlint:org.jlleitschuh.gradle.ktlint.gradle.plugin:11.1.0") // Tasks: ktlintCheck

  // Dependency Licenses
  implementation("com.github.jk1.dependency-license-report:com.github.jk1.dependency-license-report.gradle.plugin:2.1") // Tasks: generateLicenseReport

  // Dependency Versions
  implementation("com.github.ben-manes:gradle-versions-plugin:0.42.0") // Tasks: dependencyUpdates

  // Documentation
  implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.7.20") // Tasks: dokka{Gfm,Html,Javadoc,Jekyll}
}
