package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexPerson
import bibtex.expansions.MacroReferenceExpander
import com.github.ladutsko.isbn.ISBN
import com.github.ladutsko.isbn.ISBNFormat
import org.jsoup.nodes.Comment
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.nodes.XmlDeclaration
import org.jsoup.parser.Parser
import java.util.Locale
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Months as M
import org.michaeldadams.bibscrape.Bibtex.Names as N
import org.michaeldadams.bibscrape.Bibtex.Types as T

typealias NameMap = Map<String, BibtexPerson>
typealias NounMap = Map<String, String>
typealias StopWordSet = Set<String>

/** What type of ISBN or ISSN media type to prefer. */
@Suppress("BRACES_BLOCK_STRUCTURE_ERROR")
enum class MediaType { PRINT, ONLINE, BOTH }

/** What type of ISBN to prefer. */
@Suppress("BRACES_BLOCK_STRUCTURE_ERROR")
enum class IsbnType { ISBN13, ISBN10, PRESERVE }

/** Configuration object for how to fix a [BibtexEntry].
 *
 * @property names a mapping from names to how they shold be formatted
 * @property nouns a mapping from nouns to how they shold be formatted
 * @property stopWords a set of words to be skipped when generating a key
 * @property escapeAcronyms whether to surround acronyms with curly braces
 * @property isbnMedia which media type to prefer for an ISBN
 * @property isbnType which type of ISBN to prefer for an ISBN
 * @property isbnSep what separator to use an ISBN
 * @property issnMedia which media type to prefer for an ISSN
 * @property issnSep what separator to use an ISSN
 * @property noEncode which BibTeX fields to not convert to using LaTeX escapes
 * @property noCollapse which BibTeX fields to not collapse multiple whitespaces
 * @property omit which BibTeX fields to omit
 * @property omitEmpty which BibTeX fields to omit if they are empty
 */
