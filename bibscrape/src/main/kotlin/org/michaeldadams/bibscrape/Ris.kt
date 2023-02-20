package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import ch.difty.kris.domain.RisRecord
import ch.difty.kris.domain.RisType
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Types as T

fun <A> List<A>.nonEmpty(): List<A>? = this.ifEmpty { null } // TODO: place for nonEmpty()

object Ris {
  val risTypes = mapOf(
    RisType.BOOK to T.BOOK,
    RisType.CONF to T.PROCEEDINGS,
    RisType.CHAP to T.INBOOK,
    // RisType.CHAPTER to T.INBOOK, // TODO
    // RisType.INCOL to T.INCOLLECTION, // TODO
    // RisType.JFULL to T.JOURNAL, // TODO
    RisType.JOUR to T.ARTICLE,
    RisType.MGZN to T.ARTICLE,
    RisType.PAMP to T.BOOKLET,
    RisType.RPRT to T.TECHREPORT,
    // RisType.REP to T.TECHREPORT, // TODO
    RisType.UNPB to T.UNPUBLISHED,
  )

  // sub ris-author(Array:D[Str:D] $names --> Str:D) {
  //   $names
  //     .map({ # Translate "last, first, suffix" to "von Last, Jr, First"
  //       s/ (.*) ',' (.*) ',' (.*) /$1,$3,$2/;
  //       / <-[,\ ]> / ?? $_ !! () })
  //     .join( ' and ' );
  // }
  private fun risAuthor(names: List<String>?): String =
    names
      .orEmpty()
      .map { it.replace("(.*) , (.*) , (.*)".r, "$1, $3, $2") } // Change "last, first, suffix" to "von Last, Jr, First"
      .filter { it.contains("[^,\\ ]") }
      .joinByAnd()

