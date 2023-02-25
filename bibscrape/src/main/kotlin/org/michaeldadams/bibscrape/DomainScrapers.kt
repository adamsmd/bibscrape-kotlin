/** Scrapers for various domains (e.g., ACM, Springer, etc.). */

package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import ch.difty.kris.toRisRecords
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.Select
import org.jsoup.select.Evaluator as E
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Types as T

/** Scrapes the ACM Digital Library. */
object ScrapeAcm : DomainScraper {
  override val domains = listOf("acm.org")

  override fun scrape(driver: Driver): BibtexEntry {
    if ("Association for Computing Machinery" != driver.findElement(By.className("publisher__name")).innerHtml) {
      val urls = driver.findElements(By.className("issue-item__doi")).mapNotNull { it.getAttribute("href") }
      if (urls.isNotEmpty()) {
        return Scraper.dispatch(driver, urls.single())
      } else {
        println("WARNING: Non-ACM paper at ACM link, and could not find link to actual publisher") // TODO
      }
    }

    // // BibTeX
    driver.findElement(By.cssSelector("""a[data-title="Export Citation"]""")).click()
    val entry = driver.awaitFindElements(By.cssSelector("#exportCitation .csl-right-inline"))
      .flatMap { Bibtex.parseEntries(it.text) }
      // Avoid SIGPLAN Notices, SIGSOFT Software Eng Note, etc. by prefering non-journal over journal
      .sortedBy { it.contains(F.JOURNAL) }
      .first()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, F.JOURNAL to false)

    // // Abstract
    entry[F.ABSTRACT] = (
      // Sometimes ACM puts two .abstractInFull div tags around the abstract, and sometimes it doesn't.
      // Also, sometimes, it doesn't even have an abstract.
      driver.findElements(By.cssSelector(".article__abstract > .abstractInFull > .abstractInFull")).emptyOrSingle()
        ?: driver.findElements(By.cssSelector(".article__abstract > .abstractInFull")).emptyOrSingle()
      )?.innerHtml
      ?.remove("^ <p>No\\ abstract\\ available.</p> $".r)
      ?.ifEmpty { null }

    // // Author
    entry[F.AUTHOR] = driver.findElements(By.cssSelector(".citation .author-name"))
      .map { it.getAttribute("title") }
      .joinByAnd()

    // // Title
    entry[F.TITLE] = driver.findElement(By.cssSelector(".citation__title")).innerHtml

    // // Month
    //
    // ACM publication months are often inconsistent within the same page.
    // This is a best effort at picking the right month among these inconsistent results.
    // TODO: simplify month calculation in ACM
    entry[F.MONTH] =
      entry[F.MONTH]?.string
        ?: driver.findElements(By.cssSelector(".book-meta + .cover-date")).first().innerHtml.split("\\s+".r).first()
    entry[F.ISSUE_DATE]?.let {
      val month = it.string.split("\\s+".r).first()
      if (Bibtex.Months.stringToMonth(entry.ownerFile, month) != null) { entry[F.MONTH] = month }
    }

    // // Keywords
    entry[F.KEYWORDS] = driver.findElements(By.cssSelector(".tags-widget__content a"))
      .map { it.innerHtml }
      // ACM is inconsistent about the order in which these are returned.
      // We sort them so that we are deterministic.
      // TODO: do we still need to sort keywords?
      .sorted()
      .joinToString(";")

    // // Journal
    if (entry.entryType == T.ARTICLE) {
      entry[F.JOURNAL] = driver.findElement(By.cssSelector("meta[name=\"citation_journal_title\"]"))
        .getAttribute("content")
    }