class Fixer(
  // // INPUTS
  val names: NameMap,
  val nouns: NounMap,
  val stopWords: StopWordSet,

  // // OPERATING MODES
  // has Bool:D $.scrape is required;
  // has Bool:D $.fix is required;

  // // GENERAL OPTIONS
  val escapeAcronyms: Boolean,
  val isbnMedia: MediaType,
  val isbnType: IsbnType,
  val isbnSep: String,
  val issnMedia: MediaType,
  val issnSep: String,
  // # has Bool:D $.verbose is required;

  // // FIELD OPTIONS
  // val field: List<String>,
  val noEncode: Set<String>,
  val noCollapse: Set<String>,
  val omit: Set<String>,
  val omitEmpty: Set<String>
) {
  /** Returns a [BibtexEntry] that is a copy of [oldEntry] but with commons errors fixed.
   *
   * @param oldEntry the [BibtexEntry] to fix
   * @return a copy of [oldEntry] but with various fixes applied
   */
  fun fix(oldEntry: BibtexEntry): BibtexEntry {
    val entry = oldEntry.clone()

    // Expand all macros
    @Suppress("ktlint:experimental:comment-wrapping")
    MacroReferenceExpander(
      /* expandStandardMacros = */ true,
      /* expandMonthAbbreviations = */ true,
      /* removeMacros = */ false,
      /* throwAllExpansionExceptions = */ true
    ).expand(entry.ownerFile)

    // ///////////////////////////////
    //  Pre-omit fixes              //
    // ///////////////////////////////

    // DOI: move from URL to DOI
    entry.moveFieldIf(F.URL, F.DOI) {
      !entry.contains(F.DOI) && it.string.contains("http s? :// (dx\\.)? doi\\.org/".ri)
    }

    // Fix wrong field names (SpringerLink and ACM violate this)
    for ((wrongName, rightName) in listOf("issue" to F.NUMBER, "keyword" to F.KEYWORDS)) {
      entry.moveFieldIf(wrongName, rightName) { !entry.contains(rightName) || it == entry[rightName] }
    }

    // Fix Springer's use of 'note' to store 'doi'
    entry.update(F.NOTE) { note -> ifOrNull(note != entry[F.DOI]?.string) { note } }

    // ///////////////////////////////
    // Post-omit fixes              //
    // ///////////////////////////////

    // Omit fields we don't want.  Should be first after inter-field fixes.
    omit.forEach { entry.undefineField(it) }

    // Doi: remove "http://hostname/" or "DOI: "
    entry.update(F.DOI) { it.remove("http s? :// [^/]+ /".ri).remove("doi: \\s*".ri) }

    // Page numbers: remove "pp." or "p."
    entry.update(F.PAGES) { it.remove("p p? \\. \\s*".ri) }

    // Ranges: convert "-" to "--"
    for (field in listOf(F.CHAPTER, F.MONTH, F.NUMBER, F.PAGES, F.VOLUME, F.YEAR)) {
      // Don't use en-dash in techreport numbers
      val dash = if (entry.entryType == T.TECHREPORT && field == F.NUMBER) "-" else "--"

      entry.update(field) { it.replace("\\s* (- | \\N{EN DASH} | \\N{EM DASH})+ \\s*".ri, dash) }
      entry.update(field) { it.remove("n/a -- n/a".ri) }
      entry.removeIf(field) { it.isEmpty() } // TODO: let omit-if-empty handle this?
      entry.update(field) { it.replace("${WBL} (\\w+) -- \\1 ${WBR}".ri, "$1") }
      entry.update(field) { it.replace("(^|\\ ) (\\w+)--(\\w+)--(\\w+)--(\\w+) ($|,)".ri, "$1$2-$3--$4-$5$6") }
      entry.update(field) { it.replace("\\s+ , \\s+".ri, ", ") }
    }

    val page = """
      # Arabic-digits
      \d+ |
      \d+ -- \d+ |

      # Roman-digits
      [XVIxvi]+ |
      [XVIxvi]+ -- [XVIxvi]+ |

      # Roman-digits dash Arabic-digits
      [XVIxvi]+ - \d+ |
      [XVIxvi]+ - \d+ -- [XVIxvi]+ - \d+ |

      # Arabic-Digits letter
      \d+ [a-z] |
      \d+ [a-z] -- \d+ [a-z] |

      # Arabic-digits separator Arabic-digits
      \d+ [.:/] \d+ |
      \d+ ([.:/]) \d+ -- \d+ \1 \d+ |

      # "Front" page
      f \d+ |
      f \d+ -- f \d+ |

      # "es" as ending number
      \d+ -- es
    """.trimIndent()
    entry.check(F.PAGES, "Possibly incorrect page number") { it.contains("^ ${page} (, ${page})* $".r) }

    entry.check(F.VOLUME, "Possibly incorrect volume") {
      it.contains("^   \\d+          $".r) ||
        it.contains("^ \\d+  - \\d+  $".r) ||
        it.contains("^ [A-Z] - \\d+  $".r) ||
        it.contains("^ \\d+  - [A-Z] $".r)
    }

    entry.check(F.NUMBER, "Possibly incorrect number") {
      it.contains("^     \\d+           $".r) ||
        it.contains("^   \\d+ -- \\d+   $".r) ||
        it.contains("^   \\d+ (/ \\d+)* $".r) ||
        it.contains("^   \\d+ es        $".r) ||
        it.contains("^ S \\d+           $".r) ||
        it.contains("^   [A-Z]+         $".r) || // PACMPL conference abbreviations (e.g., ICFP)
        it.contains("^ Special\\ Issue\\ \\d+ (--\\d+)? $".r)
    }

    isn(entry, F.ISBN, isbnMedia, ::canonicalizeIsbn)
    isn(entry, F.ISSN, issnMedia, ::canonicalizeIssn)

    // Change language codes (e.g., "en") to proper terms (e.g., "English")
    entry.update(F.LANGUAGE) { Locale.forLanguageTag(it)?.displayLanguage ?: it }

    entry.update(F.AUTHOR) { fixNames(it, entry.entryKey) }
    entry.update(F.EDITOR) { fixNames(it, entry.entryKey) }

    // Don't include redundant URLs to publisher's page
    val publisherUrl = """
      ^ (
        http s? ://doi\.acm\.org/ |
        http s? ://doi\.ieeecomputersociety\.org/ |
        http s? ://doi\.org/ |
        http s? ://dx\.doi\.org/ |
        http s? ://portal\.acm\.org/citation\.cfm |
        http s? ://www\.jstor\.org/stable/ |
        http s? ://www\.sciencedirect\.com/science/article/
      )
    """.trimIndent().r
    entry.removeIf(F.URL) { it.contains(publisherUrl) }

    entry.check(F.YEAR, "Possibly incorrect year") { it.contains("^ \\d\\d\\d\\d $".r) }

    entry.update(F.KEYWORDS) { it.replace("\\s* ; \\s*".r, "; ") }

    // Eliminate Unicode but not for no-encode fields (e.g. doi, url, etc.)
    for ((field, value) in entry.fields) {
      if (field !in noEncode) {
        val encodedValue = html(field == F.TITLE, Parser.parseBodyFragment(value.string, "").body()) // TODO: parseFragment

        // Remove doubly-nested braces.
        // We used fixedpoint in case there are multiple levels of nested braces.
        entry[field] = fixedpoint(encodedValue) { it.replace("\\{\\{ ([^{}]*) \\}\\}".r, "{$1}") }
      }
    }

    // ///////////////////////////////
    // Post-Unicode fixes           //
    // ///////////////////////////////

    // Canonicalize series: PEPM'97 -> PEPM~'97.  After Unicode encoding so that "'" doesn't get encoded.
    entry.update(F.SERIES) {
      it.replace("^ ([A-Z]+) \\ * (19 | 20 | ' | \\{\\\\textquoteright\\} ) (\\d\\d) $".r, "$1~'$3")
    }

    // Collapse spaces and newlines.  After Unicode encoding so stuff from XML is caught.
    for (field in entry.fields.keys) {
      if (field !in noCollapse) {
        entry.update(field) {
          it
            .trim()
            .replace(" (\\R \\ *){2,} ".r, "{\\\\par}") // BibTeX eats whitespace so convert "\n\n" to paragraph break
            .replace(" \\s* \\R \\s* ".r, " ") // Remove extra line breaks
            .replace(" ( \\s | \\{~\\} )* \\s ( \\s | \\{~\\} )* ".r, " ") // Remove duplicate whitespace
            .replace(" \\s* \\{\\\\par\\} \\s* ".r, "\n{\\\\par}\n") // Nicely format paragraph breaks
        }
      }
    }

    // Warn about capitalization of non-initial "A".
    // After collapsing spaces and newline because we are about initial "A".
    entry[F.TITLE]?.let {
      if (escapeAcronyms && it.string.contains("\\ A\\ ".r)) {
        println("WARNING: An 'A' in the title may need to be wrapped in curly braces if it needs to stay capitalized")
      }
    }

    // Use bibtex month macros.  After Unicode encoding because it uses macros.
    entry.updateValue(F.MONTH) { month ->
      month.string
        .replace("\\. ($ | -)".r, "$1") // Remove dots due to abbriviations
        .split("${WBL} | ${WBR}".r)
        .filter(String::isNotEmpty)
        .map { part ->
          if (part in setOf("/", "-", "--")) {
            entry.ownerFile.makeString(part)
          } else {
            M.stringToMonth(entry.ownerFile, part)
              ?: M.intToMonth(entry.ownerFile, part)
              ?: run {
                println("WARNING: Unable to parse '${part}' in month '${month}'")
                entry.ownerFile.makeString(part)
              }
          }
        }
        .reduce(entry.ownerFile::makeConcatenatedValue)
    }

    // TODO: remove 0 at front of 'day'

    // ///////////////////////////////
    // Final fixes                  //
    // ///////////////////////////////

    // Omit empty fields we don't want
    for (field in omitEmpty) { entry.removeIf(field) { it.contains("^( \\{\\} | \"\" | )$".r) } }

    // Generate an entry key
    val name = N.bibtexPersons(
      entry[F.AUTHOR]?.string ?: entry[F.EDITOR]?.string ?: "anon",
      entry.entryKey
    ).first()
      .let { it.preLast.orEmpty() + it.last }
      .replace("\\\\ [^{}\\\\]+ \\{".r, "{") // Remove codes that add accents
      .remove("[^A-Za-z0-9]".r) // Remove non-alphanum

    val title = entry[F.TITLE]?.string
      .orEmpty()
      .replace("\\\\ [^{}\\\\]+ \\{".r, "{") // Remove codes that add accents
      .remove("[^\\ ^A-Za-z0-9-]".r) // Remove non-alphanum, space or hyphen
      .split("\\s+".r)
      .filter { it.lowercase() !in stopWords }
      .filter { it.isNotEmpty() }
      .firstOrNull()
      ?.let { ":${it}" }
      .orEmpty()

    val year = entry[F.YEAR]?.let { ":${it.string}" }.orEmpty()

    val doi =
      entry[F.ARCHIVEPREFIX]?.let { archiveprefix ->
        entry[F.EPRINT]?.let { eprint ->
          ifOrNull(archiveprefix.string == "arXiv") { ":arXiv.${eprint.string}" }
        }
      } ?: entry[F.DOI]?.let { ":${it.string}" }.orEmpty()
    entry.entryKey = name + year + title + doi

    // val unknownFields = entry.fields.keys subtract field
    // if (unknownFields.isNotEmpty) { TODO("Unknown fields: ${unknownFields}") }
    // TODO: Duplicate fields

    return entry
  }

  /** Validates the checksum of and formats an ISBN.
   *
   * @param isbn the ISBN to validate and format
   * @return the formatted ISBN
   */
  fun canonicalizeIsbn(isbn: String): String {
    val checkDigit: Char = ISBN.calculateCheckDigit(isbn).last()
    if (checkDigit.toString() != isbn.last().uppercase()) {
      println("WARNING: Invalid Check Digit. Expected: ${checkDigit}. Got: ${isbn.last()}.")
    }
    val parsed = ISBN.parseIsbn(isbn.replace(".$".r, "${checkDigit}"))
    val dehyphenated = when (isbnType) {
      IsbnType.ISBN13 -> parsed.isbn13
      IsbnType.ISBN10 -> parsed.isbn10 ?: parsed.isbn13
      IsbnType.PRESERVE -> if (ISBN.isIsbn13(isbn)) parsed.isbn13 else parsed.isbn10
    }
    return ISBNFormat(isbnSep).format(dehyphenated)
  }

  @Suppress("WRONG_ORDER_IN_CLASS_LIKE_STRUCTURES")
  private object Issn {
    const val DIGIT_X_VALUE = 10
    const val NUM_DIGITS = 8
    const val MOD = 11
    const val PRE_SEP_LENGTH = 4
    const val POST_SEP_LENGTH = 4
  }

  /** Validates the checksum of and formats an ISSN.
   *
   * @param issn the ISSN to validate and format
   * @return the formatted ISSN
   */
  fun canonicalizeIssn(issn: String): String {
    val digits = issn.mapNotNull { digit ->
      digit.digitToIntOrNull() ?: ifOrNull(digit.uppercase() == "X") { Issn.DIGIT_X_VALUE }
    }
    if (digits.size != Issn.NUM_DIGITS) { TODO("invalid number of digits in ISSN: ${issn}") }

    val checkValue =
      (-digits.take(Issn.NUM_DIGITS - 1).mapIndexed { i, c -> c * (Issn.NUM_DIGITS - i) }.sum())
        .mod(Issn.MOD)
    val checkChar = if (checkValue == Issn.DIGIT_X_VALUE) "X" else checkValue.toString()
    if (checkValue != digits.last()) { println("Warning: Invalid Check Digit in ${issn} TODO") }

    return digits.take(Issn.PRE_SEP_LENGTH).joinToString("") +
      issnSep +
      digits.drop(Issn.PRE_SEP_LENGTH).take(Issn.POST_SEP_LENGTH - 1).joinToString("") +
      checkChar
  }

  /** Fixes an ISBN or ISSN.
   *
   * @param entry the BibTeX entry to update with the fixes
   * @param field which field of [entry] to update
   * @param mediaType whether to use print, online, or both
   * @param canonicalize how to canonicalize either the ISBN or ISSN
   */
  fun isn(entry: BibtexEntry, field: String, mediaType: MediaType, canonicalize: (String) -> String): Unit {
    entry.update(field) { value ->
      // TODO: null case not needed?
      ifOrNull(value.isNotEmpty()) {
        value.find("^ ([0-9X\\ -]+) \\ \\(Print\\)\\ ([0-9X\\ -]+) \\ \\(Online\\) $".ri)?.let {
          when (mediaType) {
            MediaType.PRINT -> canonicalize(it.groupValues[1])
            MediaType.ONLINE -> canonicalize(it.groupValues[2])
            MediaType.BOTH ->
              canonicalize(it.groupValues[1]) + " (Print) " +
                canonicalize(it.groupValues[2]) + " (Online) "
          }
        } ?: canonicalize(value)
      }
    }
  }

  /** Applies the name fixing and checking rules.
   *
   * @param person the person name to be fixed and checked
   * @return the person name with fixes applied
   */
  fun fixPerson(person: BibtexPerson): BibtexPerson =
    N.simpleName(person).let { name ->
      names[name.lowercase()] ?: run {
        // Check for and warn about names the publishers might have messed up
        val first = """
            \p{IsUpper}\p{IsLower}+                           # Simple name
          | \p{IsUpper}\p{IsLower}+ - \p{IsUpper}\p{IsLower}+ # Hyphenated name with upper
          | \p{IsUpper}\p{IsLower}+ - \p{IsLower}\p{IsLower}+ # Hyphenated name with lower
          | \p{IsUpper}\p{IsLower}+   \p{IsUpper}\p{IsLower}+ # "Asian" name (e.g. XiaoLin)
          # We could allow the following but publishers often abriviate
          # names when the actual paper doesn't
          # | \p{IsUpper} \.                                  # Initial
          # | \p{IsUpper} \. - \p{IsUpper} \.                 # Double initial

        """.trimIndent()

        @Suppress("MultilineRawStringIndentation")
        val middle = """
            \p{IsUpper} \.                                    # Middle initial

        """.trimIndent()

        val last = """
            \p{IsUpper}\p{IsLower}+                           # Simple name
          | \p{IsUpper}\p{IsLower}+ - \p{IsUpper}\p{IsLower}+ # Hyphenated name with upper
          | ( d' | D' | de | De | Di | Du | La | Le | Mac | Mc | O' | Van )
            \p{IsUpper}\p{IsLower}+                           # Name with prefix

        """.trimIndent()
        if (!name.contains("^ \\s* (${first}) \\s+ ((${middle}) \\s+)? (${last}) \\s* $".r)) {
          println("WARNING: Publishers sometimes mangle names such as: ${person}")
        }

        person
      }
    }

  /** Applies the name fixing and checking rules.
   *
   * @param names the names to be fixed and checked
   * @param entryKey the entry key to be used when parsing the names
   * @return the names with fixes applied
   */
  fun fixNames(names: String, entryKey: String): String {
    val persons = N.bibtexPersons(names, entryKey).map(::fixPerson)

    // Warn about duplicate names
    persons.groupingBy(N::simpleName).eachCount().forEach {
      if (it.value != 1) { println("WARNING: Duplicate name: ${it.key}") }
    }

    return persons.map { it.toString() }.joinByAnd()
  }

  /** Converts HTML to LaTeX.
   *
   * @param isTitle whether in a BibTeX title field
   * @param nodes the HTML nodes to convert
   * @return the LaTeX version of [nodes]
   */
  fun html(isTitle: Boolean, nodes: List<Node>): String = nodes.map { html(isTitle, it) }.joinToString("")

  /** Converts HTML to LaTeX.
   *
   * @param isTitle whether in a BibTeX title field
   * @param node the HTML node to convert
   * @return the LaTeX version of [node]
   */
  fun html(isTitle: Boolean, node: Node): String =
    when (node) {
      is Comment, is DataNode, is DocumentType, is XmlDeclaration -> ""
      is TextNode -> text(isTitle, false, node) // TODO: wrap node.text in decodeEntities
      is Element -> {
        /** Wrap the children of the current node ([node]) in a LaTeX command.
         *
         * @param command the command to wrap the children with
         * @return the command wrapped around the children of [node], or the empty string if [node] has no children
         */
        fun tex(command: String): String =
          html(isTitle, node.childNodes()).let { if (it.isEmpty()) "" else "\\${command}{${it}}" }
        if (node.attributes()["aria-hidden"] == "true") {
          ""
        } else {
          when (node.tag().name) {
            "script", "svg" -> ""

            "body" -> html(isTitle, node.childNodes())
            "p", "par" -> html(isTitle, node.childNodes()) + "\n\n"

            "a" ->
              if (node.attributes()["class"].contains("${WBL} xref-fn ${WBR}".r)) {
                // TODO: check if this happens
                "" // Omit footnotes added by Oxford when on-campus
              } else {
                html(isTitle, node.childNodes()) // Remove <a> links
              }

            "i", "italic" -> tex("textit")
            "em" -> tex("emph")
            "b", "strong" -> tex("textbf")
            "tt", "code" -> tex("texttt")
            "sup", "supscrpt" -> tex("textsuperscript")
            "sub" -> tex("textsubscript")

            "math" -> node.childNodes().let { if (it.isEmpty()) "" else "\\ensuremath{${math(isTitle, it)}}" }
            // #when 'img' { '\{' ~ self.html($is-title, $node.nodes) ~ '}' }
            //   # $str ~~ s:i:g/"<img src=\"/content/" <[A..Z0..9]>+ "/xxlarge" (\d+)
            //                ".gif\"" .*? ">"/{chr($0)}/; # Fix for Springer Link
            // #when 'email' { '\{' ~ self.html($is-title, $node.nodes) ~ '}' }
            //   # $str ~~ s:i:g/"<email>" (.*?) "</email>"/$0/; # Fix for Cambridge
            "span" -> {
              val classAttr = node.attributes()["class"]
              when {
                // TODO: node.attributes()["aria-hidden"] == "true" -> ""
                classAttr.contains("${WBL} monospace ${WBR}".r) ||
                  node.attributes()["style"].contains("${WBL} font-family:monospace ${WBR}") -> tex("texttt")
                classAttr.contains("${WBL} italic ${WBR}".r) -> tex("textit")
                classAttr.contains("${WBL} bold ${WBR}".r) -> tex("textbf")
                classAttr.contains("${WBL} sup ${WBR}".r) -> tex("textsuperscript")
                classAttr.contains("${WBL} sub ${WBR}".r) -> tex("textsubscript")
                classAttr.contains("${WBL} ( sc | (type)? small -? caps | EmphasisTypeSmallCaps ) ${WBR}".r) ->
                  tex("textsc")
                else -> html(isTitle, node.childNodes())
              }
            }
            else -> {
              println("WARNING: Unknown HTML tag: ${node.tag().name}")
              "[${node.tag().name}]${html(isTitle, node.childNodes())}[/${node.tag().name}]"
            }
          }
        }
      }

      else -> error("Unknown HTML node type '${node.javaClass.name}': ${node}")
    }

  /** Converts MathML to LaTeX.
   *
   * @param isTitle whether in a BibTeX title field
   * @param nodes the MathML nodes to convert
   * @return the LaTeX version of [nodes]
   */
  fun math(isTitle: Boolean, nodes: List<Node>): String = nodes.map { math(isTitle, it) }.joinToString("")

  /** Converts MathML to LaTeX.
   *
   * @param isTitle whether in a BibTeX title field
   * @param node the MathML node to convert
   * @return the LaTeX version of [node]
   */
  fun math(isTitle: Boolean, node: Node): String =
    when (node) {
      is Comment, is DataNode, is DocumentType, is XmlDeclaration -> ""
      is TextNode -> text(isTitle, true, node) // TODO: wrap node.text in decodeEntities
      is Element ->
        when (node.tag().name) {
          "mn", "mo", "mtext" -> math(isTitle, node.childNodes())
          "mi" ->
            if (node.attributes()["mathvariant"] == "normal") {
              "\\mathrm{${math(isTitle, node.childNodes())}}"
            } else {
              math(isTitle, node.childNodes())
            }
          "msqrt" -> "\\sqrt{${math(isTitle, node.childNodes())}}"
          "mrow" -> "{${math(isTitle, node.childNodes())}}"
          "mspace" -> "\\hspace{${node.attributes()["width"]}}"
          "msubsup" -> subsup(
            math(isTitle, node.childNodes()[0]),
            math(isTitle, node.childNodes()[1]),
            math(isTitle, node.childNodes()[2])
          )
          "msub" -> subsup(
            math(isTitle, node.childNodes()[0]),
            math(isTitle, node.childNodes()[1]),
            null
          )
          "msup" -> subsup(
            math(isTitle, node.childNodes()[0]),
            null,
            math(isTitle, node.childNodes()[1])
          )
          else -> {
            println("WARNING: Unknown MathML tag: ${node.tag().name}")
            "[${node.tag().name}]${math(isTitle, node.childNodes())}[/${node.tag().name}]"
          }
        }
      else -> error("Unknown MathML node type '${node.javaClass.name}': ${node}")
    }

  /** Formats subscripts and superscripts for LaTeX.
   *
   * @param base the string to which the subscript and/or superscript is applied
   * @param sub the subscript to apply or `null` if there should be no subscript
   * @param sup the superscript to apply or `null` if there should be no superscript
   * @return the LaTeX with the subscript and supper script applied to [base]
   * */
  fun subsup(base: String, sub: String?, sup: String?): String =
    "{${base}}" + sub?.let { "_{${it}}" }.orEmpty() + sup?.let { "^{${it}}" }.orEmpty()

  /** Convert a text node containing Unicode to LaTeX.
   *
   * @param isTitle whether in a BibTeX title field, which requires extra work so BibTeX preserves capitalization
   * @param math whether already in a MathML
   * @param textNode the text node to convert
   * @return the LaTeX version of [textNode]
   */
  fun text(isTitle: Boolean, math: Boolean, textNode: TextNode): String {
    var s = textNode.wholeText
    if (isTitle) {
      // TODO: combine these loops
      // Keep proper nouns capitalized
      // Inside html()/math() in case a tag or attribute looks like a proper noun
      for ((key, value) in nouns) {
        val keyNoBrace = key.remove("[{}]".r)
        s = s.replace(
          "(?<! ${ALNUM}) ( ${Regex.escape(key)} | ${Regex.escape(keyNoBrace)} ) (?! ${ALNUM})".r,
          "${Regex.escapeReplacement(value)}"
        )
      }
      // Re-run things case insentitively in case the "nouns" setting didn't catch something
      // (We don't want to automatically convert case insensitively, since that might be too broad.)
      for ((key, value) in nouns) {
        val keyNoBrace = key.remove("[{}]".r)
        s.find("(?<! ${ALNUM}) ( ${Regex.escape(key)} | ${Regex.escape(keyNoBrace)} ) (?! ${ALNUM})".ri)?.let {
          if (it.value != value) { println("WARNING: Possibly incorrectly capitalized noun '${it.value}' in title") }
        }
      }

      // Keep acronyms capitalized
      // Note that non-initial "A" are warned after collapsing spaces and newlines.
      // Anything other than "Aaaa" or "aaaa" triggers an acronym.
      // After eliminating Unicode in case a tag or attribute looks like an acronym
      if (escapeAcronyms) {
        val notAfterBrace = """(?<! \{ )"""
        s = s
          .replace("""${notAfterBrace} ${WBL} ( ${ALNUM}+ \p{IsUppercase} ${ALNUM}* )""".r, "{$1}")
          .replace("""${notAfterBrace} ${WBL} ( (?<! \ ) A (?! \ )     ) (?! ') ${WBR}""".r, "{$1}")
          .replace("""${notAfterBrace} ${WBL} ( (?! A) \p{IsUppercase} ) (?! ') ${WBR}""".r, "{$1}")
      }
    }

    // NOTE: Ignores LaTeX introduced by translation from XML
    return Unicode.unicodeToTex(s, math) { it in "_^{}\\$".toSet() }
  }
}
