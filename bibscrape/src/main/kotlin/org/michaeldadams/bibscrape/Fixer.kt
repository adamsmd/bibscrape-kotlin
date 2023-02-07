package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexPerson
import bibtex.dom.BibtexString
import bibtex.dom.BibtexFile
import bibtex.dom.BibtexPersonList
import bibtex.expansions.PersonListExpander
// import bibtex.expansions.BibtexPersonListParser
// import org.bibsonomy.model.util.PersonNameParser
import java.util.Locale
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Types as T
import org.michaeldadams.bibscrape.Bibtex.Names as N

// enum MediaType <print online both>;
typealias NameMap = Map<String, BibtexPerson>
typealias NounMap = Map<String, String>
typealias StopWordSet = Set<String>

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
  val noEncode: List<String>,
  val noCollapse: List<String>,
  val omit: List<String>,
  val omitEmpty: List<String>
) {
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
      // TODO: is this the right syntax for ktlint-disable or is this disabling all rules?
      /* ktlint-disable indent */
      it.contains("^   \\d+           $".r) ||
      it.contains("^   \\d+ -- \\d+   $".r) ||
      it.contains("^   \\d+ (/ \\d+)* $".r) ||
      it.contains("^   \\d+ es        $".r) ||
      it.contains("^ S \\d+           $".r) ||
      it.contains("^   [A-Z]+         $".r) || // PACMPL conference abbreviations (e.g., ICFP)
      it.contains("^ Special\\ Issue\\ \\d+ (--\\d+)? $".r)
      /* ktlint-enable indent */
    }

    // self.isbn($entry, 'issn', $.issn-media, &canonical-issn);

    // self.isbn($entry, 'isbn', $.isbn-media, &canonical-isbn);

    // Change language codes (e.g., "en") to proper terms (e.g., "English")
    entry.update(F.LANGUAGE) { Locale.forLanguageTag(it)?.displayLanguage ?: it }

    entry.update(F.AUTHOR) { fixNames(it, entry.entryKey) }
    // if ($entry.fields<author>:exists) { $entry.fields<author> = $.canonical-names($entry.fields<author>) }
    entry.update(F.EDITOR) { fixNames(it, entry.entryKey) }
    // if ($entry.fields<editor>:exists) { $entry.fields<editor> = $.canonical-names($entry.fields<editor>) }

    // Don't include pointless URLs to publisher's page
    // TODO: check if should use \\. instead of \.
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
    """.trimMargin().r
    entry.removeIf(F.URL) { it.contains(publisherUrl) }

    // Year
    entry.check(F.YEAR, "Possibly incorrect year") { it.contains("^ \\d\\d\\d\\d $".r) }

    // Eliminate Unicode but not for no-encode fields (e.g. doi, url, etc.)
    //   for $entry.fields.keys -> Str:D $field {
    //     unless $field ∈ @.no-encode {
    //       update($entry, $field, {
    //         $_ = self.html($field eq 'title', from-xml("<root>{$_}</root>").root.nodes);
    //         # Repeated to handle nested results
    //         while s:g/ '{{' (<-[{}]>*) '}}' /\{$0\}/ {};
    //       });
    //     }
    //   }

    // ///////////////////////////////
    // Post-Unicode fixes           //
    // ///////////////////////////////

    // Canonicalize series: PEPM'97 -> PEPM~'97.  After Unicode encoding so that "'" doesn't get encoded.
    // TODO: entry.update(F.SERIES)
    //     { it.replace("^ ([A-Z]+) \\ * (19 | 20 | ' | \\{\\\\textquoteright\\} ) (\\d\\d) $".r, "$1~'$3") }
    entry.update(F.SERIES) { it.replace("([A-Z]+) \\ * (19 | 20 | ' | \\{\\\\textquoteright\\} ) (\\d+)".r, "$1~'$3") }

    // Collapse spaces and newlines.  After Unicode encoding so stuff from XML is caught.
    for (field in entry.fields.keys) {
      if (!noCollapse.contains(field)) {
        entry.update(field) {
          var v = it
          v = v.replace("\\s+ $".r, "") // Remove trailing whitespace
          v = v.replace("^ \\s+".r, "") // Remove leading whitespace
          v = v.replace("(\\n\\ *){2,}", "{\\par}") // BibTeX eats whitespace so convert "\n\n" to paragraph break
          v = v.replace("^ \\s* \\n \\s*".r, " ") // Remove extra line breaks
          v = v.replace(" ( \\s | \\{~\\} )* \\s ( \\s | \\{~\\} )* ".r, " ") // Remove duplicate whitespace
          v = v.replace(" \\s* \\{\\\\par\\} \\s* ".r, "\n{\\par}\n") // Nicely format paragraph breaks
          v
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
    // entry.update(F.MONTH) { month ->
    //   val parts = month
    //     .replace("\\. ($ | -)".r, "$1") // Remove dots due to abbriviations
    //     .split("\\b".r)
    //     .filter(::isNotEmpty)
    //     .map {
    //       (if (it.matches("/ | - | --")) BibtexString(it) else null) ?:
    //       (M.str2month(it)?.let(::BibtexString)) ?:
    //       (if it.matches("^ \\d+ $") num2month(it) else null) ?:
    //       run {
    //         println("WARNING: Possibly incorrect month: ${month}")
    //         BibtexString(it)
    //       }
    //     }
    //   BibtexAppend(parts)
    // }
    // update($entry, 'month', {
    //   s/ "." ($|"-") /$0/; # Remove dots due to abbriviations
    //   my BibScrape::BibTeX::Piece:D @x =
    //     .split(rx/<wb>/)
    //     .grep(rx/./)
    //     .map({
    //       $_ eq ( '/' | '-' | '--' ) and BibScrape::BibTeX::Piece.new($_) or
    //       str2month($_) or
    //       /^ \d+ $/ and num2month($_) or
    //       say "WARNING: Possibly incorrect month: $_" and BibScrape::BibTeX::Piece.new($_)});
    //   $_ = BibScrape::BibTeX::Value.new(@x)});

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
        (entry[F.AUTHOR]?.string ?: entry[F.EDITOR]?.string ?: "anon"),
        entry.entryKey
      ).first()
      .last
      .replace("\\\\ [^{}\\\\]+ \\{".r, "{") // Remove codes that add accents
      .replace("[^A-Za-z0-9]".r, "")

    // my BibScrape::BibTeX::Value:_ $name-value =
    //   $entry.fields<author> // $entry.fields<editor> // BibScrape::BibTeX::Value;
    // my Str:D $name = $name-value.defined ?? last-name(split-names($name-value.simple-str).head) !! 'anon';
    // $name ~~ s:g/ '\\' <-[{}\\]>+ '{' /\{/; # Remove codes that add accents
    // $name ~~ s:g/ <-[A..Za..z0..9]> //; # Remove non-alphanum

    val titleWord = (entry[F.TITLE]?.string ?: "")
      .replace("\\\\ [^{}\\\\]+ \\{".r, "{") // Remove codes that add accents
      // .replace("[-\\ ^A-Za-z0-9]".r, "") // Remove non-alphanum, space or hyphen
      .split("\\W+".r)
      .filter { !stopWords.contains(it.lowercase()) }
      .firstOrNull()
    val title = if (titleWord == null) "" else ":${titleWord}"
    // my BibScrape::BibTeX::Value:_ $title-value = $entry.fields<title>;
    // my Str:D $title = $title-value.defined ?? $title-value.simple-str !! '';
    // $title ~~ s:g/ '\\' <-[{}\\]>+ '{' /\{/; # Remove codes that add accents
    // $title ~~ s:g/ <-[\ \-/A..Za..z0..9]> //; # Remove non-alphanum, space or hyphen
    // $title = ($title.words.grep({$_.fc ∉ @.stop-words-strs}).head // '').fc;
    // $title = $title ne '' ?? ':' ~ $title !! '';

    val year = entry.ifField(F.YEAR) { ":${it.string}" } ?: ""

    val doi =
      entry.ifField(F.ARCHIVEPREFIX) { archiveprefix ->
        entry.ifField(F.EPRINT) { eprint ->
          if (archiveprefix.string == "arXiv") ":arXiv.${eprint.string}" else null
        }
      } ?: entry.ifField(F.DOI) { ":${it.string}" } ?: ""
    // my Str:D $doi = $entry.fields<doi>:exists ?? ':' ~ $entry.fields<doi>.simple-str !! '';
    // if $entry.fields<archiveprefix>:exists
    //     and $entry.fields<archiveprefix>.simple-str eq 'arXiv'
    //     and $entry.fields<eprint>:exists {
    //   $doi = ':arXiv.' ~ $entry.fields<eprint>.simple-str;
    // }
    entry.entryKey = name + year + title + doi

    // val unknownFields = entry.fields.keys subtract field
    // if (unknownFields.isNotEmpty()) { TODO("Unknown fields: ${unknownFields}") }
    // TODO: Duplicate fields
    //   # Put fields in a standard order (also cleans out any fields we deleted)
    //   my Int:D %fields = @.field.map(* => 0);
    //   for $entry.fields.keys -> Str:D $field {
    //     unless %fields{$field}:exists { die "Unknown field '$field'" }
    //     unless %fields{$field}.elems == 1 { die "Duplicate field '$field'" }
    //     %fields{$field} = 1;
    //   }
    //   $entry.set-fields(@.field.flatmap({ $entry.fields{$_}:exists ?? ($_ => $entry.fields{$_}) !! () }));

    return entry
  }

  // // method isbn(BibScrape::BibTeX::Entry:D $entry, Str:D $field, MediaType:D $print_or_online, &canonical --> Any:U) {
  // fun isbn(entry: BibtexEntry, field: String, mediaType: MediaType, canonicalize: (String, MediaType, String) -> String): Unit {
  //   //   update($entry, $field, {
  //   entry.update(field) { value ->
  //     if (value.isEmpty) {
  //       null
  //     } else {
  //       value.find("^ ([0-9X- ]+) \\ \\(Print\\)\\ ([0-9X- ]+) \\ \\(Online\\) $".ri) {
  //         when (type) {
  //           MediaType.print => canonicalize(it.groupValues[1], mediaType)
  //           MediaType.online => canonicalize(it.groupValues[1], mediaType)
  //           MediaType.both =>
  //             canonicalize(it.groupValues[1], mediaType) + " (Print) "
  //               canonicalize(it.groupValues[2], mediaType) + " (Online) "
  //         }
  //       } ?: canonicalize(value, type, sep)

  //   //     if m/^$/ {
  //   //       $_ = Str
  //   //     } elsif m:i/^ (<[0..9x\-\ ]>+) " (Print) " (<[0..9x\-\ ]>+) " (Online)" $/ {
  //   //       $_ = do given $print_or_online {
  //   //         when print {
  //   //           &canonical($0.Str, $.isbn-type, $.isbn-sep);
  //   //         }
  //   //         when online {
  //   //           &canonical($1.Str, $.isbn-type, $.isbn-sep);
  //   //         }
  //   //         when both {
  //   //           &canonical($0.Str, $.isbn-type, $.isbn-sep)
  //   //             ~ ' (Print) '
  //   //             ~ &canonical($1.Str, $.isbn-type, $.isbn-sep)
  //   //             ~ ' (Online)';
  //   //         }
  //   //       }
  //   //     } else {
  //   //       $_ = &canonical($_, $.isbn-type, $.isbn-sep);
  //   //     }
  //   //   });
  //   // }
  // }

  fun fixPerson(person: BibtexPerson): BibtexPerson =
    N.simpleName(person).let { name ->
      names[name.lowercase()] ?: run {
        // Check for and warn about names the publishers might have messed up
        val first = """
            \p{Upper}\p{Lower}+                       # Simple name
          | \p{Upper}\p{Lower}+ - \p{Upper}\p{Lower}+ # Hyphenated name with upper
          | \p{Upper}\p{Lower}+ - \p{Lower}\p{Lower}+ # Hyphenated name with lower
          | \p{Upper}\p{Lower}+   \p{Upper}\p{Lower}+ # "Asian" name (e.g. XiaoLin)
          # We could allow the following but publishers often abriviate
          # names when the actual paper doesn't
          # | \p{Upper} \.                            # Initial
          # | \p{Upper} \. - \p{Upper} \.             # Double initial
        """.trimIndent().r
        val middle = """\p{Upper} \.                  # Middle initial"""
        val last = """
            \p{Upper}\p{Lower}+                       # Simple name
          | \p{Upper}\p{Lower}+ - \p{Upper}\p{Lower}+ # Hyphenated name with upper
          | ( d' | D' | de | De | Di | Du | La | Le | Mac | Mc | O' | Van )
            \p{Upper}\p{Lower}+                       # Name with prefix
        """.trimIndent().r
        if (!name.matches("^ \\s* ${first} \\s+ (${middle} \\s+)? ${last} \\s* $".r)) {
          println("WARNING: Possibly incorrect name: ${name}")
        }

        person
      }
    }

  // // method canonical-names(BibScrape::BibTeX::Value:D $value --> BibScrape::BibTeX::Value:D) {
  fun fixNames(names: String, entryKey: String): String {
    val persons = N.bibtexPersons(names, entryKey).map(::fixPerson)

    // Warn about duplicate names
    persons.groupingBy(N::simpleName).eachCount().forEach {
      if (it.value > 1) { println("WARNING: Duplicate name: ${it.key}") }
    }

    return persons.map { it.toString() }.joinByAnd()
  }

  // method text(Bool:D $is-title, Str:D $str is copy, Bool:D :$math --> Str:D) {
  //   if $is-title {
  //   # Keep proper nouns capitalized
  //   # After eliminating Unicode in case a tag or attribute looks like a proper noun
  //     for @.noun-groups -> Str:D @noun-group {
  //       for @noun-group -> Str:D $noun {
  //         my Str:D $noun-no-brace = $noun.subst(rx/ <[{}]> /, '', :g);
  //         $str ~~ s:g/ « [$noun | $noun-no-brace] » /{@noun-group.head}/;
  //       }
  //     }

  //     for @.noun-groups -> Str:D @noun-group {
  //       for @noun-group -> Str:D $noun {
  //         my Str:D $noun-no-brace = $noun.subst(rx/ <[{}]> /, '', :g);
  //         for $str ~~ m:i:g/ « [$noun | $noun-no-brace] » / {
  //           if $/ ne @noun-group.head {
  //             say "WARNING: Possibly incorrectly capitalized noun '$/' in title";
  //           }
  //         }
  //       }
  //     }

  //   # Keep acronyms capitalized
  //   # Note that non-initial "A" are warned after collapsing spaces and newlines.
  //   # Anything other than "Aaaa" or "aaaa" triggers an acronym.
  //   # After eliminating Unicode in case a tag or attribute looks like an acronym
  //   $str ~~ s:g/ <!after '{'> ([<!before '_'> <alnum>]+ <upper> [<!before '_'> <alnum>]*) /\{$0\}/
  //     if $.escape-acronyms;
  //   $str ~~ s:g/ <wb> <!after '{'> ( <!after ' '> 'A' <!before ' '> | <!before 'A'> <upper>) <!before "'"> <wb>
  //              /\{$0\}/
  //     if $.escape-acronyms;
  //   }

  //   # NOTE: Ignores LaTeX introduced by translation from XML
  //   $str = unicode2tex($str, :$math, :ignore(rx/<[_^{}\\\$]>/));

  //   $str;
  // }

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

  // method html(Bool:D $is-title, @nodes where { $_.all ~~ XML::Node:D } --> Str:D) {
  //   @nodes.map({self.rec-node($is-title, $_)}).join
  // }

  // method rec-node(Bool:D $is-title, XML::Node:D $node --> Str:D) {
  //   given $node {
  //     when XML::CDATA { self.text($is-title, :!math, $node.data) }
  //     when XML::Comment { '' } # Remove HTML Comments
  //     when XML::Document { self.html($is-title, $node.root) }
  //     when XML::PI { '' }
  //     when XML::Text { self.text($is-title, :!math, decode-entities($node.text)) }

  //     when XML::Element {
  //       sub wrap(Str:D $tag --> Str:D) {
  //         my Str:D $str = self.html($is-title, $node.nodes);
  //         $str eq '' ?? '' !! "\\$tag\{" ~ $str ~ "\}"
  //       }
  //       if ($node.attribs<aria-hidden> // '') eq 'true' {
  //         ''
  //       } else {
  //         given $node.name {
  //           when 'a' and $node.attribs<class>:exists and $node.attribs<class> ~~
  //                    / « 'xref-fn' » / { '' } # Omit footnotes added by Oxford when on-campus
  //           when 'a' { self.html($is-title, $node.nodes) } # Remove <a> links
  //           when 'p' | 'par' { self.html($is-title, $node.nodes) ~ "\n\n" } # Replace <p> with \n\n
  //           when 'i' | 'italic' { wrap( 'textit' ) } # Replace <i> and <italic> with \textit
  //           when 'em' { wrap( 'emph' ) } # Replace <em> with \emph
  //           when 'b' | 'strong' { wrap( 'textbf' ) } # Replace <b> and <strong> with \textbf
  //           when 'tt' | 'code' { wrap( 'texttt' ) } # Replace <tt> and <code> with \texttt
  //           when 'sup' | 'supscrpt' { wrap( 'textsuperscript' ) } # Superscripts
  //           when 'sub' { wrap( 'textsubscript' ) } # Subscripts
  //           when 'svg' { '' }
  //           when 'script' { '' }
  //           when 'math' { $node.nodes ?? '\ensuremath{' ~ self.math($is-title, $node.nodes) ~ '}' !! '' }
  //           #when 'img' { '\{' ~ self.html($is-title, $node.nodes) ~ '}' }
  //             # $str ~~ s:i:g/"<img src=\"/content/" <[A..Z0..9]>+ "/xxlarge" (\d+)
  //                          ".gif\"" .*? ">"/{chr($0)}/; # Fix for Springer Link
  //           #when 'email' { '\{' ~ self.html($is-title, $node.nodes) ~ '}' }
  //             # $str ~~ s:i:g/"<email>" (.*?) "</email>"/$0/; # Fix for Cambridge
  //           when 'span' {
  //             if ($node.attribs<style> // '') ~~ / 'font-family:monospace' / {
  //               wrap( 'texttt' )
  //             } elsif $node.attribs<aria-hidden>:exists {
  //               ''
  //             } elsif $node.attribs<class>:exists {
  //               given $node.attribs<class> {
  //                 when / 'monospace' / { wrap( 'texttt' ) }
  //                 when / 'italic' / { wrap( 'textit' ) }
  //                 when / 'bold' / { wrap( 'textbf' ) }
  //                 when / 'sup' / { wrap( 'textsuperscript' ) }
  //                 when / 'sub' / { wrap( 'textsubscript' ) }
  //                 when / 'sc' | [ 'type' ? 'small' '-'? 'caps' ] | 'EmphasisTypeSmallCaps' / {
  //                   wrap( 'textsc' )
  //                 }
  //                 default { self.html($is-title, $node.nodes) }
  //               }
  //             } else {
  //               self.html($is-title, $node.nodes)
  //             }
  //           }
  //           default { say "WARNING: Unknown HTML tag: {$node.name}"; "[{$node.name}]" ~
  //                     self.html($is-title, $node.nodes) ~ "[/{$node.name}]" }
  //         }
  //       }
  //     }

  //     default { die "Unknown XML node type '{$node.^name}': $node" }
}
