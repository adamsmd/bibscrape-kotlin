package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import bibtex.parser.BibtexParser
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import java.io.StringReader
import java.time.Duration
import kotlin.math.roundToLong
import kotlin.text.toRegex

/** Scraping functions for BibTeX data from publisher websites, but without
 * making an effort to format them nicely. */
object Scrape {
  /** Scrapes an arbitrary URL.
   *
   * @param driver the [WebDriver] to use for scraping
   * @param url the URL to scrape
   * @return the [BibtexEntry] that was scraped
   */
  fun dispatch(driver: WebDriver, url: String): BibtexEntry {
    val newUrl = """^doi:(https?://(dx\.)?doi.org/)?""".toRegex().replace(url, "https://doi.org/")
    driver.get(newUrl)

    val domainMatchResult = """^[^/]*//([^/]*)/""".toRegex().find(driver.currentUrl)

    if (domainMatchResult == null) { throw Error("TODO") }
    val domain = domainMatchResult.groupValues.get(1)

    val scrapers = listOf(
      ScrapeAcm,
      ScrapeArxiv,
      ScrapeCambridge,
      ScrapeIeeeComputer,
      ScrapeIeeeExplore,
      ScrapeIosPress,
      ScrapeJstor,
      ScrapeOxford,
      ScrapeScienceDirect,
      ScrapeSpringer,
    )
    for (scraper in scrapers) {
      for (d in scraper.domains) {
        if ("\\b${Regex.escape(d)}\$".toRegex().containsMatchIn(domain)) {
          return scraper.scrape(driver)
        }
      }
    }

    throw Error("Unsupported domain: $domain")
  }
}

/** Scrapes BibTeX data for the specified [domains]. */
interface Scraper {
  /** The domains implemented by this scraper. */
  val domains: List<String>

  /** Scrapes the given [domain] assuming [driver] is already pointing at the
   * correct page.
   *
   * @param driver the driver to use for scraping
   * @return the BibTeX entry that was scraped
   */
  fun scrape(driver: WebDriver): BibtexEntry
}

/** Gets the `innerHTML` property of a [WebElement].
 *
 * @return the value of the `innerHTML` property
 */
fun WebElement.getInnerHtml(): String = this.getDomProperty("innerHTML")

/** Executes a given [script].
 *
 * @param script the JavaScript code to run
 * @param args the arguments to pass to the [script]
 * @return the value returned by the [script]
*/
fun WebDriver.executeScript(script: String, vararg args: Any?): Any? =
  (this as JavascriptExecutor).executeScript(script, *args)

// @Suppress("ClassOrdering", "WRONG_ORDER_IN_CLASS_LIKE_STRUCTURES")
private const val MILLIS_PER_SECOND = 1_000

/** Sets the driver's wait time while running a given [block].
 *
 * @param timeout the time to wait in seconds
 * @param block the code to execute and wait on
 * @return the value returned by the [block]
 */
fun <T> WebDriver.await(timeout: Double = 30.0, block: () -> T): T {
  val oldWait = this.manage().timeouts().implicitWaitTimeout
  this.manage().timeouts().implicitlyWait(Duration.ofMillis((MILLIS_PER_SECOND * timeout).roundToLong()))
  val result = block()
  this.manage().timeouts().implicitlyWait(oldWait)
  return result
}

// fun <T> await(driver: WebDriver, block: () -> T?, timeout: Double = 30.0, sleep: Double = 0.5): T {
//   val start = Clock.System.now()
//   while (true) {
//     try {
//       val result = block()
//       if (result != null) { return result }
//     } catch (e: Exception) {}
//     if ((Clock.System.now() - start) > timeout.seconds) {
//       throw Error("Timeout while waiting for the browser")
//     }
//     Thread.sleep((sleep * 1000.0).roundToLong())
//   }
// }
// sub await(&block --> Any:D) is export {
//   my Rat:D constant $timeout = 30.0;
//   my Rat:D constant $sleep = 0.5;
//   my Any:_ $result;
//   my Num:D $start = now.Num;
//   while True {
//     $result = &block();
//     if $result { return $result }
//     if now - $start > $timeout {
//       die "Timeout while waiting for the browser"
//     }
//     sleep $sleep;
//     CATCH { default { sleep $sleep; } }
//   }
// }

