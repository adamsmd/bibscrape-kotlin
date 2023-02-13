package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexPerson
import org.w3c.dom.Node
import org.w3c.dom.Element
import org.w3c.dom.CDATASection
import org.w3c.dom.Comment
import org.w3c.dom.Document
import org.w3c.dom.ProcessingInstruction
import org.w3c.dom.Text
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory
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
 * @property issnMedia which media type to prefer for an ISSN
 * @property isbnMedia which media type to prefer for an ISBN
 * @property isbnType which type of ISBN to prefer for an ISBN
 * @property isbnSep what separator to use an ISBN
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
  val issnMedia: MediaType,
  val isbnMedia: MediaType,
  val isbnType: IsbnType,
  val isbnSep: String,
  // # has Bool:D $.verbose is required;

  // // FIELD OPTIONS
  // val field: List<String>,
  val noEncode: Set<String>,
  val noCollapse: Set<String>,
  val omit: Set<String>,
  val omitEmpty: Set<String>
) {
  /** Returns a [BibtexEntry] that is a copy of [oldEntry] but with various fixes.
   *
   * @param oldEntry the [BibtexEntry] to fix
   * @return a copy of [oldEntry] but with various fixes applied
   */
  fun fix(oldEntry: BibtexEntry): BibtexEntry {
    val entry = oldEntry.ownerFile.makeEntry(oldEntry.entryType, oldEntry.entryKey)
    oldEntry.fields.forEach { name, value -> entry[name] = value }

    // ///////////////////////////////
    //  Pre-omit fixes              //
    // ///////////////////////////////

    // Doi: move from URL to DOI
    entry.moveFieldIf(F.URL, F.DOI) {
      !entry.contains(F.DOI) && it.string.contains("http s? :// (dx\\.)? doi\\.org/".ri)
    }

    // Fix wrong field names (SpringerLink and ACM violate this)
    for ((wrongName, rightName) in listOf("issue" to F.NUMBER, "keyword" to F.KEYWORDS)) {
      entry.moveFieldIf(wrongName, rightName) {
        !entry.contains(rightName) || it == entry[rightName]
      }
    }

    // Fix Springer's use of 'note' to store 'doi'
    entry.update(F.NOTE) { if (it == entry[F.DOI] ?: "") null else it }

    // ///////////////////////////////
    // Post-omit fixes              //
    // ///////////////////////////////

    // Omit fields we don't want.  Should be first after inter-field fixes.
    omit.forEach { entry.undefineField(it) }

    // Doi: remove "http://hostname/" or "DOI: "
    entry.update(F.DOI) {
      it
        .replace("http s? :// [^/]+ /".ri, "")
        .replace("doi: \\s*".ri, "")
    }

    // Page numbers: remove "pp." or "p."
    entry.update(F.PAGES) { it.replace("p p? \\. \\s*".ri, "") }

    // Ranges: convert "-" to "--"
    for (field in listOf(F.CHAPTER, F.MONTH, F.NUMBER, F.PAGES, F.VOLUME, F.YEAR)) {
      // Don't use en-dash in techreport numbers
      val dash = if (entry.entryType == T.TECHREPORT && field == F.NUMBER) "-" else "--"

      entry.update(field) { it.replace("\\s* (- | \\N{EN DASH} | \\N{EM DASH})+ \\s*".ri, dash) }
      entry.update(field) { it.replace("n/a -- n/a".ri, "") }
      entry.removeIf(field) { it.isEmpty() } // TODO: let omit-if-empty handle this?
      entry.update(field) { it.replace("\\b (\\w+) -- \\1".ri, "$1") }
      entry.update(field) { it.replace("(^|\\ ) (\\w+)--(\\w+)--(\\w+)--(\\w+) ($|,)".ri, "$1$2-$3--$4-$5$6") }
      entry.update(field) { it.replace("\\s+ , \\s+".ri, ", ") }
    }

    entry.check(F.PAGES, "Possibly incorrect page number") {
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
      it.contains("^ ${page} (, ${page})* $".r)
    }

    entry.check(F.VOLUME, "Possibly incorrect volume") {
      /* ktlint-disable indent */
      it.contains("^ \\d+          $".r) ||
      it.contains("^ \\d+  - \\d+  $".r) ||
      it.contains("^ [A-Z] - \\d+  $".r) ||
      it.contains("^ \\d+  - [A-Z] $".r)
      /* ktlint-enable indent */
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

    // self.isbn($entry, 'issn', $.issn-media, &canonical-issn);

    // self.isbn($entry, 'isbn', $.isbn-media, &canonical-isbn);

    // Change language codes (e.g., "en") to proper terms (e.g., "English")
    entry.update(F.LANGUAGE) { Locale.forLanguageTag(it)?.displayLanguage ?: it }

    entry.update(F.AUTHOR) { fixNames(it, entry.entryKey) }
    entry.update(F.EDITOR) { fixNames(it, entry.entryKey) }

    // Don't include pointless URLs to publisher's page
    val publisherUrl = """
      ^
      ( http s? ://doi\.acm\.org/
      | http s? ://doi\.ieeecomputersociety\.org/
      | http s? ://doi\.org/
      | http s? ://dx\.doi\.org/
      | http s? ://portal\.acm\.org/citation\.cfm
      | http s? ://www\.jstor\.org/stable/
      | http s? ://www\.sciencedirect\.com/science/article/
      )
    """.trimIndent().r
    entry.removeIf(F.URL) { it.contains(publisherUrl) }

    // Year
    entry.check(F.YEAR, "Possibly incorrect year") { it.contains("^ \\d\\d\\d\\d $".r) }

    // // Keywords
    entry.update(F.KEYWORDS) { it.replace("\\s* ; \\s*".r, "; ") }

    // Eliminate Unicode but not for no-encode fields (e.g. doi, url, etc.)
    //   for $entry.fields.keys -> Str:D $field {
    for ((field, value) in entry.fields) {
      // unless $field ∈ @.no-encode {
      if (!noEncode.contains(field)) {
        val xml = DocumentBuilderFactory
          .newInstance()
          .newDocumentBuilder()
          .parse("<root>${value.string}</root>".byteInputStream())
          .documentElement
          .childNodes
        var newValue = html(field == F.TITLE, xml)
        val doubleBrace = "\\{\\{ ([^{}]*) \\}\\}".r
        // Repeated to handle nested results
        while (newValue.matches(doubleBrace)) newValue = newValue.replace(doubleBrace, "{$1}")
        entry[field] = newValue
        // update($entry, $field, {
        //   $_ = self.html($field eq 'title', from-xml("<root>{$_}</root>").root.nodes);
        //   # Repeated to handle nested results
        //   while s:g/ '{{' (<-[{}]>*) '}}' /\{$0\}/ {};
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
      if (!noCollapse.contains(field)) {
        entry.update(field) {
          it
            .replace("\\s+ $".r, "") // Remove trailing whitespace
            .replace("^ \\s+".r, "") // Remove leading whitespace
            .replace("(\\n\\ *){2,}", "{\\par}") // BibTeX eats whitespace so convert "\n\n" to paragraph break
            .replace("^ \\s* \\n \\s*".r, " ") // Remove extra line breaks
            .replace(" ( \\s | \\{~\\} )* \\s ( \\s | \\{~\\} )* ".r, " ") // Remove duplicate whitespace
            .replace(" \\s* \\{\\\\par\\} \\s* ".r, "\n{\\par}\n") // Nicely format paragraph breaks
        }
      }
    }

    // Warn about capitalization of non-initial "A".
    // After collapsing spaces and newline because we are about initial "A".
    entry.ifField(F.TITLE) {
      if (escapeAcronyms && it.string.contains("\\ A\\ ".r)) {
        println("WARNING: 'A' may need to be wrapped in curly braces if it needs to stay capitalized")
      }
    }

    // Use bibtex month macros.  After Unicode encoding because it uses macros.
    entry.updateValue(F.MONTH) { month ->
      val parts = month
        .string
        .replace("\\. ($ | -)".r, "$1") // Remove dots due to abbriviations
        .split("\\b".r)
        .filter(String::isNotEmpty)
        .map {
          (if (setOf("/", "-", "--").contains(it)) entry.ownerFile.makeString(it) else null)
            ?: M.stringToMonth(entry.ownerFile, it)
            ?: M.intToMonth(entry.ownerFile, it)
            ?: run {
              println("WARNING: Unable to parse '${it}' in month '${month}'")
              entry.ownerFile.makeString(it)
            }
        }
        .filterNotNull()
      when (parts.size) {
        0 -> entry.ownerFile.makeString("")
        1 -> parts.first()
        else -> parts.drop(1).fold(parts.first(), entry.ownerFile::makeConcatenatedValue)
      }
    }

    // ///////////////////////////////
    // Final fixes                  //
    // ///////////////////////////////

    // Omit empty fields we don't want
    for (field in omitEmpty) {
      entry.removeIf(field) { it.contains("^( \\{\\} | \"\" | )$".r) }
    }

    // Generate an entry key
    val name =
      N.bibtexPersons(
        entry[F.AUTHOR]?.string ?: entry[F.EDITOR]?.string ?: "anon",
        entry.entryKey
      ).first()
        .last
        .replace("\\\\ [^{}\\\\]+ \\{".r, "{") // Remove codes that add accents
        .replace("[^A-Za-z0-9]".r, "") // Remove non-alphanum

    val titleWord = (entry[F.TITLE]?.string ?: "")
      .replace("\\\\ [^{}\\\\]+ \\{".r, "{") // Remove codes that add accents
      .replace("[^\\ ^A-Za-z0-9-]".r, "") // Remove non-alphanum, space or hyphen
      .split("\\s+".r)
      .filter { !stopWords.contains(it.lowercase()) }
      .filter { it.isNotEmpty() }
      .firstOrNull()
    val title = if (titleWord == null) "" else ":${titleWord}"

    val year = entry.ifField(F.YEAR) { ":${it.string}" } ?: ""

    val doi =
      entry.ifField(F.ARCHIVEPREFIX) { archiveprefix ->
        entry.ifField(F.EPRINT) { eprint ->
          if (archiveprefix.string == "arXiv") ":arXiv.${eprint.string}" else null
        }
      } ?: entry.ifField(F.DOI) { ":${it.string}" } ?: ""
    entry.entryKey = name + year + title + doi

    // val unknownFields = entry.fields.keys subtract field
    // if (unknownFields.isNotEmpty) { TODO("Unknown fields: ${unknownFields}") }
    // TODO: Duplicate fields

    return entry
  }

  fun isbn(
    entry: BibtexEntry,
    field: String,
    mediaType: MediaType,
    separator: String,
    canonicalize: (String, MediaType, String) -> String
  ): Unit {
    entry.update(field) { value ->
      if (value.isEmpty()) {
        null // TODO: not needed?
      } else {
        value.find("^ ([0-9X- ]+) \\ \\(Print\\)\\ ([0-9X- ]+) \\ \\(Online\\) $".ri)?.let {
          when (mediaType) {
            MediaType.PRINT -> canonicalize(it.groupValues[1], mediaType, separator)
            MediaType.ONLINE -> canonicalize(it.groupValues[2], mediaType, separator)
            MediaType.BOTH ->
              canonicalize(it.groupValues[1], mediaType, separator) + " (Print) " +
                canonicalize(it.groupValues[2], mediaType, separator) + " (Online) "
          }
        } ?: canonicalize(value, mediaType, separator)
      }
    }
  }

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
          println("WARNING: Publishers sometimes mangle names such as: ${name}")
        }

        person
      }
    }

  fun fixNames(names: String, entryKey: String): String {
    val persons = N.bibtexPersons(names, entryKey).map(::fixPerson)

    // Warn about duplicate names
    persons.groupingBy(N::simpleName).eachCount().forEach {
      if (it.value > 1) { println("WARNING: Duplicate name: ${it.key}") }
    }

    return persons.map { it.toString() }.joinByAnd()
  }

  // method text(Bool:D $is-title, Str:D $str is copy, Bool:D :$math --> Str:D) {
  fun text(isTitle: Boolean, math: Boolean, string: String): String {
    // if $is-title {
    // # Keep proper nouns capitalized
    // # After eliminating Unicode in case a tag or attribute looks like a proper noun
    //   for @.noun-groups -> Str:D @noun-group {
    //     for @noun-group -> Str:D $noun {
    //       my Str:D $noun-no-brace = $noun.subst(rx/ <[{}]> /, '', :g);
    //       $str ~~ s:g/ « [$noun | $noun-no-brace] » /{@noun-group.head}/;
    //     }
    //   }

    //   for @.noun-groups -> Str:D @noun-group {
    //     for @noun-group -> Str:D $noun {
    //       my Str:D $noun-no-brace = $noun.subst(rx/ <[{}]> /, '', :g);
    //       for $str ~~ m:i:g/ « [$noun | $noun-no-brace] » / {
    //         if $/ ne @noun-group.head {
    //           say "WARNING: Possibly incorrectly capitalized noun '$/' in title";
    //         }
    //       }
    //     }
    //   }

    // # Keep acronyms capitalized
    // # Note that non-initial "A" are warned after collapsing spaces and newlines.
    // # Anything other than "Aaaa" or "aaaa" triggers an acronym.
    // # After eliminating Unicode in case a tag or attribute looks like an acronym
    // $str ~~ s:g/ <!after '{'> ([<!before '_'> <alnum>]+ <upper> [<!before '_'> <alnum>]*) /\{$0\}/
    //   if $.escape-acronyms;
    // $str ~~ s:g/ <wb> <!after '{'> ( <!after ' '> 'A' <!before ' '> | <!before 'A'> <upper>) <!before "'"> <wb>
    //            /\{$0\}/
    //   if $.escape-acronyms;
    // }

    // # NOTE: Ignores LaTeX introduced by translation from XML
    // $str = unicode2tex($str, :$math, :ignore(rx/<[_^{}\\\$]>/));

    // $str;
    return Unicode.unicodeToTex(string, math) { "_^{}\\$".toSet().contains(it) }
  }

  // method math(Bool:D $is-title, @nodes where { $_.all ~~ XML::Node:D } --> Str:D) {
  //   @nodes.map({self.math-node($is-title,$_)}).join
  // }

  // method math-node(Bool:D $is-title, XML::Node:D $node --> Str:D) {
  //   given $node {
  //     when XML::CDATA { self.text($is-title, :math, $node.data) }
  //     when XML::Comment { '' } # Remove HTML Comments
  //     when XML::Document { self.math($is-title, $node.root) }
  //     when XML::PI { '' }
  //     when XML::Text { self.text($is-title, :math, decode-entities($node.text)) }
  //     when XML::Element {
  //       given $node.name {
  //         when 'mtext' { self.math($is-title, $node.nodes) }
  //         when 'mi' {
  //           ($node.attribs<mathvariant> // '') eq 'normal'
  //             ?? '\mathrm{' ~ self.math($is-title, $node.nodes) ~ '}'
  //             !! self.math($is-title, $node.nodes)
  //         }
  //         when 'mo' { self.math($is-title, $node.nodes) }
  //         when 'mn' { self.math($is-title, $node.nodes) }
  //         when 'msqrt' { '\sqrt{' ~ self.math($is-title, $node.nodes) ~ '}' }
  //         when 'mrow' { '{' ~ self.math($is-title, $node.nodes) ~ '}' }
  //         when 'mspace' { '\hspace{' ~ $node.attribs<width> ~ '}' }
  //         when 'msubsup' {
  //           '{' ~ self.math-node($is-title, $node.nodes[0])
  //           ~ '}_{' ~ self.math-node($is-title, $node.nodes[1])
  //           ~ '}^{' ~ self.math-node($is-title, $node.nodes[2])
  //           ~ '}'
  //         }
  //         when 'msub' {
  //          '{' ~ self.math-node($is-title, $node.nodes[0]) ~ '}_{' ~ self.math-node($is-title, $node.nodes[1])
  //               ~ '}' }
  //         when 'msup' {
  //          '{' ~ self.math-node($is-title, $node.nodes[0]) ~ '}^{' ~ self.math-node($is-title, $node.nodes[1]) ~ '}'
  //           }
  //         default { say "WARNING: Unknown MathML tag: {$node.name}"; "[{$node.name}]" ~
  //                   self.math($is-title, $node.nodes) ~ "[/{$node.name}]" }
  //       }
  //     }

  //     default { die "Unknown XML node type '{$node.^name}': $node" }
  //   }
  // }

  fun html(isTitle: Boolean, nodes: NodeList): String =
    (0 until nodes.length).map { html(isTitle, nodes.item(it)) }.joinToString("")
  // method html(Bool:D $is-title, @nodes where { $_.all ~~ XML::Node:D } --> Str:D) {
  //   @nodes.map({self.rec-node($is-title, $_)}).join
  // }

  // method rec-node(Bool:D $is-title, XML::Node:D $node --> Str:D) {
  fun html(isTitle: Boolean, node: Node): String =
    // given $node {
    when (node) {
      is CDATASection -> text(isTitle, false, node.data)
      // when XML::CDATA { self.text($is-title, :!math, $node.data) }
      is Comment -> "" // Remove HTML Comments
      // when XML::Comment { '' } # Remove HTML Comments
      is Document -> html(isTitle, node.documentElement)
      // when XML::Document { self.html($is-title, $node.root) }
      is ProcessingInstruction -> ""
      // when XML::PI { '' }
      is Text -> text(isTitle, false, node.data) // TODO: wrap node.text in decodeEntities
      // when XML::Text { self.text($is-title, :!math, decode-entities($node.text)) }

      // when XML::Element {
      is Element -> {
        fun wrap(tag: String): String {
          val string = html(isTitle, node.childNodes)
          return if (string == "") "" else "\\${tag}{${string}}"
        }
        // sub wrap(Str:D $tag --> Str:D) {
        //   my Str:D $str = self.html($is-title, $node.nodes);
        //   $str eq '' ?? '' !! "\\$tag\{" ~ $str ~ "\}"
        // }
        // if ($node.attribs<aria-hidden> // '') eq 'true' {
        //   ''
        // } else {
        if (node.getAttribute("aria-hidden") == "true") {
          ""
        } else {
          // given $node.name {
          when (node.nodeName) {
            // when 'a' and $node.attribs<class>:exists and $node.attribs<class> ~~
            //          / « 'xref-fn' » / { '' } # Omit footnotes added by Oxford when on-campus
            // when 'a' { self.html($is-title, $node.nodes) } # Remove <a> links
            "a" ->
              if (node.getAttribute("class").contains("\\b xref-fn \\b".r)) {
                "" // Omit footnotes added by Oxford when on-campus
              } else {
                html(isTitle, node.childNodes) // Remove <a> links
              }
            // when 'p' | 'par' { self.html($is-title, $node.nodes) ~ "\n\n" } # Replace <p> with \n\n
            "p", "par" -> html(isTitle, node.childNodes) + "\n\n" // Replace <p> with \n\n
            // when 'i' | 'italic' { wrap( 'textit' ) } # Replace <i> and <italic> with \textit
            "i", "italic" -> wrap("textit")
            // when 'em' { wrap( 'emph' ) } # Replace <em> with \emph
            "em" -> wrap("emph")
            // when 'b' | 'strong' { wrap( 'textbf' ) } # Replace <b> and <strong> with \textbf
            "b", "strong" -> wrap("textbf")
            // when 'tt' | 'code' { wrap( 'texttt' ) } # Replace <tt> and <code> with \texttt
            "tt", "code" -> wrap("texttt")
            // when 'sup' | 'supscrpt' { wrap( 'textsuperscript' ) } # Superscripts
            "sup", "supscrpt" -> wrap("textsuperscript")
            // when 'sub' { wrap( 'textsubscript' ) } # Subscripts
            "sub" -> wrap("textsubscript")
            // when 'svg' { '' }
            "svg" -> ""
            // when 'script' { '' }
            "script" -> ""
            // when 'math' { $node.nodes ?? '\ensuremath{' ~ self.math($is-title, $node.nodes) ~ '}' !! '' }
            // #when 'img' { '\{' ~ self.html($is-title, $node.nodes) ~ '}' }
            //   # $str ~~ s:i:g/"<img src=\"/content/" <[A..Z0..9]>+ "/xxlarge" (\d+)
            //                ".gif\"" .*? ">"/{chr($0)}/; # Fix for Springer Link
            // #when 'email' { '\{' ~ self.html($is-title, $node.nodes) ~ '}' }
            //   # $str ~~ s:i:g/"<email>" (.*?) "</email>"/$0/; # Fix for Cambridge
            // when 'span' {
            //   if ($node.attribs<style> // '') ~~ / 'font-family:monospace' / {
            //     wrap( 'texttt' )
            //   } elsif $node.attribs<aria-hidden>:exists {
            //     ''
            //   } elsif $node.attribs<class>:exists {
            //     given $node.attribs<class> {
            //       when / 'monospace' / { wrap( 'texttt' ) }
            //       when / 'italic' / { wrap( 'textit' ) }
            //       when / 'bold' / { wrap( 'textbf' ) }
            //       when / 'sup' / { wrap( 'textsuperscript' ) }
            //       when / 'sub' / { wrap( 'textsubscript' ) }
            //       when / 'sc' | [ 'type' ? 'small' '-'? 'caps' ] | 'EmphasisTypeSmallCaps' / {
            //         wrap( 'textsc' )
            //       }
            //       default { self.html($is-title, $node.nodes) }
            //     }
            //   } else {
            //     self.html($is-title, $node.nodes)
            //   }
            else -> {
              println("WARNING: Unknown HTML tag: ${node.nodeName}")
              "[${node.nodeName}]${html(isTitle, node.childNodes)}[/${node.nodeName}]"
            }
          }
        }
      }

      else -> TODO("Unknown XML node type '${node.javaClass.name}': ${node}")
      // default { die "Unknown XML node type '{$node.^name}': $node" }
    }
}
