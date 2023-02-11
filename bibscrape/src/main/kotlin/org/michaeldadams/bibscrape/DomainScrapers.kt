package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import ch.difty.kris.toRisRecords
import org.openqa.selenium.By
import org.w3c.dom.Element
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.text.toRegex
import org.michaeldadams.bibscrape.Bibtex.Fields as F

/** Scrapes the ACM Digital Library. */
object ScrapeAcm : DomainScraper {
  override val domains = listOf("acm.org")

  override fun scrape(driver: Driver): BibtexEntry {
    // driver.manage().timeouts().implicitlyWait(Duration.ofMillis((1000 * 30.0).roundToLong()))
    // TODO: prevent loops on ACM
    if ("Association for Computing Machinery" !=
      driver.findElement(By.className("publisher__name")).innerHtml
    ) {
      val urls = driver
        .findElements(By.className("issue-item__doi"))
        .mapNotNull { it.getAttribute("href") }
      // TODO: filter to non-acm links
      if (urls.size > 0) {
        return Scraper.dispatch(driver, URI(urls.first()))
      } else {
        TODO("WARNING: Non-ACM paper at ACM link, and could not find link to actual publisher")
      }
    }

    // // BibTeX
    driver.findElement(By.cssSelector("""a[data-title="Export Citation"]""")).click()
    val entries: List<BibtexEntry> =
      driver.awaitFindElements(By.cssSelector("#exportCitation .csl-right-inline"))
        .flatMap { Bibtex.parseEntries(it.text) }
        // Avoid SIGPLAN Notices, SIGSOFT Software Eng Note, etc. by prefering
        // non-journal over journal
        .sortedBy { it.contains(F.JOURNAL) }
    val entry = entries.first()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, F.JOURNAL to false)

    // // Abstract
    val abstract = driver
      .findElements(By.cssSelector(".abstractSection.abstractInFull"))
      .lastOrNull()
      ?.innerHtml
    if (abstract != null && abstract != "<p>No abstract available.</p>") {
      entry[F.ABSTRACT] = abstract
    }

    // // Author
    entry[F.AUTHOR] = driver
      .findElements(By.cssSelector(".citation .author-name"))
      .map { it.getAttribute("title") }
      .joinByAnd()

    // // Title
    entry[F.TITLE] = driver.findElement(By.cssSelector(".citation__title")).innerHtml

    // // Month
    //
    // ACM publication months are often inconsistent within the same page.
    // This is a best effort at picking the right month among these inconsistent results.
    if (entry[F.MONTH] == null) {
      entry[F.MONTH] = driver
        .findElement(By.cssSelector(".book-meta + .cover-date"))
        .innerHtml
        .split("\\s+".r)
        .first()
    }
    entry.ifField(F.ISSUE_DATE) {
      val month = it.string.split("\\s+".r).first()
      if (Bibtex.Months.stringToMonth(entry.ownerFile, month) != null) {
        entry[F.MONTH] = month
      }
    }

    // // Keywords
    val keywords = driver
      .findElements(By.cssSelector(".tags-widget__content a"))
      .map { it.innerHtml }
      // ACM is inconsistent about the order in which these are returned.
      // We sort them so that we are deterministic.
      .sorted()
    if (keywords.size > 0) {
      entry[F.KEYWORDS] = keywords.joinToString("; ")
    }

    // // Journal
    if (entry.entryType == "article") {
      val journal = driver
        .findElements(By.cssSelector("meta[name=\"citation_journal_title\"]"))
        .map { it.getAttribute("content") }
      if (journal.size > 0) {
        entry[F.JOURNAL] = journal.first()
      }
    }

    val issns =
      driver
        .findElements(By.className("cover-image__details"))
        .sortedBy { it.findElements(By.className("journal-meta")).size > 0 }
    if (issns.size > 0) {
      val issn = issns.first().innerHtml
      val pissn =
        """<span class="bold">ISSN:</span><span class="space">(.*?)</span>"""
          .r
          .find(issn)
      val eissn =
        """<span class="bold">EISSN:</span><span class="space">(.*?)</span>"""
          .r
          .find(issn)
      if (pissn != null && eissn != null) {
        entry[F.ISSN] = "${pissn.groupValues[1]} (Print) ${eissn.groupValues[1]} (Online)"
      }
    }