/** Parses a [string] into its constituent BibTeX entries.
 *
 * @param string the string to parse
 * @return the entries that were succesfully parsed
 */
fun parseBibtex(string: String): List<BibtexEntry> {
  val bibtexFile = BibtexFile()
  val parser = BibtexParser(false) // false => don't throw parse exceptions
  parser.parse(bibtexFile, StringReader(string))
  return bibtexFile.getEntries().filterIsInstance<BibtexEntry>()
}

/** Scrapes the ACM Digital Library. */
object ScrapeAcm : Scraper {
  override val domains = listOf("acm.org")

  override fun scrape(driver: WebDriver): BibtexEntry {
    // driver.manage().timeouts().implicitlyWait(Duration.ofMillis((1000 * 30.0).roundToLong()))
    // TODO: prevent loops on ACM
    if ("Association for Computing Machinery" !=
      driver.findElement(By.className("publisher__name")).getInnerHtml()
    ) {
      val urls = driver
        .findElements(By.className("issue-item__doi"))
        .mapNotNull { it.getAttribute("href") }
      // TODO: filter to non-acm links
      if (urls.size > 0) { return Scrape.dispatch(driver, urls.first()) }
      else { TODO("WARNING: Non-ACM paper at ACM link, and could not find link to actual publisher") }
    }

    // // BibTeX
    driver.findElement(By.cssSelector("""a[data-title="Export Citation"]""")).click()
    val entries: List<BibtexEntry> =
      driver.await { driver.findElements(By.cssSelector("#exportCitation .csl-right-inline")) }
        .flatMap { parseBibtex(it.text) }
        // Avoid SIGPLAN Notices, SIGSOFT Software Eng Note, etc. by prefering
        // non-journal over journal
        .sortedBy { it.getFieldValue("journal") != null }
    val entry = entries.first()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, "journal" to false)

    // // Abstract
    val abstract = driver
      .findElements(By.cssSelector(".abstractSection.abstractInFull"))
      .last()
      .getInnerHtml()
    if (abstract != "<p>No abstract available.</p>") {
      entry.setField("abstract", entry.getOwnerFile().makeString(abstract))
    }

    // // Author
    val author = driver
      .findElements(By.cssSelector(".citation .author-name"))
      .map { it.getAttribute("title") }
      .joinToString(" and ")
    entry.setField("author", entry.getOwnerFile().makeString(author))

    // // Title
    val title = driver.findElement(By.cssSelector(".citation__title")).getInnerHtml()
    entry.setField("title", entry.getOwnerFile().makeString(title))

    // // Month
    //
    // ACM publication months are often inconsistent within the same page.
    // This is a best effort at picking the right month among these inconsistent results.
    if (entry.getFieldValue("issue_date") != null) {
      val month = entry.getFieldValue("issue_date").toString().split("\\s+").first()
      if (Month.str2month(entry.getOwnerFile(), month) != null) {
        entry.setField("month", entry.getOwnerFile().makeString(month))
      }
    } else if (entry.getFieldValue("month") != null) {
      val month = driver
        .findElement(By.cssSelector(".book-meta + .cover-date"))
        .getInnerHtml()
        .split("\\s+")
        .first()
      entry.setField("month", entry.getOwnerFile().makeString(month))
    }

    // // Keywords
    val keywords = driver
      .findElements(By.cssSelector(".tags-widget__content a"))
      .map { it.getInnerHtml() }
      // ACM is inconsistent about the order in which these are returned.
      // We sort them so that we are deterministic.
      .sorted()
    if (keywords.size > 0) {
      entry.setField("keywords", entry.getOwnerFile().makeString(keywords.joinToString("; ")))
    }

    // // Journal
    if (entry.getEntryType() == "article") {
      val journal = driver
        .findElements(By.cssSelector("meta[name=\"citation_journal_title\"]"))
        .map { it.getAttribute("content") }
      if (journal.size > 0) {
        entry.setField("journal", entry.getOwnerFile().makeString(journal.first()))
      }
    }

