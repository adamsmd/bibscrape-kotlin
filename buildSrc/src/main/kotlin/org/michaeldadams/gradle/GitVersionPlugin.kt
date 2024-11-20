// Set the version based on Git tags.
// Create a new version with `git tag -a v2023.01.01`.
// Based on <https://github.com/ArcticLampyrid/gradle-git-version/>.

package org.michaeldadams.gradle

import org.eclipse.jgit.api.Git
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.Date

class GitVersionPlugin : Plugin<Project> {
  // TODO: config: date format, match/prefix
  override fun apply(project: Project) {
    if (project.version == Project.DEFAULT_VERSION) {
      try {
        project.version = Git.open(project.rootDir).use { git ->
          val tag = git.describe().setAlways(true).setLong(false).setMatch("v*").setTags(true).call()
          val suffix = SimpleDateFormat("'-'yyyyMMdd'T'HHmmssXX").format(Date())
          tag.removePrefix("v") + if (!git.status().call().isClean) { suffix } else { "" }
        }
      } catch (e: Throwable) {
        println("Failed to detect version for ${project.name} based on git info")
        e.printStackTrace()
      }
    }
  }
}