    // // Pages
    if (entry.getFieldValue("articleno") != null &&
      entry.getFieldValue("numpages") != null &&
      entry.getFieldValue("pages") == null
    ) {
      val articleno = entry.getFieldValue("articleno").toString()
      val numpages = entry.getFieldValue("numpages").toString()
      entry[F.PAGES] = "${articleno}:1--${articleno}:${numpages}"
    }

    return entry
  }
}

/** Scrapes the arXiv repository. */
object ScrapeArxiv : DomainScraper {
  override val domains = listOf("arxiv.org")

  override fun scrape(driver: Driver): BibtexEntry {
    // format_bibtex_arxiv in
    // https://github.com/mattbierbaum/arxiv-bib-overlay/blob/master/src/ui/CiteModal.tsx
    // Ensure we are at the "abstract" page
    val urlRegex = "://arxiv.org/ ([^/]+) / (.*) $".r

    val urlMatch = urlRegex.find(driver.currentUrl)!!
    if (urlMatch.groupValues[1] != "abs") {
      driver.get("https://arxiv.org/abs/${urlMatch.groupValues[2]}")
    }

    // Id
    val id = urlRegex.find(driver.currentUrl)!!.groupValues[1]

    // Use the arXiv API to download meta-data
    // #$web-driver.get("https://export.arxiv.org/api/query?id_list=$id"); # Causes a timeout
    // #$web-driver.execute_script(
    // #   'window.location.href = arguments[0]', "https://export.arxiv.org/api/query?id_list=$id");
    driver.executeScript("window.open(arguments[0], \"_self\")", "https://export.arxiv.org/api/query?id_list=${id}")
    val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(driver.pageSource).documentElement
    driver.navigate().back()

    fun Element.getElementListByTagName(tag: String): List<Element> =
      this.getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
      }

    val doi = xml.getElementListByTagName("arxiv:doi")
    // if @doi and Backtrace.new.map({$_.subname}).grep({$_ eq 'scrape-arxiv'}) <= 1 {
    //   # Use publisher page if it exists
    //   dispatch('doi:' ~ @doi.head.contents».text.join(''));
    // } else {

    val xmlEntry: Element = xml.getElementListByTagName("entry")[0]

    fun Element.text(tag: String): String =
      this.getElementListByTagName(tag).let { it.getOrNull(0)?.textContent ?: "" }
    // sub text(XML::Element:D $element, Str:D $str --> Str:D) {
    //   my XML::Element:D @elements = $element.getElementsByTagName($str);
    //   if @elements {
    //     @elements.head.contents».text.join('');
    //   } else {
    //     '';
    //   }
    // }

    // // BibTeX object
    val entry = BibtexFile().makeEntry("misc", "arxiv.${id}")

    // // Title
    entry[F.TITLE] = xmlEntry.text("title")

    val authors = xmlEntry.getElementListByTagName("author")

    // // Author
    // author=<author><name>: One for each author. Has child element <name> containing the author name.
    entry[F.AUTHOR] = authors.map { it.text("name") }.joinByAnd()

    // // Affiliation
    // affiliation=<author><arxiv:affiliation>:
    //   The author's affiliation included as a subelement of <author> if present.
    authors.map { it.text("arxiv:affiliation") }.filter { it != "" }.joinByAnd().let {
      if (it != "") { entry[F.AFFILIATION] = it }
    }

    // // How published
    entry[F.HOWPUBLISHED] = "arXiv.org"