    val issns =
      driver
        .findElements(By.className("cover-image__details"))
        .sortedBy({ it.findElements(By.className("journal-meta")).size > 0 })
    if (issns.size > 0) {
      val issn = issns.first().getInnerHtml()
      val pissn =
        """<span class="bold">ISSN:</span><span class="space">(.*?)</span>"""
          .toRegex()
          .find(issn)
      val eissn =
        """<span class="bold">EISSN:</span><span class="space">(.*?)</span>"""
          .toRegex()
          .find(issn)
      if (pissn != null && eissn != null) {
        entry.setField(
          "issn",
          entry.getOwnerFile().makeString(
            "${pissn.groupValues.get(1)} (Print) ${eissn.groupValues.get(1)} (Online)"
          )
        )
      }
    }

    // // Pages
    if (entry.getFieldValue("articleno") != null &&
      entry.getFieldValue("numpages") != null &&
      entry.getFieldValue("pages") == null
    ) {
      val articleno = entry.getFieldValue("articleno").toString()
      val numpages = entry.getFieldValue("numpages").toString()
      entry.setField("pages", entry.getOwnerFile().makeString("$articleno:1--$articleno:$numpages"))
    }

    return entry
  }
}

/** Scrapes the arXiv repository. */
object ScrapeArxiv : Scraper {
  override val domains = listOf("arxiv.org")

  override fun scrape(@Suppress("UNUSED_PARAMETER") driver: WebDriver): BibtexEntry {
    // format_bibtex_arxiv in
    // https://github.com/mattbierbaum/arxiv-bib-overlay/blob/master/src/ui/CiteModal.tsx
    // Ensure we are at the "abstract" page
    // $web-driver%<current_url> ~~ / '://arxiv.org/' (<-[/]>+) '/' (.*) $/;
    // if $0 ne 'abs' {
    //   $web-driver.get("https://arxiv.org/abs/$1");
    // }

    // Id
    // $web-driver%<current_url> ~~ / '://arxiv.org/' (<-[/]>+) '/' (.*) $/;
    // my Str:D $id = $1.Str;

    // Use the arXiv API to download meta-data
    // #$web-driver.get("https://export.arxiv.org/api/query?id_list=$id"); # Causes a timeout
    // #$web-driver.execute_script(
    // #   'window.location.href = arguments[0]', "https://export.arxiv.org/api/query?id_list=$id");
    // $web-driver.execute_script( 'window.open(arguments[0], "_self")',
    //   "https://export.arxiv.org/api/query?id_list=$id");
    // my Str:D $xml-string = $web-driver.read-downloads();
    // my XML::Document:D $xml = from-xml($xml-string);

    // my XML::Element:D @doi = $xml.getElementsByTagName('arxiv:doi');
    // if @doi and Backtrace.new.map({$_.subname}).grep({$_ eq 'scrape-arxiv'}) <= 1 {
    //   # Use publisher page if it exists
    //   dispatch('doi:' ~ @doi.head.contents».text.join(''));
    // } else {
    //   my XML::Element:D $xml-entry = $xml.getElementsByTagName('entry').head;

    //   sub text(XML::Element:D $element, Str:D $str --> Str:D) {
    //     my XML::Element:D @elements = $element.getElementsByTagName($str);
    //     if @elements {
    //       @elements.head.contents».text.join('');
    //     } else {
    //       '';
    //     }
    //   }

    //   # BibTeX object
    //   my BibScrape::BibTeX::Entry:D $entry = BibScrape::BibTeX::Entry.new(:type('misc'), :key("arxiv.$id"));

    //   # Title
    //   my Str:D $title = text($xml-entry, 'title');
    //   $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    //   # Author
    //   my XML::Element:D @authors = $xml-entry.getElementsByTagName('author');
    //   my Str:D $author = @authors.map({text($_, 'name')}).join( ' and ' );
    //     # author=<author><name> 	One for each author. Has child element <name> containing the author name.
    //   $entry.fields<author> = BibScrape::BibTeX::Value.new($author);

    //   # Affiliation
    //   my Str:D $affiliation = @authors.map({text($_, 'arxiv:affiliation')}).grep({$_ ne ''}).join( ' and ' );
    //     # affiliation=<author><arxiv:affiliation>
    //     // The author's affiliation included as a subelement of <author> if present.
    //   $entry.fields<affiliation> = BibScrape::BibTeX::Value.new($affiliation)
    //     if $affiliation ne '';

    //   # How published
    //   $entry.fields<howpublished> = BibScrape::BibTeX::Value.new('arXiv.org');

    //   # Year, month and day
    //   my Str:D $published = text($xml-entry, 'published');
    //   $published ~~ /^ (\d ** 4) '-' (\d ** 2) '-' (\d ** 2) 'T'/;
    //   my (Str:D $year, Str:D $month, Str:D $day) = ($0.Str, $1.Str, $2.Str);
    //     # year, month, day = <published> 	The date that version 1 of the article was submitted.
    //     # <updated> 	The date that the retrieved version of the article was
    //             submitted. Same as <published> if the retrieved version is version 1.
    //   $entry.fields<year> = BibScrape::BibTeX::Value.new($year);
    //   $entry.fields<month> = BibScrape::BibTeX::Value.new($month);
    //   $entry.fields<day> = BibScrape::BibTeX::Value.new($day);

    //   my Str:D $doi = $xml-entry.elements(:TAG<link>, :title<doi>).map({$_.attribs<href>}).join(';');
    //   $entry.fields<doi> = BibScrape::BibTeX::Value.new($doi)
    //     if $doi ne '';

    //   # Eprint
    //   my Str:D $eprint = $id;
    //   $entry.fields<eprint> = BibScrape::BibTeX::Value.new($eprint);

    //   # Archive prefix
    //   $entry.fields<archiveprefix> = BibScrape::BibTeX::Value.new('arXiv');

    //   # Primary class
    //   my Str:D $primaryClass = $xml-entry.getElementsByTagName('arxiv:primary_category').head.attribs<term>;
    //   $entry.fields<primaryclass> = BibScrape::BibTeX::Value.new($primaryClass);

    //   # Abstract
    //   my Str:D $abstract = text($xml-entry, 'summary');
    //   $entry.fields<abstract> = BibScrape::BibTeX::Value.new($abstract);

    //   # The following XML elements are ignored
    //   # <link> 	Can be up to 3 given url's associated with this article.
    //   # <category> 	The arXiv or ACM or MSC category for an article if present.
    //   # <arxiv:comment> 	The authors comment if present.
    //   # <arxiv:journal_ref> 	A journal reference if present.
    //   # <arxiv:doi> 	A url for the resolved DOI to an external resource if present.
    TODO()
  }
}

