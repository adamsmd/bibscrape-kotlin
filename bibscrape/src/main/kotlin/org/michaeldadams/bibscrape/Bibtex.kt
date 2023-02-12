package org.michaeldadams.bibscrape

import bibtex.dom.BibtexAbstractValue
import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import bibtex.dom.BibtexMacroReference
import bibtex.dom.BibtexPerson
import bibtex.dom.BibtexPersonList
import bibtex.dom.BibtexString
import bibtex.expansions.PersonListExpander
import bibtex.parser.BibtexParser
import java.io.Reader
import java.io.StringReader

/** Joins a list of strings by the string " and ".
 *
 * @return the joined string
 */
fun Iterable<String>.joinByAnd(): String = this.joinToString(Bibtex.Names.AND)

/** Checks whether [field] exists in a [BibtexEntry].
 *
 * @param field the name of the field to check
 * @return `true` if [field] exists in the receiver and `false` if it does not
 */
@Suppress("FUNCTION_BOOLEAN_PREFIX")
fun BibtexEntry.contains(field: String): Boolean =
  this.fields.containsKey(field)

/** Runs [block] on the value of [field] if [field] exists in the receiver.
 *
 * @param A the type returned by [block]
 * @param field the name of the field to check
 * @param block the function to run if [field] exists.  Receives the value of the [field] as an argument.
 * @return the value returned by [block] if [field] exists in the receiver, otherwise `null`
 */
inline fun <A> BibtexEntry.ifField(field: String, block: (BibtexAbstractValue) -> A): A? =
  this[field]?.let(block)

/** Prints a warning if [field] exists in the receiver, but [block] returns
 * `false` on the value of that field.
 *
 * @param field the name of the field to check
 * @param msg the message to use in the warning
 * @param block the predicate to apply to the value of [field]
 * @return [Unit] if [block] returns `true`, otherwise `null`
 */
inline fun BibtexEntry.check(field: String, msg: String, block: (String) -> Boolean): Unit? =
  this.ifField(field) {
    val value = it.string
    if (!block(value)) {
      println("WARNING: ${msg}: ${value}")
    }
  }

/** Casts the receiver to a [BibtexString] and gets its string contents.
 *
 * @throws ClassCastException thrown If the receiver is not a [BibtexString]
 */
val BibtexAbstractValue.string: String
  get() = (this as BibtexString).content

/** Gets the value for a given [field] in the receiver.
 *
 * @param field the field for which to get the value
 * @return the value for the given [field]
 */
operator fun BibtexEntry.get(field: String): BibtexAbstractValue? =
  this.getFieldValue(field)

/** Sets the value for [field] in the receiver to be [value].
 *
 * @param field the field for which to set the value
 * @param value the value to which to set the field
 */
operator fun BibtexEntry.set(field: String, value: String?): Unit {
  this[field] = value?.let { this.ownerFile.makeString(it) }
}

/** Sets the value for [field] in the receiver to be [value].
 *
 * @param field the field for which to set the value
 * @param value the value to which to set the field
 */
operator fun BibtexEntry.set(field: String, value: BibtexAbstractValue?): Unit {
  if (value != null) this.setField(field, value) else this.undefineField(field)
}

/** Calls [updateValue], but with the [BibtexAbstractValue] values converted to
 * and from [String] values.
 *
 * @param field the name of the field to update
 * @param block the function to run on the value of [field]
 * @return [Unit] if [field] existed in the receiver, otherwise `null`
 * @see updateValue
 */
inline fun BibtexEntry.update(field: String, block: (String) -> String?): Unit? =
  this.updateValue(field) { this.ownerFile.makeString(block(it.string)) }

/** Sets the value for [field] in the receiver to be the result of applying [block]
 * to the previous value for [field] in the receiver.  If [block] returns `null`,
 * then [field] is removed from the receiver. If [field] does not exist in the
 * receiver, [block] is not called, and the value of [field] is not modified.
 *
 * @param field the name of the field to update
 * @param block the function to run on the value of [field]
 * @return [Unit] if [field] existed in the receiver, otherwise `null`
 */
inline fun BibtexEntry.updateValue(field: String, block: (BibtexAbstractValue) -> BibtexAbstractValue?): Unit? =
  this.ifField(field) {
    this[field] = block(it)
  }

/** Moves the value from field [src] to field [dst] in the receiver if [block]
 * returns `true` on the value of field [src].  If there is no [src] field,
 * then nothing is done.  If [src] is moved to [dst], then [src] is removed
 * from the receiver (i.e., this is "move", not "copy").
 *
 * @param src the field to move from
 * @param dst the field to move to
 * @param block the predicate to determine whether to do the move
 * @return [Unit] if field [src] existed in the receiver, otherwise `null`
 */
inline fun BibtexEntry.moveFieldIf(src: String, dst: String, block: (BibtexAbstractValue) -> Boolean): Unit? =
  this.ifField(src) {
    if (block(it)) {
      this[dst] = it
      this.undefineField(src)
    }
  }