    // // Year, month and day
    val published = xmlEntry.text("published")
    // $published ~~ /^ (\d ** 4) '-' (\d ** 2) '-' (\d ** 2) 'T'/;
    // my (Str:D $year, Str:D $month, Str:D $day) = ($0.Str, $1.Str, $2.Str);
    //   # year, month, day = <published> 	The date that version 1 of the article was submitted.
    //   # <updated> 	The date that the retrieved version of the article was
    //           submitted. Same as <published> if the retrieved version is version 1.
    // $entry.fields<year> = BibScrape::BibTeX::Value.new($year);
    // $entry.fields<month> = BibScrape::BibTeX::Value.new($month);
    // $entry.fields<day> = BibScrape::BibTeX::Value.new($day);

    // my Str:D $doi = $xml-entry.elements(:TAG<link>, :title<doi>).map({$_.attribs<href>}).join(';');
    // $entry.fields<doi> = BibScrape::BibTeX::Value.new($doi)
    //   if $doi ne '';

    // // Eprint
    entry[F.EPRINT] = id

    // // Archive prefix
    entry[F.ARCHIVEPREFIX] = "arXiv"

    // // Primary class
    // my Str:D $primaryClass = $xml-entry.getElementsByTagName('arxiv:primary_category').head.attribs<term>;
    // $entry.fields<primaryclass> = BibScrape::BibTeX::Value.new($primaryClass);

    // // Abstract
    entry[F.ABSTRACT] = xmlEntry.text("summary")

    // The following XML elements are ignored
    // <link>              Can be up to 3 given url's associated with this article.
    // <category>          The arXiv or ACM or MSC category for an article if present.
    // <arxiv:comment>     The authors comment if present.
    // <arxiv:journal_ref> A journal reference if present.
    // <arxiv:doi>         A url for the resolved DOI to an external resource if present.

    return entry
  }
}

/** Scrapes Cambridge University Press. */
object ScrapeCambridge : DomainScraper {
  override val domains = listOf("cambridge.org")

  override fun scrape(driver: Driver): BibtexEntry {
    val match = "^https?://www.cambridge.org/core/services/aop-cambridge-core/content/view/('S'\\d+)\$"
      .toRegex()
      .matchEntire(driver.currentUrl)
    if (match != null) {
      driver.get("https://doi.org/10.1017/${match.groupValues[1]}")
    }

    // This must be before BibTeX otherwise Cambridge sometimes hangs due to an alert box
    val meta = HtmlMeta.parse(driver)

    // // BibTeX
    driver.awaitFindElement(By.className("export-citation-product")).click()
    driver.awaitFindElement(By.cssSelector("[data-export-type=\"bibtex\"]")).click()
    val entry = Bibtex.parseEntries(driver.pageSource).first()
    driver.navigate().back()

    // // HTML Meta
    HtmlMeta.bibtex(entry, meta, F.ABSTRACT to false)

    // // Title
    entry[F.TITLE] = driver.awaitFindElement(By.className("article-title")).innerHtml

    // // Abstract
    // my #`(Inline::Python::PythonObject:D) @abstract = $web-driver.find_elements_by_class_name( 'abstract' );
    // if @abstract {
    //   my Str:D $abstract = @abstract.head.get_property( 'innerHTML' );
    //   #my $abstract = meta( 'citation_abstract' );
    //   $abstract ~~ s:g/ "\n      \n      " //;
    //   $abstract ~~ s/^ '<div ' <-[>]>* '>'//;
    //   $abstract ~~ s/ '</div>' $//;
    //   $entry.fields<abstract> = BibScrape::BibTeX::Value.new($abstract)
    //     unless $abstract ~~ /^ '//static.cambridge.org/content/id/urn' /;
    // }

    // // ISSN
    val pissn = driver.findElement(By.name("productIssn")).getAttribute("value")
    val eissn = driver.findElement(By.name("productEissn")).getAttribute("value")
    entry[F.ISSN] = "${pissn} (Print) ${eissn} (Online)"

    return entry
  }
}

/** Scrapes IEEE Computer. */
object ScrapeIeeeComputer : DomainScraper {
  override val domains = listOf("computer.org")