/** Scrapes Cambridge University Press. */
object ScrapeCambridge : Scraper {
  override val domains = listOf("cambridge.org")

  override fun scrape(driver: WebDriver): BibtexEntry {
    val m = "^https?://www.cambridge.org/core/services/aop-cambridge-core/content/view/('S'\\d+)\$"
      .toRegex()
      .matchEntire(driver.currentUrl)
    if (m != null) {
      driver.get("https://doi.org/10.1017/${m.groups[1]}")
    }
    // if $web-driver%<current_url> ~~
    //     /^ 'http' 's'? '://www.cambridge.org/core/services/aop-cambridge-core/content/view/' ( 'S' \d+) $/ {
    //   $web-driver.get("https://doi.org/10.1017/$0");
    // }

    // This must be before BibTeX otherwise Cambridge sometimes hangs due to an alert box
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    val meta = HtmlMeta.parse(driver)

    // // BibTeX
    driver.await { driver.findElement(By.className("export-citation-product")) }.click()
    // await({ $web-driver.find_element_by_class_name( 'export-citation-product' ) }).click;
    driver.await { driver.findElement(By.cssSelector("[data-export-type=\"bibtex\"]")) }.click()
    // await({ $web-driver.find_element_by_css_selector( '[data-export-type="bibtex"]' ) }).click;
    // val entry = parseBibtex(driver.readDownloads()).first()
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-parse($web-driver.read-downloads()).items.head;

    // // HTML Meta
    // html-meta-bibtex($entry, $meta, :!abstract);

    // // Title
    // my Str:D $title =
    //   await({ $web-driver.find_element_by_class_name( 'article-title' ) }).get_property( 'innerHTML' );
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

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
    // my Str:D $pissn = $web-driver.find_element_by_name( 'productIssn' ).get_attribute( 'value' );
    // my Str:D $eissn = $web-driver.find_element_by_name( 'productEissn' ).get_attribute( 'value' );
    // $entry.fields<issn> = BibScrape::BibTeX::Value.new("$pissn (Print) $eissn (Online)");

    TODO()
  }
}

