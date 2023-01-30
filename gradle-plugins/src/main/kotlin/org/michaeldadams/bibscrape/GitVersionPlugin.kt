package org.michaeldadams.bibscrape

import org.eclipse.jgit.api.Git
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File

// ////////////////////////////////////////////////////////////////
// Set the version based on Git tags
// Create a new version with something like: git tag -a v2023.01.01
class GitVersionPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    // Set the version in the project object
    project.version = Git.open(project.rootDir).use { git ->
      val describe = git.describe().apply { setMatch("v*") }.call()
      val isClean = git.status().call().isClean
      describe
        .removePrefix("v")
        .plus(if (isClean) { "" } else { "-dirty" })
    }

    // Create a "version" task for printing the project version
    project.tasks.create("version", VersionTask::class.java)

    // Create a "generateBuildInformation" task to generate the BuildInformation class
    val generateBuildInformation =
      project.tasks.create(
        "generateBuildInformation",
        GenerateBuildInformationTask::class.java
      )

    // Ensure that generateBuildInformation is called before compilation
    project
      .extensions
      .getByType(KotlinJvmProjectExtension::class.java)
      .sourceSets
      .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      .kotlin
      .srcDir(generateBuildInformation)
  }
}

open class VersionTask : DefaultTask() {
  @TaskAction
  fun version() { println(project.version) }
}

open class GenerateBuildInformationTask : DefaultTask() {
  @OutputDirectory
  val outputDirectory = File(project.buildDir, "build-information/main/kotlin")

  @TaskAction
  fun generate() {
    val code = """
      |// Do not edit this file by hand.  It is generated by gradle.
      |package org.michaeldadams.bibscrape
      |
      |/** Information about how this code was built.  This object was generated by gradle. */
      |object BuildInformation {
      |  val version: String = "${project.version}"
      |}
      |
    """.trimMargin()

    outputDirectory.mkdirs()
    val file = File(outputDirectory, "BuildInformation.kt")
    val outdated = try { code != file.readText() } catch (_: Throwable) { true }
    if (outdated) { file.writeText(code) }
  }
}
