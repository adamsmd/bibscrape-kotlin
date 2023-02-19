// NOTE: Groups with comment headers are sorted alphabetically by group name (TODO)

description = "Collect BibTeX information from publisher pages"

// To see a complete list of tasks, use: ./gradlew tasks
plugins {
  id("bibscrape-gradle-settings")
  id("git-version")
  application // Provides "./gradlew installDist" then "./build/install/bibscrape/bin/bibscrape"

  // Documentation
  id("org.jetbrains.dokka") version "1.7.20" // Tasks: dokka{Gfm,Html,Javadoc,Jekyll}
}

repositories {
  maven { url = uri("https://dev.bibsonomy.org/maven2/") }
}

dependencies {
  // BibTeX
  // https://ftp.math.utah.edu/pub/tex/bib/
  // $ wget --recursive --timestamping --reject=html,dvi,ltx,pdf,ps.gz,ps.xz,sok,twx,db --reject='bib*' --reject='filehdr-*' --reject-regex='.*/(RCS|idx|toc)/.*' ftp://ftp.math.utah.edu/pub/tex/bib/
  implementation("org.bibsonomy:bibsonomy-bibtex-parser:3.9.4")

  // Command-line argument parsing
  implementation("com.github.ajalt.clikt:clikt:3.5.1")

  // HTML and XML Escapes
  implementation("org.apache.commons:commons-text:1.10.0")

  // ISBN
  implementation("com.github.ladutsko:isbn-core:1.1.1")
  implementation("org.glassfish.jaxb:jaxb-runtime:2.3.8") // Fixes: NoClassDefFoundError: javax/xml/bind/JAXBContext

  // Logging
  implementation("ch.qos.logback:logback-classic:1.4.5")
  // implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")

  // RIS
  implementation("ch.difty.kris:kris-core:0.4.1")

  // Test diffs
  implementation("io.github.java-diff-utils:java-diff-utils:4.12")

  // Test-report generation
  implementation("org.junit.platform:junit-platform-reporting:1.9.2")

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
// Checking
// tasks.register("checkAll") { // TODO
//   dependsOn(gradle.includedBuild("bibscrape-gradle-plugins").task(":clean"))
//   dependsOn(gradle.includedBuild("bibscrape-gradle-plugins").task(":check"))
//   dependsOn(task("clean"))
//   dependsOn(task("check"))
// }

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
