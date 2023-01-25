package org.michaeldadams.bibscrape

import bibtex.dom.BibtexFile
import bibtex.dom.BibtexMacroReference

/** Month handling functions for BibTeX. */
object Month {
  private val longNames =
    "january february march april may june july august september october november december".split(" ")
  private val macroNames =
    "jan feb mar apr may jun jul aug sep oct nov dec".split(" ")

  private val months =
    (listOf("sept" to "sep") +
      macroNames.map { it to it } +
      longNames zip macroNames
    ).toMap()

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

  /** Converts a string containing a month to the BibTeX macro for that month if it exists. */
  fun str2month(bibtexFile: BibtexFile, string: String): BibtexMacroReference? {
    val month: String? = months.get(string.lowercase())
    if (month == null) {
      return null
    } else {
      return wrap(bibtexFile, month)
    }
  }
}
