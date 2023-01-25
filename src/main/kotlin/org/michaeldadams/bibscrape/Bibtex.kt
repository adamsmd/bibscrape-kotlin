package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import bibtex.dom.BibtexMacroReference
import bibtex.parser.BibtexParser
import java.io.StringReader

/** BibTeX utility functions. */
object Bibtex {
  private val longNames =
    "january february march april may june july august september october november december".split(" ")
  private val macroNames =
    "jan feb mar apr may jun jul aug sep oct nov dec".split(" ")

  private val months =
    (listOf("sept" to "sep") +
      macroNames.map { it to it } +
      longNames zip macroNames
    ).toMap()

  /** Parses a [string] into its constituent BibTeX entries.
   *
   * @param string the string to parse
   * @return the entries that were succesfully parsed
   */
  fun parse(string: String): List<BibtexEntry> {
    val bibtexFile = BibtexFile()
    val parser = BibtexParser(false) // false => don't throw parse exceptions
    parser.parse(bibtexFile, StringReader(string))
    return bibtexFile.getEntries().filterIsInstance<BibtexEntry>()
  }

  private fun wrap(bibtexFile: BibtexFile, macro: String?): BibtexMacroReference? {
    if (macro == null) {
      return null
    } else {
      return bibtexFile.makeMacroReference(macro)
    }
  }

  // sub num2month(Str:D $num --> BibScrape::BibTeX::Piece:D) is export {
  //   $num ~~ m/^ \d+ $/
  //     ?? wrap(@macro-names[$num-1])
  //     !! die "Invalid month number: $num"
  // }

  /** Converts a string containing a month to the BibTeX macro for that month if it exists.
   *
   * @param bibtexFile the [BibTexFile] to use as the factory for the [BibtexMacroReference]
   * @param string the [String] to parse as a month name
   * @return a [BibtexMacroReference] for the given month or [null] if parsing failed
   */
  fun str2month(bibtexFile: BibtexFile, string: String): BibtexMacroReference? {
    val month: String? = months.get(string.lowercase())
    if (month == null) {
      return null
    } else {
      return wrap(bibtexFile, month)
    }
  }
}