/** Scrapes IEEE Computer. */
object ScrapeIeeeComputer : Scraper {
  override val domains = listOf("computer.org")

  override fun scrape(driver: WebDriver): BibtexEntry {
    // // BibTeX
    driver.await { driver.findElement(By.cssSelector(".article-action-toolbar button")) }.click()
    // await({ $web-driver.find_element_by_css_selector( '.article-action-toolbar button' ) }).click;
    val bibtexLink = driver.await { driver.findElement(By.linkText("BibTex")) }
    // my #`(Inline::Python::PythonObject:D) $bibtex-link =
    //           await({ $web-driver.find_element_by_link_text( 'BibTex' ) });
    driver.executeScript("arguments[0].removeAttribute(\"target\")", bibtexLink)
    // $web-driver.execute_script( 'arguments[0].removeAttribute("target")', $bibtex-link);
    driver.findElement(By.linkText("BibTex")).click()
    // $web-driver.find_element_by_link_text( 'BibTex' ).click;
    var bibtexText = driver.await { driver.findElement(By.tagName("pre")) }.getInnerHtml()
    // my Str:D $bibtex-text = await({ $web-driver.find_element_by_tag_name( 'pre' ) }).get_property( 'innerHTML' );
    // $bibtex-text ~~ s/ "\{," /\{key,/;
    // $bibtex-text = Blob.new($bibtex-text.ords).decode; # Fix UTF-8 encoding
    val entry = parseBibtex(bibtexText).first()
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-parse($bibtex-text).items.head;
    driver.navigate().back()

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    HtmlMeta.bibtex(entry, meta)
    // html-meta-bibtex($entry, $meta);

    // // Authors
    // my Str:D @authors =
    //   ($web-driver.find_elements_by_css_selector(
    //      'a[href^="https://www.computer.org/csdl/search/default?type=author&"]' )
    //   )».get_property( 'innerHTML' );
    // $entry.fields<author> = BibScrape::BibTeX::Value.new(@authors.join( ' and ' ));

    // // Affiliation
    // my Str:D @affiliations =
    //   ($web-driver.find_elements_by_class_name( 'article-author-affiliations' ))».get_property( 'innerHTML' );
    // $entry.fields<affiliation> = BibScrape::BibTeX::Value.new(@affiliations.join( ' and ' ))
    //   if @affiliations;

    // // Keywords
    // update($entry, 'keywords', { s:g/ ';' \s* /; / });

    TODO()
  }
}

/** Scrapes IEEE Explore. */
object ScrapeIeeeExplore : Scraper {
  override val domains = listOf("ieeexplore.ieee.org")

  override fun scrape(driver: WebDriver): BibtexEntry {
    // // BibTeX
    driver.await { driver.findElement(By.tagName("xpl-cite-this-modal")) }.click()
    // await({ $web-driver.find_element_by_tag_name( 'xpl-cite-this-modal' ) }).click;
    driver.await { driver.findElement(By.cssSelector("BibTeX")) }.click()
    // await({ $web-driver.find_element_by_link_text( 'BibTeX' ) }).click;
    driver.await { driver.findElement(By.cssSelector(".enable-abstract input")) }.click()
    // await({ $web-driver.find_element_by_css_selector( '.enable-abstract input' ) }).click;
    val text = driver.await { driver.findElement(By.className("ris-text")) }.getInnerHtml()
    // my Str:D $text = await({ $web-driver.find_element_by_class_name( 'ris-text' ) }).get_property( 'innerHTML' );
    val entry = parseBibtex(text).first()
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-parse($text).items.head;

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    HtmlMeta.bibtex(entry, meta)
    // html-meta-bibtex($entry, $meta);

    // // HTML body text
    // val body = driver.findElement(By.tagName("body")).getInnerHtml()
    // my Str:D $body = $web-driver.find_element_by_tag_name( 'body' ).get_property( 'innerHTML' );

    // // Keywords
    // my Str:D $keywords = $entry.fields<keywords>.simple-str;
    // $keywords ~~ s:g/ ';' ' '* /; /;
    // $entry.fields<keywords> = BibScrape::BibTeX::Value.new($keywords);

    // // Author
    // my Str:D $author = $entry.fields<author>.simple-str;
    // $author ~~ s:g/ '{' (<-[}]>+) '}' /$0/;
    // $entry.fields<author> = BibScrape::BibTeX::Value.new($author);

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
    // update($entry, 'abstract', { s/ '&lt;&gt;' $// });

    TODO()
  }
}

