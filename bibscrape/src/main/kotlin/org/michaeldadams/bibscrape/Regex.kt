package org.michaeldadams.bibscrape

import kotlin.text.RegexOption
import kotlin.text.toRegex

/** A regex matching an ISSN. */
const val ISSN_REGEX: String = """\d\d\d\d - \d\d\d[0-9Xx]"""

/** A regex matching an ISBN. */
const val ISBN_REGEX: String = """[-0-9Xx]+""" // TODO: improve

/** Converts a [String] to a [Regex] with the [RegexOption.COMMENTS] option
 * enabled. */
val String.r: Regex
  get() = this.toRegex(RegexOption.COMMENTS)

/** Converts a [String] to a [Regex] with the [RegexOption.COMMENTS] and
 * [RegexOption.IGNORE_CASE] options enabled. */
val String.ri: Regex
  get() = this.toRegex(setOf(RegexOption.COMMENTS, RegexOption.IGNORE_CASE))

/** Runs `regex.find` on the receiver.  This is a convienence, so that, when
 * matching, the regex comes after the string it is matching.
 *
 * @param regex the [Regex] to match
 * @return the result of matching [regex]
 */
fun String.find(regex: Regex): MatchResult? = regex.find(this)
