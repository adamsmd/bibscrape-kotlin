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

  private fun risAuthor(names: List<String>?): String =
    names
      .orEmpty()
      // Change "last, first, suffix" to "von Last, Jr, First"
      .map { it.replace("^ (.*) , (.*) , (.*) $".r, "$1, $3, $2") }
      .filter { it.contains("[^,\\ ]") }
      .joinByAnd()

  fun bibtex(bibtexFile: BibtexFile, ris: RisRecord): BibtexEntry {
    // TY: ref type (INCOL|CHAPTER -> CHAP, REP -> RPRT)
    val type = risTypes[ris.type] ?: run { println("Unknown RIS TY: ${ris.type}. Using misc."); T.MISC }
    val entry = bibtexFile.makeEntry(type, "")

    // my Regex:D $doi = rx/^ (\s* 'doi:' \s* \w+ \s+)? (.*) $/;
    val doi = "^ \\s* ( doi: \\s* \\S+ ) \\s+".r

    // # A1|AU: author primary
    entry[F.AUTHOR] = risAuthor(ris.firstAuthors.nonEmpty() ?: ris.authors)
    // # A2|ED: author secondary
    entry[F.EDITOR] = risAuthor(ris.secondaryAuthors.nonEmpty() ?: ris.editor?.let { listOf(it) })
    // TODO: ris editor should be list like ris.authors is?

    // my Str:D %self;
    // for %ris.kv -> Str:D $key, Array:D[Str:D] $value {
    //   %self{$key} = $value.join( '; ' );
    // }

    // ID: reference id
    entry[F.KEY] = ris.referenceId
    // T1|TI|CT: title primary
    // BT: title primary (books and unpub), title secondary (otherwise)
    val isBook = ris.type in setOf(RisType.BOOK, RisType.UNPB)
    // set( 'title', %self<T1> // %self<TI> // %self<CT> // ((%self<TY> // '') eq ( 'BOOK' | 'UNPB' )) && %self<BT>);
    entry[F.TITLE] = ris.primaryTitle ?: ris.title ?: ris.unpublishedReferenceTitle ?: ifOrNull(isBook) { ris.bt }
    entry[F.BOOKTITLE] = ifOrNull(!isBook) { ris.bt }
    // T2: title secondary
    entry[F.JOURNAL] = ris.secondaryTitle
    // JF|JO: periodical name, full
    // JA: periodical name, abbriviated
    // J1: periodical name, user abbriv 1
    // J2: periodical name, user abbriv 2
    entry[F.JOURNAL] =
      ris.periodicalNameFullFormatJF
        ?: ris.periodicalNameFullFormatJO
        ?: ris.periodicalNameStandardAbbrevation
        ?: ris.periodicalNameUserAbbrevation
        ?: ris.alternativeTitle
        ?: ris.secondaryTitle
    // T3: title series
    entry[F.SERIES] = ris.tertiaryTitle

    // A3: author series
    // A[4-9]: author (undocumented)
    // TODO?

    // Y1|PY: date primary
    val (year, month, day) =
      (ris.date ?: ris.publicationYear ?: ris.primaryDate).orEmpty().split("/ | -".r) + listOf(null, null, null)
    entry[F.YEAR] = year
    entry[F.MONTH] = month?.let { Bibtex.Months.intToMonth(entry.ownerFile, it) }
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

    // Y2: date secondary
    // TODO

    // N1|AB: notes (skip leading doi)
    // N2: abstract (skip leading doi)
    val abstract = (ris.notes ?: ris.abstr ?: ris.abstr2).orEmpty().remove(doi)
    if (abstract.length > 0) { // TODO: better way for length > 0
      entry[F.ABSTRACT] = abstract
    }
    // KW: keyword. multiple
    entry[F.KEYWORDS] = if (ris.keywords.isEmpty()) null else ris.keywords.joinToString("; ")

    // RP: reprint status (too complex for what we need)

    // VL: volume number
    entry[F.VOLUME] = ris.volumeNumber
    // IS|CP: issue
    entry[F.NUMBER] = ris.issue ?: ris.cp
    // SP: start page (may contain end page)
    // EP: end page
    entry[F.PAGES] = if (ris.endPage != null) "${ris.startPage}--${ris.endPage}" else ris.startPage
    // CY: city
    // PB: publisher
    entry[F.PUBLISHER] = ris.publisher
    // SN: isbn or issn
    if (ris.isbnIssn.orEmpty().contains("\\b ${ISSN_REGEX} \\b".r)) {
      entry[F.ISSN] = ris.isbnIssn
    }
    if (ris.isbnIssn.orEmpty().contains("\\b ${ISBN_REGEX} \\b".ri)) {
      entry[F.ISBN] = ris.isbnIssn
    }
    // AD: address
    // AV: (unneeded)
    // M[1-3]: misc
    // U[1-5]: user

    // UR: multiple lines or separated by semi, may try for doi
    entry[F.URL] = ris.url
    // L1: link to pdf, multiple lines or separated by semi
    // L2: link to text, multiple lines or separated by semi
    // L3: link to records
    // L4: link to images

    // DO|DOI: doi
    // TODO: ris<DOI>
    entry[F.DOI] = ris.doi ?: ris.typeOfWork ?: ris.notes?.find(doi)?.groupValues?.firstOrNull()

    // ER: End of record

    return entry
  }

  // ABST         Abstract
  // INPR         In Press
  // JFULL                Journal (full)
  // SER          Serial (Book, Monograph)
  // THES phdthesis/mastersthesis Thesis/Dissertation
  // IS
  // CP|CY
}