  override fun scrape(driver: Driver): BibtexEntry {
    // // BibTeX
    driver.awaitFindElement(By.cssSelector(".article-action-toolbar button")).click()
    val bibtexLink = driver.awaitFindElement(By.linkText("BibTex"))
    driver.executeScript("arguments[0].removeAttribute(\"target\")", bibtexLink)
    driver.findElement(By.linkText("BibTex")).click()
    var bibtexText = driver.awaitFindElement(By.tagName("pre")).innerHtml
    // $bibtex-text ~~ s/ "\{," /\{key,/;
    // $bibtex-text = Blob.new($bibtex-text.ords).decode; # Fix UTF-8 encoding
    val entry = Bibtex.parseEntries(bibtexText).first()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // Authors
    entry[F.AUTHOR] = driver
      .findElements(By.cssSelector("a[href^=\"https://www.computer.org/csdl/search/default?type=author&\"]"))
      .map { it.innerHtml }
      .joinByAnd()

    // // Affiliation
    val affiliations = driver
      .findElements(By.className("article-author-affiliations"))
      .map { it.innerHtml }
    if (affiliations.isNotEmpty()) {
      entry[F.AFFILIATION] = affiliations.joinByAnd()
    }

    // // Keywords
    // TODO(?): entry.update(F.KEYWORDS) { it.replace("\\s* ; \\s*".r, "; ") } // TODO: move to Fix.kt?
    entry.update(F.KEYWORDS) { it.replace("; \\s*".r, "; ") } // TODO: move to Fix.kt?

    return entry
  }
}

/** Scrapes IEEE Explore. */
object ScrapeIeeeExplore : DomainScraper {
  override val domains = listOf("ieeexplore.ieee.org")

  override fun scrape(driver: Driver): BibtexEntry {
    // // BibTeX
    driver.awaitFindElement(By.tagName("xpl-cite-this-modal")).click()
    driver.awaitFindElement(By.linkText("BibTeX")).click()
    driver.awaitFindElement(By.cssSelector(".enable-abstract input")).click()
    val text = driver.awaitFindElement(By.className("ris-text")).innerHtml
    val entry = Bibtex.parseEntries(text).first()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // HTML body text
    val body = driver.findElement(By.tagName("body")).innerHtml

    // // Keywords
    entry.update(F.KEYWORDS) { it.replace("; \\s*".r, "; ") } // TODO: move to Fix.kt?

    // // Author
    entry.update(F.AUTHOR) { it.replace("\\{ ([^}]+) \\}".r, "$1") }

    // // ISSN
    val issnsRegex = """
      "issn:[
        \{"format":"Print ISSN","value":" (${ISSN_REGEX}) "\},
        \{"format":"Electronic ISSN","value":" (${ISSN_REGEX}) "\}]
    """.trimIndent().r
    body.find(issnsRegex)?.let {
      entry[F.ISSN] = "${it.groupValues[1]} (Print) ${it.groupValues[2]} (Online)"
    }

    // // ISBN
    val isbnsRegex = """
      "isbn:[
        \{"format":"Print ISBN","value":" (${ISBN_REGEX}) ","isbnType":""\},
        \{"format":"CD","value":" (${ISBN_REGEX}) "\}] ","isbnType":""\}]
    """.trimIndent().r
    body.find(isbnsRegex)?.let {
      entry[F.ISBN] = "${it.groupValues[1]} (Print) ${it.groupValues[2]} (Online)"
    }

    // // Publisher
    entry[F.PUBLISHER] =
      driver.findElement(By.cssSelector(".publisher-info-container > span > span > span + span")).innerHtml

    // // Affiliation
    // my Str:D $affiliation =
    //   ($body ~~ m:g/ '"affiliation":["' (<-["]>+) '"]' /)
    //   .map(sub (Match:D $match --> Str:D) { $match[0].Str }).join( ' and ' );
    // $entry.fields<affiliation> = BibScrape::BibTeX::Value.new($affiliation)
    //   if $affiliation;

    // // Location
    // my Str:D $location = (($body ~~ / '"confLoc":"' (<-["]>+) '"' /)[0] // '').Str;
    // if $location {
    //   $location ~~ s/ ',' \s+ $//;
    //   $location ~~ s/ ', USA, USA' $/, USA/;
    //   $entry.fields<location> = BibScrape::BibTeX::Value.new($location.Str);
    // }

    // // Conference date
    body.find("\"conferenceDate\":\" ([^\"]+) \"".r)?.let {
      entry[F.CONFERENCE_DATE] = it.groupValues[1]
    }

    // // Abstract
    entry.update(F.ABSTRACT) { it.replace("&lt;&gt; $".r, "") }

    return entry
  }
}

