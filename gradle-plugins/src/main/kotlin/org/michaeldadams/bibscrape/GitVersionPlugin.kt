package org.michaeldadams.bibscrape

import org.eclipse.jgit.api.Git
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File

// ////////////////////////////////////////////////////////////////
// Version Information
// Create a new version with: git tag -a v2023.01.01

// Set the version based on Git tags
class GitVersionPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val version = Git.open(project.rootDir).use { git ->
      val describe = git.describe().apply { setMatch("v*") }.call()
      val isClean = git.status().call().isClean
      describe
        .removePrefix("v")
        .plus(if (isClean) { "" } else { "-dirty" })
    }

    project.version = version
    project.tasks.create("version", VersionAction::class.java)

    val code = """
      |// Do not edit this file by hand.  It is generated by gradle.
      |package org.michaeldadams.bibscrape
      |
      |/** This object was generated by gradle. */
      |object BuildInformation {
      |  val version: String = "${version}"
      |}
      |
    """.trimMargin()

    // TODO: fix bug with "./gradlew clean build" (needs tasks and dependencies)
    val generatedSrcDir = File(project.buildDir, "generated/main/kotlin")
    generatedSrcDir.mkdirs()
    val file = File(generatedSrcDir, "BuildInformation.kt")
    val outdated = try { code != file.readText() } catch (_: Throwable) { true }
    if (outdated) { file.writeText(code) }

    project
      .extensions
      .getByType(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java)
      .sourceSets
      .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      .kotlin
      .srcDir(generatedSrcDir)
  }
}

open class VersionAction : DefaultTask() {
  @TaskAction
  fun version() {
    println(project.version)
  }
}