/** Scrapes IOS Press. */
object ScrapeIosPress : Scraper {
  override val domains = listOf("iospress.com")

  override fun scrape(@Suppress("UNUSED_PARAMETER") driver: WebDriver): BibtexEntry {
    // // RIS
    // await({ $web-driver.find_element_by_class_name( 'p13n-cite' ) }).click;
    // await({ $web-driver.find_element_by_class_name( 'btn-clear' ) }).click;
    // my BibScrape::Ris::Ris:D $ris = ris-parse($web-driver.read-downloads());
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-of-ris($ris);

    // // HTML Meta
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    // html-meta-bibtex($entry, $meta);

    // // Title
    // my Str:D $title =
    //   $web-driver.find_element_by_css_selector( '[data-p13n-title]' ).get_attribute( 'data-p13n-title' );
    // $title ~~ s:g/ "\n" //; # Remove extra newlines
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    // // Abstract
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

    TODO()
  }
}

/** Scrape JStor. */
object ScrapeJstor : Scraper {
  override val domains = listOf("jstor.org")

  override fun scrape(driver: WebDriver): BibtexEntry {
    // // Remove overlay
    val overlays = driver.findElements(By.className("reveal-overlay"))
    // my #`(Inline::Python::PythonObject:D) @overlays = $web-driver.find_elements_by_class_name( 'reveal-overlay' );
    overlays.forEach { driver.executeScript("arguments[0].removeAttribute(\"style\")", it) }
    // @overlays.map({ $web-driver.execute_script( 'arguments[0].removeAttribute("style")', $_) });

    // // BibTeX
    // Note that on-campus is different than off-campus
    // await({ $web-driver.find_elements_by_css_selector( '[data-qa="cite-this-item"]' )
    //         || $web-driver.find_elements_by_class_name( 'cite-this-item' ) }).head.click;
    // await({ $web-driver.find_element_by_css_selector( '[data-sc="text link: citation text"]' ) }).click;
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-parse($web-driver.read-downloads()).items.head;

    // // HTML Meta
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    // html-meta-bibtex($entry, $meta);

    // // Title
    // Note that on-campus is different than off-campus
    // my Str:D $title =
    //   ($web-driver.find_elements_by_class_name( 'item-title' )
    //     || $web-driver.find_elements_by_class_name( 'title-font' )).head.get_property( 'innerHTML' );
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    // // DOI
    // my Str:D $doi = $web-driver.find_element_by_css_selector( '[data-doi]' ).get_attribute( 'data-doi' );
    // $entry.fields<doi> = BibScrape::BibTeX::Value.new($doi);

    // // ISSN
    // update($entry, 'issn', { s/^ (<[0..9Xx]>+) ', ' (<[0..9Xx]>+) $/$0 (Print) $1 (Online)/ });

    // // Month
    // my Str:D $month =
    //   ($web-driver.find_elements_by_css_selector( '.turn-away-content__article-summary-journal a' )
    //     || $web-driver.find_elements_by_class_name( 'src' )).head.get_property( 'innerHTML' );
    // if $month ~~ / '(' (<alpha>+) / {
    //   $entry.fields<month> = BibScrape::BibTeX::Value.new($0.Str);
    // }

    // // Publisher
    // Note that on-campus is different than off-campus
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

    TODO()
  }
}

/** Scrape Oxford University Publishing. */
object ScrapeOxford : Scraper {
  override val domains = listOf("oup.com")