    driver.findElements(By.className("cover-image__details"))
      .sortedBy { it.findElements(By.className("journal-meta")).isNotEmpty() }
      .first()
      .innerHtml
      .let { issn ->
        val pissn = issn.find("""<span\ class="bold">ISSN:</span><span\ class="space">(.*?)</span>""".r)
        val eissn = issn.find("""<span\ class="bold">EISSN:</span><span\ class="space">(.*?)</span>""".r)
        if (pissn != null && eissn != null) {
          entry[F.ISSN] = "${pissn.groupValues[1]} (Print) ${eissn.groupValues[1]} (Online)"
        }
      }

    // // Pages
    if (entry[F.PAGES] == null) {
      entry[F.ARTICLENO]?.let { articleno ->
        entry[F.NUMPAGES]?.let { numpages ->
          entry[F.PAGES] = "${articleno.string}:1--${articleno.string}:${numpages.string}"
        }
      }
    }

    return entry
  }
}

/** Scrapes the arXiv repository. */
object ScrapeArxiv : DomainScraper {
  override val domains = listOf("arxiv.org")

  override fun scrape(driver: Driver): BibtexEntry {
    // Ensure we are at the "abstract" page
    val urlRegex = "://arxiv.org/ ([^/]+) / (.*) $".r

    val urlMatch = urlRegex.find(driver.currentUrl)!!
    if (urlMatch.groupValues[1] != "abs") { driver.get("https://arxiv.org/abs/${urlMatch.groupValues[2]}") }

    // Id
    val id = urlRegex.find(driver.currentUrl)!!.groupValues[2]

    // Use the arXiv API to download meta-data
    driver.get("https://export.arxiv.org/api/query?id_list=${id}")
    val xml = Parser.parseBodyFragment(driver.textPlain(), "").body()
    println("<${xml}>")
    driver.navigate().back()

    // val doi = xml.select(E.Tag("arxiv:doi"))
    // if @doi and Backtrace.new.map({$_.subname}).grep({$_ eq 'scrape-arxiv'}) <= 1 {
    //   # Use publisher page if it exists
    //   dispatch('doi:' ~ @doi.head.contents».text.join(''));
    // } else {

    val xmlEntry = xml.select(E.Tag("entry")).single()

    /** Searches for a given tag and returns the text in it.
     *
     * @receiver the element in which to search for the tag
     * @param tag the name of the tag to search for
     * @return the text in the searched tag or null if the tag does not exist
     */
    fun Element.tagTextOrNull(tag: String): String? = this.select(E.Tag(tag)).emptyOrSingle()?.wholeText()

    /** Searches for a given tag and returns the text in it.
     *
     * @receiver the element in which to search for the tag
     * @param tag the name of the tag to search for
     * @return the text in the searched tag
     */
    fun Element.tagText(tag: String): String = this.tagTextOrNull(tag)!!

    // // BibTeX object
    val entry = BibtexFile().makeEntry("misc", "arxiv.${id}")

    // // Title
    entry[F.TITLE] = xmlEntry.tagText("title")

    // // Authors and Affiliation
    val authors = xmlEntry.select(E.Tag("author"))

    // author=<author><name>: One for each author. Has child element <name> containing the author name.
    entry[F.AUTHOR] = authors.map { it.tagText("name") }.joinByAnd()

    // affiliation=<author><arxiv:affiliation>: The author's affiliation included as a subelement of <author> if present
    entry[F.AFFILIATION] = authors
      .mapNotNull { it.tagTextOrNull("arxiv:affiliation") }
      .joinByAnd()
      .ifEmpty { null }

    // // How published
    entry[F.HOWPUBLISHED] = "arXiv.org"

    // // Year, month and day
    val (_, year, month, day) = xmlEntry.tagText("published").find("^ (\\d{4}) - (\\d{2}) - (\\d{2}) T".r)!!.groupValues
    // year, month, day = <published> The date that version 1 of the article was submitted.
    // <updated> The date that the retrieved version of the article was
    //           submitted. Same as <published> if the retrieved version is version 1.
    entry[F.YEAR] = year
    entry[F.MONTH] = month
    entry[F.DAY] = day

    // // DOI
    entry[F.DOI] = xmlEntry.select(E.Tag("link"))
      .flatMap { it.select(E.AttributeWithValue("title", "doi")) }
      .singleOrNull()
      ?.attributes()
      ?.get("href")

    // // Eprint
    entry[F.EPRINT] = id

    // // Archive prefix
    entry[F.ARCHIVEPREFIX] = "arXiv"

    // // Primary class
    entry[F.PRIMARYCLASS] = xmlEntry.select(E.Tag("arxiv:primary_category")).single().attributes()["term"]

    // // Abstract
    entry[F.ABSTRACT] = xmlEntry.tagText("summary")

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
    driver.currentUrl.find("^ http s? ://www.cambridge.org/core/services/aop-cambridge-core/content/view/ (S \\d+) $".r)
      ?.let { driver.get("https://doi.org/10.1017/${it.groupValues[1]}") }

    // This must be before BibTeX otherwise Cambridge sometimes hangs due to an alert box
    val meta = HtmlMeta.parse(driver)

    // // BibTeX
    driver.awaitNonNull {
      driver.findElements(By.cssSelector("[data-export-type=\"bibtex\"]")).firstOrNull()?.click()
        ?: driver.findElements(By.className("export-citation-product")).firstOrNull()?.click()?.let { null }
    }
    val entry = Bibtex.parseEntries(driver.textPlain()).single()
    driver.navigate().back()

    // // HTML Meta
    HtmlMeta.bibtex(entry, meta, F.ABSTRACT to false)

    // // Title
    // There are multiple "h1"s, but the first one should be the title
    entry[F.TITLE] = driver.awaitFindElement(By.cssSelector("#maincontent > h1")).innerHtml

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
    entry[F.ABSTRACT] = driver.findElements(By.className("abstract"))
      .emptyOrSingle()
      ?.innerHtml
      ?.remove("""\\R\ \ \ \ \ \ \\R\ \ \ \ \ \ """.r)
      ?.remove("^ <div\\ [^>]* >".r)
      ?.remove(" </div> $".r)
      ?.remove("""^ //static.cambridge.org/content/id/urn .*""".r)

    // // ISSN
    // val pissn = driver.findElement(E.name("productIssn")).getAttribute("value")
    // val eissn = driver.findElement(E.name("productEissn")).getAttribute("value")
    // entry[F.ISSN] = "${pissn} (Print) ${eissn} (Online)"
    // println("<${meta["citation_issn"]}>")
    val (eissn, pissn) = meta["citation_issn"]!! + listOf(null, null)
    entry[F.ISSN] = "${pissn} (Print) ${eissn} (Online)"

    return entry
  }
}

