plugins {
  kotlin("jvm") version "1.7.10"
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.eclipse.jgit:org.eclipse.jgit:6.4.0.202211300538-r")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")
}

gradlePlugin {
  plugins.register("git-version") {
    id = "git-version"
    implementationClass = "org.michaeldadams.bibscrape.GitVersionPlugin"
  }
}
