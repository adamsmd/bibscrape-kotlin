/** Convienience functions for working with regular expressions. */

package org.michaeldadams.bibscrape

import kotlin.text.RegexOption
import kotlin.text.toRegex

/** Converts a [String] to a [Regex] with the [RegexOption.COMMENTS] option
 * enabled. */
@Suppress("CUSTOM_GETTERS_SETTERS")
val String.r: Regex
  get() = this.toRegex(RegexOption.COMMENTS) // TODO: UNICODE_CHARACTER_CLASS

/** Converts a [String] to a [Regex] with the [RegexOption.COMMENTS] and
 * [RegexOption.IGNORE_CASE] options enabled. */
@Suppress("CUSTOM_GETTERS_SETTERS")
val String.ri: Regex
  get() = this.toRegex(setOf(RegexOption.COMMENTS, RegexOption.IGNORE_CASE))

/** Calls `regex.find` on the receiver.  This is a convienence, so that, when
 * matching, the regex comes after the string it is matching.
 *
 * @param regex the [Regex] to match
 * @return the result of matching [regex]
 */
fun String.find(regex: Regex): MatchResult? = regex.find(this)

/** Calls `regex.split` on the receiver.  This is a convienence, so that, when
 * splitting, the regex comes after the string it is splitting.
 *
 * @param regex the [Regex] used to split
 * @return the result of splitting with [regex]
 */
fun String.split(regex: Regex): List<String> = regex.split(this)

/** Removes all occurences of [regex] from the receiver.
 *
 * @param regex what to remove
 * @return the string after all matches of [regex] are removed
 */
fun String.remove(regex: Regex): String = this.replace(regex, "")

/** Regex string for Unicode Alphabetic or Digit characters */
const val ALNUM = """(?: \p{IsAlphabetic} | \p{IsDigit} )"""

/** Regex string for a Word-Break on the Left (WBL) */
const val WBL = """(?: ^ | (?<! ${ALNUM} ) ) (?= ${ALNUM} )"""

/** Regex string for a Word-Break on the Reft (WBR) */
const val WBR = """(?<= ${ALNUM} ) (?: $ | (?! ${ALNUM} ) )"""
