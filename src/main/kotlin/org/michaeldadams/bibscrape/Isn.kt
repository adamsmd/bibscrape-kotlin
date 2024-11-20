/** Regular expressions for ISBN and ISSN numbers. */

package org.michaeldadams.bibscrape

/** A middle (non-first and non-last) digit in an ISBN.  Allows "-" and " " before the digit. */
private const val ISBN_DIGIT = """(?: [-\ ]? \d )"""

/** A regex matching an ISBN. */
const val ISBN_REGEX: String = """(?: \d (?: ${ISBN_DIGIT}{8} | ${ISBN_DIGIT}{11} ) [-\ ]? [0-9Xx] )"""

/** A regex matching an ISSN. */
const val ISSN_REGEX: String = """(?: \d\d\d\d - \d\d\d[0-9Xx] )"""
