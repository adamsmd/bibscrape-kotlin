package org.michaeldadams.bibscrape

import kotlin.text.RegexOption
import kotlin.text.toRegex

val String.r: Regex
  get() = this.toRegex(RegexOption.COMMENTS)

val String.ri: Regex
  get() = this.toRegex(setOf(RegexOption.COMMENTS, RegexOption.IGNORE_CASE))

fun String.find(regex: Regex): MatchResult? = regex.find(this)