  override fun scrape(driver: WebDriver): BibtexEntry {
    // say "WARNING: Oxford imposes rate limiting.  BibScrape might hang if you try multiple papers in a row.";

    // // BibTeX
    driver.await { driver.findElement(By.className("js-cite-button")) }.click()
    // await({ $web-driver.find_element_by_class_name( 'js-cite-button' ) }).click;
    val selectElement = driver.await { driver.findElement(By.id("selectFormat")) }
    // my #`(Inline::Python::PythonObject:D) $select-element =
    //     await({ $web-driver.find_element_by_id( 'selectFormat' ) });
    // my #`(Inline::Python::PythonObject:D) $select = $web-driver.select($select-element);
    // await({
    //   $select.select_by_visible_text( '.bibtex (BibTex)' );
    //   my #`(Inline::Python::PythonObject:D) $button =
    //     $web-driver.find_element_by_class_name( 'citation-download-link' );
    //   # Make sure the drop-down was populated
    //   $button.get_attribute( 'class' ) !~~ / « 'disabled' » /
    //     and $button }
    // ).click;
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-parse($web-driver.read-downloads()).items.head;

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    // html-meta-bibtex($entry, $meta, :month, :year);

    // // Title
    val title = driver.findElement(By.className("article-title-main")).getInnerHtml()
    // my Str:D $title = $web-driver.find_element_by_class_name( 'article-title-main' ).get_property( 'innerHTML' );
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    // // Abstract
    val abstract = driver.findElement(By.className("abstract")).getInnerHtml()
    // my Str:D $abstract = $web-driver.find_element_by_class_name( 'abstract' ).get_property( 'innerHTML' );
    // $entry.fields<abstract> = BibScrape::BibTeX::Value.new($abstract);

    // // ISSN
    // my Str:D $issn = $web-driver.find_element_by_tag_name( 'body' ).get_property( 'innerHTML' );
    // my Str:D $pissn = ($issn ~~ / 'Print ISSN ' (\d\d\d\d '-' \d\d\d<[0..9Xx]>)/)[0].Str;
    // my Str:D $eissn = ($issn ~~ / 'Online ISSN ' (\d\d\d\d '-' \d\d\d<[0..9Xx]>)/)[0].Str;
    // $entry.fields<issn> = BibScrape::BibTeX::Value.new("$pissn (Print) $eissn (Online)");

    // // Publisher
    // update($entry, 'publisher', { s/^ 'Oxford Academic' $/Oxford University Press/ });

    TODO()
  }
}

/** Scrape Science Direct. */
object ScrapeScienceDirect : Scraper {
  override val domains = listOf("sciencedirect.com", "elsevier.com")

  override fun scrape(driver: WebDriver): BibtexEntry {
    // // BibTeX
    // await({
    //   $web-driver.find_element_by_id( 'export-citation' ).click;
    //   $web-driver.find_element_by_css_selector( 'button[aria-label="bibtex"]' ).click;
    //   True
    // });
    // my BibScrape::BibTeX::Entry:D $entry = bibtex-parse($web-driver.read-downloads()).items.head;

    // // HTML Meta
    val meta = HtmlMeta.parse(driver)
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    // html-meta-bibtex($entry, $meta, :number);

    // // Title
    val title = driver.findElement(By.className("title-text")).getInnerHtml()
    // my Str:D $title = $web-driver.find_element_by_class_name( 'title-text' ).get_property( 'innerHTML' );
    // $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

    // // Keywords
    // my Str:D @keywords =
    //   ($web-driver.find_elements_by_css_selector(
    //      '.keywords-section > .keyword > span' ))».get_property( 'innerHTML' );
    // $entry.fields<keywords> = BibScrape::BibTeX::Value.new(@keywords.join( '; ' ));

    // // Abstract
    // my Str:D @abstract =
    //   ($web-driver.find_elements_by_css_selector( '.abstract > div' ))».get_property( 'innerHTML' );
    // $entry.fields<abstract> = BibScrape::BibTeX::Value.new(@abstract.head)
    //   if @abstract;

    // // Series
    // if $entry.fields<note> {
    //   $entry.fields<series> = $entry.fields<note>;
    //   $entry.fields<note>:delete;
    // }

    TODO()
  }
}

/** Scrape Springer. */
object ScrapeSpringer : Scraper {
  override val domains = listOf("link.springer.com")

  override fun scrape(driver: WebDriver): BibtexEntry {
    // // BibTeX
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
    // my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
    // $entry.type = html-meta-type($meta);
    // html-meta-bibtex($entry, $meta, :publisher);

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
    // my Str:D @pisbn = $web-driver.find_elements_by_id( 'print-isbn' )».get_property( 'innerHTML' );
    // my Str:D @eisbn = $web-driver.find_elements_by_id( 'electronic-isbn' )».get_property( 'innerHTML' );
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

    TODO()
  }
}
