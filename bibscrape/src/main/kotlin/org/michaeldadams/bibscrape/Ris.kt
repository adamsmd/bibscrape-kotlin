package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import ch.difty.kris.domain.RisRecord
import ch.difty.kris.domain.RisType
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Types as T

object Ris {
  val risTypes = mapOf(
    RisType.BOOK to T.BOOK,
    RisType.CONF to T.PROCEEDINGS,
    RisType.CHAP to T.INBOOK,
    // RisType.CHAPTER to T.INBOOK,
    // RisType.INCOL to T.INCOLLECTION,
    // RisType.JFULL to T.JOURNAL,
    RisType.JOUR to T.ARTICLE,
    RisType.MGZN to T.ARTICLE,
    RisType.PAMP to T.BOOKLET,
    RisType.RPRT to T.TECHREPORT,
    // RisType.REP to T.TECHREPORT,
    RisType.UNPB to T.UNPUBLISHED,
  )

  // my Str:D %ris-types = <
  //   BOOK book
  //   CONF proceedings
  //   CHAP inbook
  //   CHAPTER inbook
  //   INCOL incollection
  //   JFULL journal
  //   JOUR article
  //   MGZN article
  //   PAMP booklet
  //   RPRT techreport
  //   REP techreport
  //   UNPB unpublished>;

  // sub ris-author(Array:D[Str:D] $names --> Str:D) {
  //   $names
  //     .map({ # Translate "last, first, suffix" to "von Last, Jr, First"
  //       s/ (.*) ',' (.*) ',' (.*) /$1,$3,$2/;
  //       / <-[,\ ]> / ?? $_ !! () })
  //     .join( ' and ' );
  // }

  // sub bibtex-of-ris(Ris:D $ris --> BibScrape::BibTeX::Entry:D) is export {
  fun bibtex(bibtexFile: BibtexFile, ris: RisRecord): BibtexEntry {
    val type = risTypes[ris.type] ?: run { println("Unknown RIS TY: ${ris.type}. Using misc."); T.MISC }
    val entry = bibtexFile.makeEntry(type, "")
    // my Array:D[Str:D] %ris = $ris.fields;
    // my BibScrape::BibTeX::Entry:D $entry = BibScrape::BibTeX::Entry.new();

    // my Regex:D $doi = rx/^ (\s* 'doi:' \s* \w+ \s+)? (.*) $/;

    // sub set(Str:D $key, Str:_ $value --> Any:U) {
    //   $entry.fields{$key} = BibScrape::BibTeX::Value.new($value)
    //     if $value;
    //   return;
    // }

    // # A1|AU: author primary
    // set( 'author', ris-author(%ris<A1> // %ris<AU> // []));
    // # A2|ED: author secondary
    // set( 'editor', ris-author(%ris<A2> // %ris<ED> // []));

    // my Str:D %self;
    // for %ris.kv -> Str:D $key, Array:D[Str:D] $value {
    //   %self{$key} = $value.join( '; ' );
    // }

    // # TY: ref type (INCOL|CHAPTER -> CHAP, REP -> RPRT)
    // $entry.type =
    //   %ris-types{%self<TY> // ''}
    //   // ((!%self<TY>.defined or say "Unknown RIS TY: {%self<TY>}. Using misc.") and 'misc');
    // # ID: reference id
    // $entry.key = %self<ID>;
    entry[F.KEY] = ris.referenceId
    // # T1|TI|CT: title primary
    // # BT: title primary (books and unpub), title secondary (otherwise)
    // set( 'title', %self<T1> // %self<TI> // %self<CT> // ((%self<TY> // '') eq ( 'BOOK' | 'UNPB' )) && %self<BT>);
    // set( 'booktitle', !((%self<TY> // '') eq ( 'BOOK' | 'UNPB' )) && %self<BT>);
    // # T2: title secondary
    // set( 'journal', %self<T2>);
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
    // set( 'year', $year);
    // $entry.fields<month> = BibScrape::BibTeX::Value.new(num2month($month))
    //   if $month;
    // if (%self<C1>:exists) {
    //   %self<C1> ~~ / 'Full publication date: ' (\w+) '.'? ( ' ' \d+)? ', ' (\d+)/;
    //   ($month, $day, $year) = ($0, $1, $2);
    //   set( 'month', $month);
    // }
    // set( 'day', $day)
    //   if $day.defined;
    // # Y2: date secondary

    // # N1|AB: notes (skip leading doi)
    // # N2: abstract (skip leading doi)
    // (%self<N1> // %self<AB> // %self<N2> // '') ~~ $doi;
    // set( 'abstract', $1.Str)
    //   if $1.Str.chars > 0;
    // # KW: keyword. multiple
    // set( 'keywords', %self<KW>)
    //   if %self<KW>:exists;
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
    // # CY: city
    // # PB: publisher
    // set( 'publisher', %self<PB>);
    entry[F.PUBLISHER] = ris.publisher
    // # SN: isbn or issn
    // set( 'issn', %self<SN>)
    entry[F.ISSN] = ris.isbnIssn
    //   if %self<SN> and %self<SN> ~~ / « \d ** 4 '-' \d ** 4 » /;
    // set( 'isbn', %self<SN>)
    //   if %self<SN> and %self<SN> ~~ / « ([\d | 'X'] <[-\ ]>*) ** 10..13 » /;
    // #AD: address
    // #AV: (unneeded)
    // #M[1-3]: misc
    // #U[1-5]: user
    // # UR: multiple lines or separated by semi, may try for doi
    // set( 'url', %self<UR>)
    //   if %self<UR>:exists;
    // #L1: link to pdf, multiple lines or separated by semi
    // #L2: link to text, multiple lines or separated by semi
    // #L3: link to records
    // #L4: link to images
    // # DO|DOI: doi
    // set( 'doi', %self<DO> // %self<DOI> // %self<M3> // (%self<N1> and %self<N1> ~~ $doi and $0));
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