  fun bibtex(bibtexFile: BibtexFile, ris: RisRecord): BibtexEntry {
    val type = risTypes[ris.type] ?: run { println("Unknown RIS TY: ${ris.type}. Using misc."); T.MISC }
    val entry = bibtexFile.makeEntry(type, "")
    // my Array:D[Str:D] %ris = $ris.fields;
    // my BibScrape::BibTeX::Entry:D $entry = BibScrape::BibTeX::Entry.new();

    // my Regex:D $doi = rx/^ (\s* 'doi:' \s* \w+ \s+)? (.*) $/;
    val doi = "^ ( \\s* doi: \\s* \\w+ \\s+ )? (.*) $".r

    // sub set(Str:D $key, Str:_ $value --> Any:U) {
    //   $entry.fields{$key} = BibScrape::BibTeX::Value.new($value)
    //     if $value;
    //   return;
    // }

    // # A1|AU: author primary
    // set( 'author', ris-author(%ris<A1> // %ris<AU> // []));
    entry[F.AUTHOR] = risAuthor(ris.firstAuthors.nonEmpty() ?: ris.authors)
    // # A2|ED: author secondary
    // set( 'editor', ris-author(%ris<A2> // %ris<ED> // []));
    entry[F.EDITOR] = risAuthor(ris.secondaryAuthors.nonEmpty() ?: ris.editor?.let { listOf(it) })
    // TODO: ris editor should be list like ris.authors is?

    // my Str:D %self;
    // for %ris.kv -> Str:D $key, Array:D[Str:D] $value {
    //   %self{$key} = $value.join( '; ' );
    // }

    // # TY: ref type (INCOL|CHAPTER -> CHAP, REP -> RPRT)
    // $entry.type =
    //   %ris-types{%self<TY> // ''}
    //   // ((!%self<TY>.defined or say "Unknown RIS TY: {%self<TY>}. Using misc.") and 'misc');
    entry.entryType = risTypes[ris.type]
    // # ID: reference id
    // $entry.key = %self<ID>;
    entry[F.KEY] = ris.referenceId
    // # T1|TI|CT: title primary
    // # BT: title primary (books and unpub), title secondary (otherwise)
    val isBook = ris.type in setOf(RisType.BOOK, RisType.UNPB)
    // set( 'title', %self<T1> // %self<TI> // %self<CT> // ((%self<TY> // '') eq ( 'BOOK' | 'UNPB' )) && %self<BT>);
    entry[F.TITLE] = ris.primaryTitle ?: ris.title ?: where(isBook) { ris.bt }
    // set( 'booktitle', !((%self<TY> // '') eq ( 'BOOK' | 'UNPB' )) && %self<BT>);
    entry[F.BOOKTITLE] = where(!isBook) { ris.bt }
    // # T2: title secondary
    // set( 'journal', %self<T2>);
    entry[F.JOURNAL] = ris.secondaryTitle
    // # JF|JO: periodical name, full
    // # JA: periodical name, abbriviated
    // # J1: periodical name, user abbriv 1
    // # J2: periodical name, user abbriv 2
    // set( 'journal', %self<JF> // %self<JO> // %self<JA> // %self<J1> // %self<J2>);
    entry[F.JOURNAL] =
      ris.periodicalNameFullFormatJF
        ?: ris.periodicalNameFullFormatJO
        ?: ris.periodicalNameStandardAbbrevation
        ?: ris.periodicalNameUserAbbrevation
        ?: ris.alternativeTitle
        ?: ris.secondaryTitle
    // # T3: title series
    // set( 'series', %self<T3>);
    entry[F.SERIES] = ris.tertiaryTitle

    // # A3: author series
    // # A[4-9]: author (undocumented)
    // # Y1|PY: date primary
    // my Str:_ ($year, $month, $day) = (%self<DA> // %self<PY> // %self<Y1> // '').split(rx/ "/" | "-" /);
    val (year, month, day) =
      (ris.date ?: ris.publicationYear ?: ris.primaryDate ?: "").split("/|-".r) + listOf(null, null, null)
    // val (year, month, day) = listOf(0, 1, 2).map { date.getOrNull(it) }
    // set( 'year', $year);
    entry[F.YEAR] = year
    // $entry.fields<month> = BibScrape::BibTeX::Value.new(num2month($month))
    //   if $month;
    entry[F.MONTH] = month?.let { Bibtex.Months.intToMonth(entry.ownerFile, it) }
    // set( 'day', $day)
    //   if $day.defined;
    entry[F.DAY] = day
    // if (%self<C1>:exists) {
    //   %self<C1> ~~ / 'Full publication date: ' (\w+) '.'? ( ' ' \d+)? ', ' (\d+)/;
    //   ($month, $day, $year) = ($0, $1, $2);
    //   set( 'month', $month);
    // }
    // TODO: is the RIS C1 code still needed
    ris.custom1?.find("Full\\ publication\\ date:\\ (\\w+) \\.? (\\ \\d+)? ,\\ (\\d+)".r)?.let { match ->
      val (c1Month, c1Day, c1Year) = match.groupValues
      entry[F.MONTH] = c1Month ?: entry[F.MONTH]?.string
      entry[F.DAY] = c1Day ?: entry[F.DAY]?.string
    }
    // # Y2: date secondary

    // # N1|AB: notes (skip leading doi)
    // # N2: abstract (skip leading doi)
    // (%self<N1> // %self<AB> // %self<N2> // '') ~~ $doi;
    // set( 'abstract', $1.Str)
    //   if $1.Str.chars > 0;
    val abstract = (ris.notes ?: ris.abstr ?: ris.abstr2)?.find(doi)?.groupValues?.get(2) ?: ""
    if (abstract.length > 0) {
      entry[F.ABSTRACT] = abstract
    }
    // # KW: keyword. multiple
    // set( 'keywords', %self<KW>)
    //   if %self<KW>:exists;
    entry[F.KEYWORDS] = if (ris.keywords.isEmpty()) null else ris.keywords.joinToString("; ")
    // # RP: reprint status (too complex for what we need)

    // # VL: volume number
    // set( 'volume', %self<VL>);
    entry[F.VOLUME] = ris.volumeNumber
    // # IS|CP: issue
    // set( 'number', %self<IS> // %self<CP>);
    entry[F.NUMBER] = ris.issue ?: ris.cp
    // # SP: start page (may contain end page)
    // # EP: end page
    // set( 'pages', %self<EP> ?? "{%self<SP>}--{%self<EP>}" !! %self<SP>); # Note that SP may contain end page
    entry[F.PAGES] =
      // Note that SP may contain end page
      if (ris.endPage != null) "${ris.startPage}--${ris.endPage}" else ris.startPage
    // # CY: city
    // # PB: publisher
    // set( 'publisher', %self<PB>);
    entry[F.PUBLISHER] = ris.publisher
    // # SN: isbn or issn
    // set( 'issn', %self<SN>)
    //   if %self<SN> and %self<SN> ~~ / « \d ** 4 '-' \d ** 4 » /;
    if ((ris.isbnIssn ?: "").contains("\\b \\d{4} - \\d{4} \\b".r)) {
      entry[F.ISSN] = ris.isbnIssn
    }
    // set( 'isbn', %self<SN>)
    //   if %self<SN> and %self<SN> ~~ / « ([\d | 'X'] <[-\ ]>*) ** 10..13 » /;
    if ((ris.isbnIssn ?: "").contains("\\b ((\\d | X) [-\\ ]?){10,13} \\b".ri)) {
      entry[F.ISBN] = ris.isbnIssn
    }
    // #AD: address
    // #AV: (unneeded)
    // #M[1-3]: misc
    // #U[1-5]: user
    // # UR: multiple lines or separated by semi, may try for doi
    // set( 'url', %self<UR>)
    //   if %self<UR>:exists;
    entry[F.URL] = ris.url
    // #L1: link to pdf, multiple lines or separated by semi
    // #L2: link to text, multiple lines or separated by semi
    // #L3: link to records
    // #L4: link to images
    // # DO|DOI: doi
    // set( 'doi', %self<DO> // %self<DOI> // %self<M3> // (%self<N1> and %self<N1> ~~ $doi and $0));
    // TODO: ris<DOI>
    entry[F.DOI] = ris.doi ?: ris.typeOfWork ?: ris.notes?.find(doi)?.groupValues?.get(1)
    // # ER: End of record

    // $entry;
    return entry
  }

  // # #ABST         Abstract
  // # #INPR         In Press
  // # #JFULL                Journal (full)
  // # #SER          Serial (Book, Monograph)
  // # #THES phdthesis/mastersthesis Thesis/Dissertation
  // # IS
  // # CP|CY
}
