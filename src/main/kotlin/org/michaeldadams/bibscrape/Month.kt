package org.michaeldadams.bibscrape

import bibtex.dom.BibtexFile
import bibtex.dom.BibtexMacroReference

object Month {
  val longNames =
    "january february march april may june july august september october november december".split(" ")
  val macroNames =
    "jan feb mar apr may jun jul aug sep oct nov dec".split(" ")

  val months = (
    listOf("sept" to "sep") +
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

  fun str2month(bibtexFile: BibtexFile, string: String): BibtexMacroReference? {
    val month: String? = months.get(string.lowercase())
    if (month == null) {
      return null
    } else {
      return wrap(bibtexFile, month)
    }
  }
}