/** Scrapes IOS Press. */
object ScrapeIosPress : DomainScraper {
  override val domains = listOf("iospress.com")

  override fun scrape(driver: Driver): BibtexEntry {
    // // RIS
    driver.awaitFindElement(By.className("p13n-cite")).click()
    driver.awaitFindElement(By.className("btn-clear")).click()
    val ris = driver.pageSource.split("\\R".r).toRisRecords().first()
    driver.navigate().back()

    // my BibScrape::Ris::Ris:D $ris = ris-parse($web-driver.read-downloads());
    val entry = Bibtex.parse(BibtexFile(), ris)
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-of-ris($ris);

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // Title
    entry[F.TITLE] = driver
      .findElement(By.cssSelector("[data-p13n-title]"))
      .getDomAttribute("data-p13n-title")
      .replace("\\\\n".r, "")

    // // Abstract
    entry[F.ABSTRACT] = driver
      .findElement(By.cssSelector("[data-abstract]"))
      .getDomAttribute("data-abstract")
      .replace("([.!?]) \\ \\ ".r, "$0\n\n") // Insert missing paragraphs.  This is a heuristic solution.

    // // ISSN
    // if $ris.fields<SN>:exists {
    //   my Str:D $eissn = $ris.fields<SN>.head;
    //   my Str:D $pissn = $web-driver.meta( 'citation_issn' ).head;
    //   $entry.fields<issn> = BibScrape::BibTeX::Value.new("$pissn (Print) $eissn (Online)");
    // }

    return entry
  }
}

/** Scrape JStor. */
object ScrapeJstor : DomainScraper {
  override val domains = listOf("jstor.org")

  override fun scrape(driver: Driver): BibtexEntry {
    // // Remove overlay
    val overlays = driver.findElements(By.className("reveal-overlay"))
    overlays.forEach { driver.executeScript("arguments[0].removeAttribute(\"style\")", it) }

    // // BibTeX
    // Note that on-campus is different than off-campus
    // await({ $web-driver.find_elements_by_css_selector( '[data-qa="cite-this-item"]' )
    //         || $web-driver.find_elements_by_class_name( 'cite-this-item' ) }).head.click;
    // await({ $web-driver.find_element_by_css_selector( '[data-sc="text link: citation text"]' ) }).click;
    val entry = Bibtex.parseEntries(driver.pageSource).first()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // Title
    // Note that on-campus is different than off-campus
    entry[F.TITLE] = (
      driver.findElement(By.className("item-title"))
        ?: driver.findElement(By.className("title-font"))
      )!!.innerHtml

    // // DOI
    entry[F.DOI] = driver.findElement(By.cssSelector("[data-doi]")).getDomAttribute("data-doi")

    // // ISSN
    entry.update(F.ISSN) { it.replace("^ ([0-9Xx]+) ,\\ ([0-9Xx]+) $".r, "$1 (Print) $2 (Online)") }

    // // Month
    val month = (
      driver.findElements(By.cssSelector(".turn-away-content__article-summary-journal a")) +
        driver.findElements(By.className("src"))
      ).first().innerHtml
    month.find("\\( ([A-Za-z]+) ".r)?.let {
      entry[F.MONTH] = it.groupValues[1]
    }

    // // Publisher
    // Note that on-campus is different than off-campus
    entry[F.PUBLISHER] =
      driver.findElement(By.className("turn-away-content__article-summary-journal"))?.let {
        it.innerHtml.find("Published\\ By:\\ ([^<]*)".r)!!.groupValues[1]
      } ?: driver.findElement(By.className("publisher-link")).innerHtml
    // my Str:D $publisher =
    //   do if $web-driver.find_elements_by_class_name( 'turn-away-content__article-summary-journal' ) {
    //     my Str:D $text =
    //       $web-driver.find_element_by_class_name(
    //          'turn-away-content__article-summary-journal' ).get_property( 'innerHTML' );
    //     $text ~~ / 'Published By: ' (<-[<]>*) /;
    //     $0.Str
    //   } else {
    //     $web-driver.find_element_by_class_name( 'publisher-link' ).get_property( 'innerHTML' )
    //   };
    // $entry.fields<publisher> = BibScrape::BibTeX::Value.new($publisher);

    return entry
  }
}