/** Scrapes IEEE Computer. */
object ScrapeIeeeComputer : DomainScraper {
  override val domains = listOf("computer.org")

  override fun scrape(driver: Driver): BibtexEntry {
    // // Cookie prompt since it sometimes obscures the button we want to click
    driver.findElements(By.cssSelector("[aria-label=\"cookieconsent\"]"))
      .forEach { driver.executeScript("arguments[0].remove()", it) }

    // // BibTeX
    driver.findElement(By.cssSelector(".article-action-toolbar button")).click()
    driver.findElement(By.partialLinkText("BIB TEX")).click()
    var bibtexText = driver.awaitFindElement(By.id("bibTextContent")).innerHtml.replace("<br>".r, "\n").replace("&amp;", "&")
    // TODO: $bibtex-text = Blob.new($bibtex-text.ords).decode; # Fix UTF-8 encoding
    val entry = Bibtex.parseEntries(bibtexText).single()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // Authors
    entry[F.AUTHOR] = driver.findElements(By.cssSelector("a[href^=\"/csdl/search/default?type=author&\"]"))
      .map { it.innerHtml }
      .joinByAnd()

    // // Affiliation
    entry[F.AFFILIATION] = driver.findElements(By.className("article-author-affiliations"))
      .map { it.innerHtml }
      .joinByAnd()
      .ifEmpty { null }

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
    val entry = Bibtex.parseEntries(text).single()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // HTML body text
    val body = driver.findElement(By.tagName("body")).innerHtml

    // // Author
    entry.update(F.AUTHOR) { it.replace("\\{ ([^}]+) \\}".r, "$1") }

    // // ISSN
    val issnsRegex = """
      "issn:[
        \{"format":"Print ISSN","value":" (${ISSN_REGEX}) "\},
        \{"format":"Electronic ISSN","value":" (${ISSN_REGEX}) "\}]
    """.trimIndent().r
    body.find(issnsRegex)?.let { entry[F.ISSN] = "${it.groupValues[1]} (Print) ${it.groupValues[2]} (Online)" }

    // // ISBN
    val isbnsRegex = """
      "isbn:[
        \{"format":"Print ISBN","value":" (${ISBN_REGEX}) ","isbnType":""\},
        \{"format":"CD","value":" (${ISBN_REGEX}) "\}] ","isbnType":""\}]
    """.trimIndent().r
    body.find(isbnsRegex)?.let { entry[F.ISBN] = "${it.groupValues[1]} (Print) ${it.groupValues[2]} (Online)" }

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
    entry[F.CONFERENCE_DATE] = body.find("\"conferenceDate\":\" ([^\"]+) \"".r)?.groupValues?.get(1)

    // // Abstract
    entry.update(F.ABSTRACT) { it.remove("&lt;&gt; $".r) }

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
    val entry = Ris.bibtex(BibtexFile(), driver.textPlain().split("\\R".r).toRisRecords().single())
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // Title
    entry[F.TITLE] = driver.findElement(By.cssSelector("[data-p13n-title]"))
      .getDomAttribute("data-p13n-title")
      .remove("\\R".r)

    // // Abstract
    entry[F.ABSTRACT] = driver.findElement(By.cssSelector("[data-abstract]"))
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
    driver.findElements(By.className("reveal-overlay"))
      .forEach { driver.executeScript("arguments[0].removeAttribute(\"style\")", it) }

    // // BibTeX
    // Note that on-campus is different than off-campus
    // await({ $web-driver.find_elements_by_css_selector( '[data-qa="cite-this-item"]' )
    //         || $web-driver.find_elements_by_class_name( 'cite-this-item' ) }).head.click;
    // await({ $web-driver.find_element_by_css_selector( '[data-sc="text link: citation text"]' ) }).click;
    val entry = Bibtex.parseEntries(driver.textPlain()).single()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // Title
    // Note that on-campus is different than off-campus
    entry[F.TITLE] = (
      driver.findElements(By.className("item-title")).emptyOrSingle()
        ?: driver.findElement(By.className("title-font"))
      ).innerHtml

    // // DOI
    entry[F.DOI] = driver.findElement(By.cssSelector("[data-doi]")).getDomAttribute("data-doi")

    // // ISSN
    entry.update(F.ISSN) { it.replace("^ ([0-9Xx]+) ,\\ ([0-9Xx]+) $".r, "$1 (Print) $2 (Online)") }

    // // Month
    entry[F.MONTH] = (
      driver.findElements(By.cssSelector(".turn-away-content__article-summary-journal a")) +
        driver.findElements(By.className("src"))
      )
      .first()
      .innerHtml
      .find("\\( ([A-Za-z]+) ".r)
      ?.groupValues
      ?.get(1)

    // // Publisher
    // Note that on-campus is different than off-campus
    entry[F.PUBLISHER] =
      driver.findElements(By.className("turn-away-content__article-summary-journal")).emptyOrSingle()?.let {
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
    // TODO: is this still the case
    println("WARNING: Oxford imposes rate limiting.  BibScrape might hang if you try multiple papers in a row.")

    // // BibTeX
    driver.awaitFindElement(By.className("js-cite-button")).click()
    val selectElement = Select(driver.awaitFindElement(By.id("selectFormat")))
    driver.awaitNonNull {
      // TODO: rewrite
      selectElement.selectByVisibleText(".bibtex (BibTex)")
      val button = driver.findElement(By.className("citation-download-link"))
      if (!button.getAttribute("class").contains("\\b disabled \\b".r)) {
        button.click()
      } else {
        selectElement.selectByVisibleText(".enw (EndNote)")
        null
      }
    }
    // driver.findElement(By.className("citation-download-link")).click()
    // my #`(Inline::Python::PythonObject:D) $select = $web-driver.select($select-element);
    // await({
    //   $select.select_by_visible_text( '.bibtex (BibTex)' );
    //   my #`(Inline::Python::PythonObject:D) $button =
    //     $web-driver.find_element_by_class_name( 'citation-download-link' );
    //   # Make sure the drop-down was populated
    //   $button.get_attribute( 'class' ) !~~ / « 'disabled' » /
    //     and $button }
    // ).click;
    val entry = Bibtex.parseEntries(driver.textPlain()).single()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, F.AUTHOR to true, F.MONTH to true, F.YEAR to true)

    // // Title
    entry[F.TITLE] = driver.findElement(By.className("article-title-main")).innerHtml

    // // Abstract
    entry[F.ABSTRACT] = driver.findElement(By.className("abstract")).innerHtml

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
    driver.awaitNonNull {
      // TODO: why are these in the same await? This probably breaks the "driver.timeout" model
      driver.findElement(By.id("export-citation")).click()
      driver.findElement(By.cssSelector("button[aria-label=\"bibtex\"]")).click()
    }
    val entry = Bibtex.parseEntries(driver.textPlain()).single()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, "number" to true)