/** Moves the value from field [src] to field [dst] in the receiver.  If there
 * is no [src] field, then nothing is done.  If [src] is moved to [dst], then
 * [src] is removed from the receiver (i.e., this is "move", not "copy").
 *
 * @param src the field to move from
 * @param dst the field to move to
 * @return [Unit] if field [src] existed in the receiver, otherwise `null`
 */
fun BibtexEntry.moveField(src: String, dst: String): Unit? = this.moveFieldIf(src, dst) { true }

/** Remove [field] from the receiver if [block] returns `true` on the existing value of [field].
 * If [field] does not exist in the receiver, then nothing is done.
 *
 * @param field the name of the field to remove
 * @param block the predicate to determine whether to remove [field]
 * @return [Unit] if [field] existed in the receiver, otherwise `null`
 */
inline fun BibtexEntry.removeIf(field: String, block: (String) -> Boolean): Unit? =
  this.update(field) { if (block(it)) null else it }

/** BibTeX utility functions. */
object Bibtex {
  /** Constants for BibTeX entry types. */
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

  /** Constants for BibTeX entry fields. */
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

  /** Constants and functions for BibTeX author and editor fields. */
  object Names {
    /** The string used by BibTex to separate names in author and editor fields. */
    const val AND = " and "

    /** The string used by BibTeX to represent "et al.". */
    const val OTHERS = "others"

    /** Parses a [String] containing BibTeX names.
     *
     * @param string the names to parse
     * @param entryKey the BibTex key to use in parse errors
     * @return the list of persons parsed from [string]
     */
    fun bibtexPersons(string: String, entryKey: String): List<BibtexPerson> {
      // bibtex.expansions.BibtexPersonListParser is not public so we have to go
      // through PersonListExpander
      val entry = BibtexFile().makeEntry("", entryKey)
      entry.ownerFile.addEntry(entry)
      entry[Fields.AUTHOR] = string

      PersonListExpander(true, true).expand(entry.ownerFile)
      val personList = (entry[Fields.AUTHOR] as BibtexPersonList).list

      @Suppress("UNCHECKED_CAST")
      return personList as List<BibtexPerson>
    }

    /** Parses a [String] containing a single BibTeX name.
     *
     * @param string the name to parse
     * @param entryKey the BibTex key to use in parse errors
     * @return the person produced by parsing
     */
    fun bibtexPerson(string: String, entryKey: String): BibtexPerson {
      val persons = bibtexPersons(string, entryKey)
      if (persons.size != 1) TODO()
      return persons.first()
    }

    /** Returns a name of a [BibtexPerson] as a simple [String] in "First von
     * Last Jr." order.
     *
     * @param person the [BibtexPerson] for which to produce the name
     * @return the [String] representing the name
     */
    fun simpleName(person: BibtexPerson): String =
      if (person.isOthers) {
        Bibtex.Names.OTHERS
      } else {
        listOf(person.first, person.preLast, person.last, person.lineage)
          .filterNotNull()
          .joinToString(" ")
      }
  }

  /** Constants and functions for the BibTeX month field. */
  object Months {
    private val longNames =
      "january february march april may june july august september october november december".split(" ")

    private val macroNames =
      "jan feb mar apr may jun jul aug sep oct nov dec".split(" ")

    private val monthMap = (
      listOf("sept" to "sep") +
        (macroNames zip macroNames) +
        (longNames zip macroNames)
      ).toMap()

    /** Converts a string containing a month number to the BibTeX macro for that month if it exists.
     *
     * @param bibtexFile the [BibTexFile] to use as the factory for the [BibtexMacroReference]
     * @param string the [String] to parse as a month number
     * @return a [BibtexMacroReference] for the given month or [null] if parsing failed
     */
    fun intToMonth(bibtexFile: BibtexFile, string: String): BibtexMacroReference? =
      string.toIntOrNull()?.let { macroNames.getOrNull(it) }?.let { bibtexFile.makeMacroReference(it) }

    /** Converts a string containing a month name to the BibTeX macro for that month if it exists.
     *
     * @param bibtexFile the [BibTexFile] to use as the factory for the [BibtexMacroReference]
     * @param string the [String] to parse as a month name
     * @return a [BibtexMacroReference] for the given month or [null] if parsing failed
     */
    fun stringToMonth(bibtexFile: BibtexFile, string: String): BibtexMacroReference? =
      monthMap[string.lowercase()]?.let { bibtexFile.makeMacroReference(it) }
  }

  /** Parses a [string] as a BibTeX file and returns the [BibtexEntry] values in it.
   *
   * @param string the string to parse
   * @return the [BibtexEntry] values in the parse result
   */
  fun parseEntries(string: String): List<BibtexEntry> =
    parse(string).entries.filterIsInstance<BibtexEntry>()

  /** Parses a [string] as a BibTeX file.
   *
   * @param string the string to parse
   * @return the parse result
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
}