/** Scrape Oxford University Press. */
object ScrapeOxford : DomainScraper {
  override val domains = listOf("oup.com")

  override fun scrape(driver: Driver): BibtexEntry {
    println("WARNING: Oxford imposes rate limiting.  BibScrape might hang if you try multiple papers in a row.")

    // // BibTeX
    driver.awaitFindElement(By.className("js-cite-button")).click()
    val selectElement = driver.awaitFindElement(By.id("selectFormat"))
    // my #`(Inline::Python::PythonObject:D) $select = $web-driver.select($select-element);
    // await({
    //   $select.select_by_visible_text( '.bibtex (BibTex)' );
    //   my #`(Inline::Python::PythonObject:D) $button =
    //     $web-driver.find_element_by_class_name( 'citation-download-link' );
    //   # Make sure the drop-down was populated
    //   $button.get_attribute( 'class' ) !~~ / « 'disabled' » /
    //     and $button }
    // ).click;
    val entry = Bibtex.parseEntries(driver.pageSource).first()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, F.MONTH to true, F.YEAR to true)

    // // Title
    val title = driver.findElement(By.className("article-title-main")).innerHtml

    // // Abstract
    val abstract = driver.findElement(By.className("abstract")).innerHtml

    // // ISSN
    val issn = driver.findElement(By.tagName("body")).innerHtml
    val pissn = issn.find("Print\\ ISSN\\ (${ISSN_REGEX})".r)!!.groupValues[1]
    val eissn = issn.find("Online\\ ISSN\\ (${ISSN_REGEX})".r)!!.groupValues[1]
    entry[F.ISSN] = "${pissn} (Print) ${eissn} (Online)"

    // // Publisher
    entry.update(F.PUBLISHER) { it.replace("^ Oxford\\ Academic $".r, "Oxford University Press") }

    return entry
  }
}

/** Scrape Science Direct. */
object ScrapeScienceDirect : DomainScraper {
  override val domains = listOf("sciencedirect.com", "elsevier.com")

  override fun scrape(driver: Driver): BibtexEntry {
    // // BibTeX
    driver.await {
      // TODO: why are these in the same await? This probably breaks the "driver.timeout" model
      it.findElement(By.id("export-citation")).click()
      it.findElement(By.cssSelector("button[aria-label=\"bibtex\"]")).click()
    }
    val entry = Bibtex.parseEntries(driver.pageSource).first()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, "number" to true)

    // // Title
    entry[F.TITLE] = driver.findElement(By.className("title-text")).innerHtml

    // // Keywords
    entry[F.KEYWORDS] = driver
      .findElements(By.cssSelector(".keywords-section > .keyword > span"))
      .map { it.innerHtml }
      .joinToString("; ")

    // // Abstract
    driver
      .findElements(By.cssSelector(".abstract > div"))
      .map { it.innerHtml }
      .firstOrNull()
      ?.let { entry[F.ABSTRACT] = it }

    // // Series
    entry.moveField(F.NOTE, F.SERIES)

    return entry
  }
}

