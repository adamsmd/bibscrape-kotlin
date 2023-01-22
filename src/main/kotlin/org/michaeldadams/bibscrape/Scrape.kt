package org.michaeldadams.bibscrape

import bibtex.parser.BibtexParser
import bibtex.dom.BibtexFile
import bibtex.dom.BibtexEntry
import org.openqa.selenium.WebDriver
import org.openqa.selenium.By
import kotlin.text.toRegex
import kotlin.math.roundToLong
import java.time.Duration
import kotlinx.datetime.Clock
import java.io.StringReader
import org.michaeldadams.bibscrape.Month

//@kotlin.time.ExperimentalTime
object Scrape {

  fun dispatch(driver: WebDriver, url: String): BibtexEntry {
    val newUrl = """^doi:(https?://(dx\.)?doi.org/)?""".toRegex().replace(url, "https://doi.org/")
    driver.get(newUrl)

    val domainMatchResult = """^[^/]*//([^/]*)/""".toRegex().find(driver.currentUrl)

    if (domainMatchResult == null) { throw Error("TODO") }
    val domain = domainMatchResult.groupValues.get(1)

    val domainMap = mapOf(
      "acm.org"             to ::scrapeAcm,
      "arxiv.org"           to ::scrapeArxiv,
      "cambridge.org"       to ::scrapeCambridge,
      "computer.org"        to ::scrapeIeeeComputer,
      "ieeexplore.ieee.org" to ::scrapeIeeeExplore,
      "iospress.com"        to ::scrapeIosPress,
      "jstor.org"           to ::scrapeJstor,
      "oup.com"             to ::scrapeOxford,
      "sciencedirect.com"   to ::scrapeScienceDirect,
      "elsevier.com"        to ::scrapeElsevier,
      "link.springer.com"   to ::scrapeSpringer,
    )

    for ((dom, function) in domainMap) {
      if("\\b${Regex.escape(dom)}\$".toRegex().containsMatchIn(domain)) {
        return function(driver)
      }
    }

    throw Error("Unsupported domain: $domain")
  }

  fun <T> await(driver: WebDriver, timeout: Double = 30.0, block: () -> T): T {
    val oldWait = driver.manage().timeouts().implicitWaitTimeout
    driver.manage().timeouts().implicitlyWait(Duration.ofMillis((1000 * timeout).roundToLong()))
    val result = block()
    driver.manage().timeouts().implicitlyWait(oldWait)
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

  fun parseBibtex(s: String): List<BibtexEntry> {
    val bibtexFile = BibtexFile()
    val parser = BibtexParser(false)
    parser.parse(bibtexFile, StringReader(s))
    return bibtexFile.getEntries().filterIsInstance<BibtexEntry>()
  }

  fun scrapeAcm(driver: WebDriver): BibtexEntry {
    // driver.manage().timeouts().implicitlyWait(Duration.ofMillis((1000 * 30.0).roundToLong()))
    // TODO: prevent loops on ACM
    if ("Association for Computing Machinery" != driver.findElement(By.className("publisher__name")).getDomProperty("innerHTML")) {
      val urls = driver.findElements(By.className("issue-item__doi")).mapNotNull { it.getAttribute("href") }
      // TODO: filter to non-acm links
      if (urls.size > 0) { return dispatch(driver, urls.first()) }
      else { TODO("WARNING: Non-ACM paper at ACM link, and could not find link to actual publisher") }
    }

    //// BibTeX
    driver.findElement(By.cssSelector("""a[data-title="Export Citation"]""")).click()
    val entries: List<BibtexEntry> =
      await(driver) {driver.findElements(By.cssSelector("#exportCitation .csl-right-inline"))}
        .flatMap { parseBibtex(it.text) }
        // Avoid SIGPLAN Notices, SIGSOFT Software Eng Note, etc. by prefering
        // non-journal over journal
        .sortedBy { it.getFieldValue("journal") != null }
    val entry = entries.first()

    //// HTML Meta
    val meta = HtmlMeta.parse(driver)
    HtmlMeta.bibtex(entry, meta, "journal" to false)

    //// Abstract
    val abstract = driver
      .findElements(By.cssSelector(".abstractSection.abstractInFull"))
      .last().getDomProperty("innerHTML")
    if (abstract != null && abstract != "<p>No abstract available.</p>") {
      entry.setField("abstract", entry.getOwnerFile().makeString(abstract))
    }

    //// Author
    val author = driver.findElements(By.cssSelector(".citation .author-name")).map { it.getAttribute("title") }.joinToString(" and ")
    entry.setField("author", entry.getOwnerFile().makeString(author))

    //// Title
    val title = driver.findElement(By.cssSelector(".citation__title")).getDomProperty("innerHTML")
    entry.setField("title", entry.getOwnerFile().makeString(title))

    //// Month
    //
    // ACM publication months are often inconsistent within the same page.
    // This is a best effort at picking the right month among these inconsistent results.
    if (entry.getFieldValue("issue_date") != null) {
      val month = entry.getFieldValue("issue_date").toString().split("\\s+").first()
      if (Month.str2month(entry.getOwnerFile(), month) != null) {
        entry.setField("month", entry.getOwnerFile().makeString(month))
      }
    } else if (entry.getFieldValue("month") != null) {
      val month = driver.findElement(By.cssSelector(".book-meta + .cover-date")).getDomProperty("innerHTML").split("\\s+").first()
      entry.setField("month", entry.getOwnerFile().makeString(month))
    }

    //// Keywords
    val keywords = driver
      .findElements(By.cssSelector(".tags-widget__content a"))
      .map { it.getDomProperty("innerHTML") }
      // ACM is inconsistent about the order in which these are returned.
      // We sort them so that we are deterministic.
      .sorted()
    if (keywords.size > 0) {
      entry.setField("keywords", entry.getOwnerFile().makeString(keywords.joinToString("; ")))
    }

    //// Journal
    if (entry.getEntryType() == "article") {
      val journal =
        driver.findElements(By.cssSelector("meta[name=\"citation_journal_title\"]" )).map { it.getAttribute("content") }
      if (journal.size > 0) {
        entry.setField("journal", entry.getOwnerFile().makeString(journal.first()))
      }
    }

    val issns =
      driver
        .findElements(By.className("cover-image__details"))
        .sortedBy({ it.findElements(By.className("journal-meta")).size > 0 })
    if (issns.size > 0) {
      val issn = issns.first().getDomProperty("innerHTML")
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
            "${pissn.groupValues.get(1)} (Print) ${eissn.groupValues.get(1)} (Online)"))
      }
    }

    //// Pages
    if (entry.getFieldValue("articleno") != null
      && entry.getFieldValue("numpages") != null
      && entry.getFieldValue("pages") == null) {
      val articleno = entry.getFieldValue("articleno").toString()
      val numpages = entry.getFieldValue("numpages").toString()
      entry.setField("pages", entry.getOwnerFile().makeString("$articleno:1--$articleno:$numpages"))
    }

    return entry
  }

  fun scrapeArxiv(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeCambridge(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeIeeeComputer(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeIeeeExplore(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeIosPress(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeJstor(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeOxford(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeScienceDirect(driver: WebDriver): BibtexEntry {
    TODO()
  }

  fun scrapeElsevier(driver: WebDriver): BibtexEntry {
    return scrapeScienceDirect(driver)
  }

  fun scrapeSpringer(driver: WebDriver): BibtexEntry {
    TODO()
  }
}
