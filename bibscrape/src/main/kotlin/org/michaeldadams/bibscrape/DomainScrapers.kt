package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import org.openqa.selenium.By
import java.net.URI
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
      .last()
      .innerHtml
    if (abstract != "<p>No abstract available.</p>") {
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
    entry.ifField(F.MONTH) {
      entry[F.MONTH] = driver
        .findElement(By.cssSelector(".book-meta + .cover-date"))
        .innerHtml
        .split("\\s+")
        .first()
    }
    entry.ifField(F.ISSUE_DATE) {
      val month = it.string.split("\\s+").first()
      if (Bibtex.str2month(entry.ownerFile, month) != null) {
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
    val xmlString = driver.pageSource
    driver.navigate().back()
    // my XML::Document:D $xml = from-xml($xml-string);

    // my XML::Element:D @doi = $xml.getElementsByTagName('arxiv:doi');
    // if @doi and Backtrace.new.map({$_.subname}).grep({$_ eq 'scrape-arxiv'}) <= 1 {
    //   # Use publisher page if it exists
    //   dispatch('doi:' ~ @doi.head.contents».text.join(''));
    // } else {
    //   my XML::Element:D $xml-entry = $xml.getElementsByTagName('entry').head;

    // sub text(XML::Element:D $element, Str:D $str --> Str:D) {
    //   my XML::Element:D @elements = $element.getElementsByTagName($str);
    //   if @elements {
    //     @elements.head.contents».text.join('');
    //   } else {
    //     '';
    //   }
    // }

    // // BibTeX object
    val file = BibtexFile()
    val entry = file.makeEntry("misc", "arxiv.${id}")

    // // Title
    // my Str:D $title = text($xml-entry, 'title');
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    // // Author
    // my XML::Element:D @authors = $xml-entry.getElementsByTagName('author');
    // my Str:D $author = @authors.map({text($_, 'name')}).join( ' and ' );
    //   # author=<author><name> 	One for each author. Has child element <name> containing the author name.
    // $entry.fields<author> = BibScrape::BibTeX::Value.new($author);

    // // Affiliation
    // my Str:D $affiliation = @authors.map({text($_, 'arxiv:affiliation')}).grep({$_ ne ''}).join( ' and ' );
    //   # affiliation=<author><arxiv:affiliation>
    //   // The author's affiliation included as a subelement of <author> if present.
    // $entry.fields<affiliation> = BibScrape::BibTeX::Value.new($affiliation)
    //   if $affiliation ne '';

    // // How published
    // $entry.fields<howpublished> = BibScrape::BibTeX::Value.new('arXiv.org');

    // // Year, month and day
    // my Str:D $published = text($xml-entry, 'published');
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
    // my Str:D $eprint = $id;
    // $entry.fields<eprint> = BibScrape::BibTeX::Value.new($eprint);

    // // Archive prefix
    // $entry.fields<archiveprefix> = BibScrape::BibTeX::Value.new('arXiv');

    // // Primary class
    // my Str:D $primaryClass = $xml-entry.getElementsByTagName('arxiv:primary_category').head.attribs<term>;
    // $entry.fields<primaryclass> = BibScrape::BibTeX::Value.new($primaryClass);

    // // Abstract
    // my Str:D $abstract = text($xml-entry, 'summary');
    // $entry.fields<abstract> = BibScrape::BibTeX::Value.new($abstract);

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
    val m = "^https?://www.cambridge.org/core/services/aop-cambridge-core/content/view/('S'\\d+)\$"
      .toRegex()
      .matchEntire(driver.currentUrl)
    if (m != null) {
      driver.get("https://doi.org/10.1017/${m.groupValues[1]}")
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
    if (!affiliations.isEmpty()) {
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
    // if $body ~~ /
    //     '"issn":[{"format":"Print ISSN","value":"' (\d\d\d\d '-' \d\d\d<[0..9Xx]>)
    //     '"},{"format":"Electronic ISSN","value":"' (\d\d\d\d '-' \d\d\d<[0..9Xx]>) '"}]' / {
    //   $entry.fields<issn> = BibScrape::BibTeX::Value.new("$0 (Print) $1 (Online)");
    // }

    // // ISBN
    // if $body ~~ /
    //     '"isbn":[{"format":"Print ISBN","value":"' (<[-0..9Xx]>+)
    //     '","isbnType":""},{"format":"CD","value":"' (<[-0..9Xx]>+) '","isbnType":""}]' / {
    //   $entry.fields<isbn> = BibScrape::BibTeX::Value.new("$0 (Print) $1 (Online)");
    // }

    // // Publisher
    entry[F.PUBLISHER] =
      driver.findElement(By.cssSelector(".publisher-info-container > span > span > span + span")).innerHtml
    // my Str:D $publisher =
    //   $web-driver
    //   .find_element_by_css_selector( '.publisher-info-container > span > span > span + span' )
    //   .get_property( 'innerHTML' );
    // $entry.fields<publisher> = BibScrape::BibTeX::Value.new($publisher);

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
    // $body ~~ / '"conferenceDate":"' (<-["]>+) '"' /;
    // $entry.fields<conference_date> = BibScrape::BibTeX::Value.new($0.Str) if $0;

    // // Abstract
    entry.update(F.ABSTRACT) { it.replace("&lt;&gt; $".r, "") }
    // update($entry, 'abstract', { s/ '&lt;&gt;' $// });

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
    // my BibScrape::Ris::Ris:D $ris = ris-parse($web-driver.read-downloads());
    val entry: BibtexEntry = TODO()
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-of-ris($ris);

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta)

    // // Title
    entry[F.TITLE] = driver
      .findElement(By.cssSelector("[data-p13n-title]"))
      .getDomAttribute("data-p13n-title")
      .replace("\\\\n".r, "")
    // my Str:D $title =
    //   $web-driver.find_element_by_css_selector( '[data-p13n-title]' ).get_attribute( 'data-p13n-title' );
    // $title ~~ s:g/ "\n" //; # Remove extra newlines
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    // // Abstract
    entry[F.ABSTRACT] = driver
      .findElement(By.cssSelector("[data-abstract]"))
      .getDomAttribute("data-abstract")
      .replace("([.!?]) \\ \\ ".r, "$0\n\n") // Insert missing paragraphs.  This is a heuristic solution.
    // my Str:D $abstract =
    //   $web-driver.find_element_by_css_selector( '[data-abstract]' ).get_attribute( 'data-abstract' );
    // $abstract ~~ s:g/ (<[.!?]>) '  ' /$0\n\n/; # Insert missing paragraphs.  This is a heuristic solution.
    // $entry.fields<abstract> = BibScrape::BibTeX::Value.new($abstract);

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

    // my Str:D $title =
    //   ($web-driver.find_elements_by_class_name( 'item-title' )
    //     || $web-driver.find_elements_by_class_name( 'title-font' )).head.get_property( 'innerHTML' );
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    // // DOI
    entry[F.DOI] = driver.findElement(By.cssSelector("[data-doi]")).getDomAttribute("data-doi")

    // // ISSN
    entry.update(F.ISSN) { it.replace("^ ([0-9Xx]+) ,\\ ([0-9Xx]+) $".r, "$1 (Print) $2 (Online)") }

    // // Month
    val month = (
      driver.findElements(By.cssSelector(".turn-away-content__article-summary-journal a")) +
        driver.findElements(By.className("src"))
      ).first().innerHtml
    // my Str:D $month =
    //   ($web-driver.find_elements_by_css_selector( '.turn-away-content__article-summary-journal a' )
    //     || $web-driver.find_elements_by_class_name( 'src' )).head.get_property( 'innerHTML' );
    // if $month ~~ / '(' (<alpha>+) / {
    //   $entry.fields<month> = BibScrape::BibTeX::Value.new($0.Str);
    // }

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
    val pissn = issn.find("Print\\ ISSN\\ (\\d\\d\\d\\d - \\d\\d\\d [0-9Xx])".r)!!.groupValues[1]
    // my Str:D $pissn = ($issn ~~ / 'Print ISSN ' (\d\d\d\d '-' \d\d\d<[0..9Xx]>)/)[0].Str;
    val eissn = issn.find("Online\\ ISSN\\ (\\d\\d\\d\\d - \\d\\d\\d [0-9Xx])".r)!!.groupValues[1]
    // my Str:D $eissn = ($issn ~~ / 'Online ISSN ' (\d\d\d\d '-' \d\d\d<[0..9Xx]>)/)[0].Str;
    entry[F.ISSN] = "${pissn} (Print) ${eissn} (Online)"
    // $entry.fields<issn> = BibScrape::BibTeX::Value.new("$pissn (Print) $eissn (Online)");

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
    // await({
    //   $web-driver.find_element_by_id( 'export-citation' ).click;
    //   $web-driver.find_element_by_css_selector( 'button[aria-label="bibtex"]' ).click;
    //   True
    // });
    val entry = Bibtex.parseEntries(driver.pageSource).first()
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, "number" to true)

    // // Title
    val title = driver.findElement(By.className("title-text")).innerHtml
    entry[F.TITLE] = title

    // // Keywords
    entry[F.KEYWORDS] = driver
      .findElements(By.cssSelector(".keywords-section > .keyword > span"))
      .map { it.innerHtml }
      .joinToString("; ")

    // // Abstract
    val abstract = driver
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
    val file = BibtexFile()
    val entry = file.makeEntry("", "")
    // my BibScrape::BibTeX::Entry:D $entry = BibScrape::BibTeX::Entry.new();
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
    // if $entry.fields<editor>:exists {
    //   my Str:D $names = $entry.fields<editor>.simple-str;
    //   $names ~~ s:g/ ' '* "\n" / /;
    //   $entry.fields<editor> = BibScrape::BibTeX::Value.new($names);
    // }

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
    // my Str:D @pisbn = $web-driver.find_elements_by_id( 'print-isbn' )».get_property( 'innerHTML' );
    val eisbn = driver.findElement(By.id("electronic-isbn"))?.innerHtml
    // my Str:D @eisbn = $web-driver.find_elements_by_id( 'electronic-isbn' )».get_property( 'innerHTML' );
    if (pisbn != null && eisbn != null) entry[F.ISBN] = "${pisbn} (Print) ${eisbn} (Online)"
    // $entry.fields<isbn> = BibScrape::BibTeX::Value.new("{@pisbn.head} (Print) {@eisbn.head} (Online)")
    //   if @pisbn and @eisbn;

    // // ISSN
    // if $web-driver.find_element_by_tag_name( 'head' ).get_property( 'innerHTML' )
    //     ~~ / '{"eissn":"' (\d\d\d\d '-' \d\d\d<[0..9Xx]>) '","pissn":"' (\d\d\d\d '-' \d\d\d<[0..9Xx]>) '"}' / {
    //   $entry.fields<issn> = BibScrape::BibTeX::Value.new("$1 (Print) $0 (Online)");
    // }

    // // Series, Volume and ISSN
    //
    // Ugh, Springer doesn't have a reliable way to get the series, volume,
    // or ISSN.  Fortunately, this only happens for LNCS, so we hard code
    // it.
    // if $web-driver.find_element_by_tag_name( 'body' ).get_property( 'innerHTML' ) ~~ / '(LNCS, volume ' (\d*) ')' / {
    //   $entry.fields<volume> = BibScrape::BibTeX::Value.new($0.Str);
    //   $entry.fields<series> = BibScrape::BibTeX::Value.new( 'Lecture Notes in Computer Science' );
    // }

    // // Keywords
    entry[F.KEYWORDS] = driver
      .findElements(By.className("c-article-subject-list__subject"))
      .map { it.innerHtml }
      .joinToString("; ")
    // my Str:D @keywords =
    //   $web-driver.find_elements_by_class_name( 'c-article-subject-list__subject' )».get_property( 'innerHTML' );
    // $entry.fields<keywords> = BibScrape::BibTeX::Value.new(@keywords.join( '; ' ));

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
    // update($entry, 'publisher', {
    //   my Str:D $address = $entry.fields<address>.defined ?? $entry.fields<address>.simple-str !! '';
    //   $_ = 'Springer'
    //     if $_ eq "Springer, $address";
    // });

    return entry
  }
}