/** Scrape Springer. */
object ScrapeSpringer : DomainScraper {
  override val domains = listOf("link.springer.com")

  override fun scrape(driver: Driver): BibtexEntry {
    // // BibTeX
    val entry = BibtexFile().makeEntry("", "")
    // Use the BibTeX download if it is available
    // if $web-driver.find_elements_by_id( 'button-Dropdown-citations-dropdown' ) {
    //   await({
    //     # Close the cookie/GDPR overlay
    //     try { $web-driver.find_element_by_id( 'onetrust-accept-btn-handler' ).click; }
    //     # Scroll to the link.  (Otherwise WebDriver reports an error.)
    //     try { $web-driver.find_element_by_id( 'button-Dropdown-citations-dropdown' ).click; }
    //     # Click the actual link for BibTeX
    //     $web-driver.find_element_by_css_selector( '#Dropdown-citations-dropdown a[data-track-label="BIB"]' ).click;
    //     True });
    //   $entry = bibtex-parse($web-driver.read-downloads).items.head;
    // }

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, "publisher" to true)

    entry.update(F.EDITOR) { it.replace("\\ *\\\n".r, " ") }

    // // Author
    // my Any:D @authors =
    //   $web-driver.find_elements_by_css_selector(
    //     '.c-article-authors-search__title,
    //       .c-article-author-institutional-author__name,
    //       .authors-affiliations__name')
    //   .map({
    //     $_.get_attribute( 'class' ) ~~ / « 'c-article-author-institutional-author__name' » /
    //       ?? '{' ~ $_.get_property( 'innerHTML' ) ~ '}'
    //       !! $_.get_property( 'innerHTML' ) });
    // @authors.map({ s:g/ '&nbsp;' / /; });
    // $entry.fields<author> = BibScrape::BibTeX::Value.new(@authors.join( ' and ' ));

    // // ISBN
    val pisbn = driver.findElement(By.id("print-isbn"))?.innerHtml
    val eisbn = driver.findElement(By.id("electronic-isbn"))?.innerHtml
    if (pisbn != null && eisbn != null) { entry[F.ISBN] = "${pisbn} (Print) ${eisbn} (Online)" }

    // // ISSN
    driver.findElement(By.tagName("head"))?.innerHtml
      ?.find("""\{ "eissn" : " (${ISSN_REGEX}) " , "pissn" : " (${ISSN_REGEX}) " \\}""".r)
      ?.let {
        entry[F.ISSN] = "${it.groupValues[2]} (Print) ${it.groupValues[1]} (Online)"
      }

    // // Series, Volume and ISSN
    //
    // Ugh, Springer doesn't have a reliable way to get the series, volume,
    // or ISSN.  Fortunately, this only happens for LNCS, so we hard code
    // it.
    driver.findElement(By.tagName("body")).innerHtml.find("\\(LNCS,\\ volume\\ (\\d*) \\)".r)?.let {
      entry[F.VOLUME] = it.groupValues[1]
      entry[F.SERIES] = "Lecture Notes in Computer Science"
    }

    // // Keywords
    entry[F.KEYWORDS] = driver
      .findElements(By.className("c-article-subject-list__subject"))
      .map { it.innerHtml }
      .joinToString("; ")

    // // Abstract
    // my #`(Inline::Python::PythonObject:D) @abstract =
    //   ($web-driver.find_elements_by_class_name( 'Abstract' ),
    //     $web-driver.find_elements_by_id( 'Abs1-content' )).flat;
    // if @abstract {
    //   my Str:D $abstract = @abstract.head.get_property( 'innerHTML' );
    //   $abstract ~~ s/^ '<h' <[23]> .*? '>Abstract</h' <[23]> '>' //;
    //   $entry.fields<abstract> = BibScrape::BibTeX::Value.new($abstract);
    // }

    // // Publisher
    // The publisher field should not include the address
    entry.update("publisher") {
      val address = entry[F.ADDRESS]?.string ?: ""
      if (it == "Springer, ${address}") "Springer" else it
    }

    return entry
  }
}