    // // Title
    entry[F.TITLE] = driver.findElement(By.className("title-text")).innerHtml

    // // Keywords
    entry[F.KEYWORDS] = driver.findElements(By.cssSelector(".keywords-section > .keyword > span"))
      .map { it.innerHtml }
      .joinToString("; ")

    // // Abstract
    entry[F.ABSTRACT] = driver.findElement(By.cssSelector("#abstracts > .abstract.author > div")).innerHtml

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

    entry.update(F.EDITOR) { it.replace("\\ * \\R".r, " ") }

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
    val pisbn = driver.findElements(By.id("print-isbn")).emptyOrSingle()?.innerHtml
    val eisbn = driver.findElements(By.id("electronic-isbn")).emptyOrSingle()?.innerHtml
    if (pisbn != null && eisbn != null) { entry[F.ISBN] = "${pisbn} (Print) ${eisbn} (Online)" }

    // // ISSN
    driver.findElement(By.tagName("head"))
      .innerHtml
      .find("""\{ "eissn" : " (${ISSN_REGEX}) " , "pissn" : " (${ISSN_REGEX}) " \\}""".r)
      ?.let { entry[F.ISSN] = "${it.groupValues[2]} (Print) ${it.groupValues[1]} (Online)" }

    // // Series, Volume and ISSN
    //
    // Ugh, Springer doesn't have a reliable way to get the series, volume,
    // or ISSN.  Fortunately, this only happens for LNCS, so we hard code
    // it.
    driver.findElement(By.tagName("body"))
      .innerHtml
      .find("\\(LNCS,\\ volume\\ (\\d*) \\)".r)
      ?.let {
        entry[F.VOLUME] = it.groupValues[1]
        entry[F.SERIES] = "Lecture Notes in Computer Science"
      }

    // // Keywords
    entry[F.KEYWORDS] = driver.findElements(By.className("c-article-subject-list__subject"))
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
    entry.update("publisher") { if (it == "Springer, ${entry[F.ADDRESS]?.string}") "Springer" else it }

    return entry
  }
}
