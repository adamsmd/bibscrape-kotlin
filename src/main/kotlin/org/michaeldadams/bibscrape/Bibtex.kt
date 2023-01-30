package org.michaeldadams.bibscrape

import bibtex.dom.BibtexAbstractValue
import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import bibtex.dom.BibtexMacroReference
import bibtex.dom.BibtexString
import bibtex.parser.BibtexParser
import java.io.Reader
import java.io.StringReader

fun Iterable<String>.joinByAnd(): String = this.joinToString(" and ")

fun BibtexEntry.contains(field: String): Boolean =
  this.fields.containsKey(field)

inline fun <A> BibtexEntry.ifField(field: String, block: (BibtexAbstractValue) -> A): A? =
  this[field]?.let(block)

inline fun BibtexEntry.check(field: String, msg: String, block: (String) -> Boolean): Unit? =
  this.ifField(field) {
    val value = it.string
    if (!block(value)) {
      println("WARNING: ${msg}: ${value}")
    }
  }

val BibtexAbstractValue.string: String // TODO: return null when not BibtexString
  get() = (this as? BibtexString)?.content ?: this.toString()

operator fun BibtexEntry.get(field: String): BibtexAbstractValue? =
  this.getFieldValue(field)

operator fun BibtexEntry.set(field: String, value: String) {
  this.setField(field, this.ownerFile.makeString(value))
}

operator fun BibtexEntry.set(field: String, value: BibtexAbstractValue) {
  this.setField(field, value)
}

inline fun BibtexEntry.update(field: String, block: (String) -> String?): Unit? =
  this.ifField(field) {
    val newValue = block(it.string)
    if (newValue != null) { this.set(field, newValue) } else { this.undefineField(field) }
  }

/** BibTeX utility functions. */
object Bibtex {
  @Suppress("UndocumentedPublicProperty")
  object Types {
    const val ARTICLE = "article"
    const val BOOK = "book"
    const val BOOKLET = "booklet"
    const val COLLECTION = "collection"
    const val CONFERENCE = "conference"
    const val DATASET = "dataset"
    const val ELECTRONIC = "electronic"
    const val INBOOK = "inbook"
    const val INCOLLECTION = "incollection"
    const val INPROCEEDINGS = "inproceedings"
    const val MANUAL = "manual"
    const val MASTERSTHESIS = "mastersthesis"
    const val MISC = "misc"
    const val PATENT = "patent"
    const val PERIODICAL = "periodical"
    const val PHDTHESIS = "phdthesis"
    const val PREPRINT = "preprint"
    const val PRESENTATION = "presentation"
    const val PROCEEDINGS = "proceedings"
    const val STANDARD = "standard"
    const val TECHREPORT = "techreport"
    const val THESIS = "thesis"
    const val UNPUBLISHED = "unpublished"
  }

  @Suppress("UndocumentedPublicProperty")
  object Fields {
    const val ABSTRACT = "abstract"
    const val ADDRESS = "address"
    const val AFFILIATION = "affiliation"
    const val ANNOTE = "annote"
    const val ARCHIVEPREFIX = "archiveprefix"
    const val ARTICLENO = "articleno"
    const val AUTHOR = "author"
    const val BIB_SCRAPE_URL = "bib_scrape_url"
    const val BOOKTITLE = "booktitle"
    const val CHAPTER = "chapter"
    const val CONFERENCE_DATE = "conference_date"
    const val CROSSREF = "crossref"
    const val DAY = "day"
    const val DOI = "doi"
    const val EDITION = "edition"
    const val EDITOR = "editor"
    const val EMAIL = "email"
    const val EPRINT = "eprint"
    const val HOWPUBLISHED = "howpublished"
    const val INSTITUTION = "institution"
    const val ISBN = "isbn"
    const val ISSN = "issn"
    const val ISSUE_DATE = "issue_date"
    const val JOURNAL = "journal"
    const val KEY = "key"
    const val KEYWORDS = "keywords"
    const val LANGUAGE = "language"
    const val LOCATION = "location"
    const val MONTH = "month"
    const val NOTE = "note"
    const val NUMBER = "number"
    const val NUMPAGES = "numpages"
    const val ORGANIZATION = "organization"
    const val PAGES = "pages"
    const val PRIMARYCLASS = "primaryclass"
    const val PUBLISHER = "publisher"
    const val SCHOOL = "school"
    const val SERIES = "series"
    const val TITLE = "title"
    const val TYPE = "type"
    const val URL = "url"
    const val VOLUME = "volume"
    const val YEAR = "year"
  }

  private val longNames =
    "january february march april may june july august september october november december".split(" ")
  private val macroNames =
    "jan feb mar apr may jun jul aug sep oct nov dec".split(" ")

  private val months = (
    listOf("sept" to "sep") +
      (macroNames zip macroNames) +
      (longNames zip macroNames)
    ).toMap()

  fun parseEntries(string: String): List<BibtexEntry> =
    parse(string).entries.filterIsInstance<BibtexEntry>()

  /** Parses a [string] into its constituent BibTeX entries.
   *
   * @param string the string to parse
   * @return the entries that were succesfully parsed
   */
  fun parse(string: String): BibtexFile = StringReader(string).use(::parse)

  /** Parses the contents of [reader] into its constituent BibTeX entries.
   *
   * @param reader the reader to parse
   * @return the entries that were succesfully parsed
   */
  fun parse(reader: Reader): BibtexFile {
    val bibtexFile = BibtexFile()
    val parser = BibtexParser(false) // false => don't throw parse exceptions
    parser.parse(bibtexFile, reader)
    return bibtexFile
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
    val month: String? = months[string.lowercase()]
    if (month == null) {
      return null
    } else {
      return wrap(bibtexFile, month)
    }
  }
}
