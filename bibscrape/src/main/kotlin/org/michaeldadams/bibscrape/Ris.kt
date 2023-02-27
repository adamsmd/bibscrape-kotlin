package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import ch.difty.kris.domain.RisRecord
import ch.difty.kris.domain.RisType
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Types as T

/** RIS utility functions. */
object Ris {
  @Suppress("ktlint:trailing-comma-on-call-site")
  private val risTypes = mapOf(
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

  private fun risAuthor(names: List<String>?): String? =
    names
      .orEmpty()
      // Change "last, first, suffix" to "von Last, Jr, First"
      .map { it.replace("^ (.*) , (.*) , (.*) $".r, "$1, $3, $2") }
      .filter { it.contains("[^,\\ ]".r) }
      .joinByAnd()
      .ifEmpty { null }

  /** Generates a BibTeX entry from an RIS record.
   *
   * @param bibtexFile the [BibtexFile] that shoud own the generated entry
   * @param ris the RIS record from which to generate the entry
   * @return the generated BibTeX entry
   */
  fun bibtex(bibtexFile: BibtexFile, ris: RisRecord): BibtexEntry {
    // TY: ref type (INCOL|CHAPTER -> CHAP, REP -> RPRT)
    val type = risTypes[ris.type] ?: T.MISC.also { println("Unknown RIS TY: ${ris.type}. Using misc.") }
    val entry = bibtexFile.makeEntry(type, "")

    // # A1|AU: author primary
    entry[F.AUTHOR] = risAuthor(ris.firstAuthors.ifEmpty { ris.authors })
    // # A2|ED: author secondary
    entry[F.EDITOR] = risAuthor(ris.secondaryAuthors.ifEmpty { ris.editor?.let { listOf(it) } })
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
    entry[F.DAY] = day?.ifEmpty { null }
    // TODO: is the RIS C1 code still needed
    ris.custom1?.find("Full\\ publication\\ date:\\ (\\w+) \\.? (\\ \\d+)? ,\\ (\\d+)".r)?.let { match ->
      val (c1Month, c1Day, /* c1Year */ _) = // ktlint-disable experimental:comment-wrapping
        match.groupValues + listOf(null, null, null)
      entry[F.MONTH] = c1Month ?: entry[F.MONTH]?.string
      entry[F.DAY] = c1Day ?: entry[F.DAY]?.string
    }

    // Y2: date secondary
    // TODO

    // N1|AB: notes (skip leading doi)
    // N2: abstract (skip leading doi)
    val doi = "^ \\s* ( doi: \\s* \\S+ ) \\s+".r
    entry[F.ABSTRACT] = (ris.notes ?: ris.abstr ?: ris.abstr2)?.remove(doi)?.ifEmpty { null }
    // KW: keyword. multiple
    entry[F.KEYWORDS] = ris.keywords.orEmpty().joinToString("; ").ifEmpty { null }

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
    if (ris.isbnIssn.orEmpty().contains("${WBL} ${ISBN_REGEX} ${WBR}".ri)) { entry[F.ISBN] = ris.isbnIssn }
    if (ris.isbnIssn.orEmpty().contains("${WBL} ${ISSN_REGEX} ${WBR}".r)) { entry[F.ISSN] = ris.isbnIssn }
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
    entry[F.DOI] = ris.doi ?: ris.typeOfWork ?: ris.notes?.find(doi)?.groupValues?.get(1)

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
