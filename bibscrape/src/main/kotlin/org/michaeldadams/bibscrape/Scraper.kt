package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import java.net.URI
import org.michaeldadams.bibscrape.Bibtex.Fields as F

/** Exception thrown when asked to scrape a domain for which there is no scraper.
 *
 * @property domain the requested domain
 * @property url the url that resulted in the requested domain
 */
data class UnsupportedDomainException(val domain: String, val url: URI) :
  Exception("Unsupported domain '${domain}' while scraping '${url}'")

/** Scraping functions for BibTeX data from publisher websites, but without
 * making an effort to format them nicely. */
object Scraper {
  /** Scrapes an arbitrary URL.
   *
   * @param url the URL to scrape
   * @param window whether to show the browser window while scraping
   * @param timeout the timeout in seconds to use
   * @return the [BibtexEntry] that was scraped
   */
  fun scrape(url: URI, window: Boolean, timeout: Double): BibtexEntry =
    // TODO: option for withLogFile
    // TODO: option for verbose
    Driver.make(headless = !window, verbose = true, timeout = timeout).use { driver ->
      val entry = dispatch(driver, url)
      entry[F.BIB_SCRAPE_URL] = url.toString()
      entry
    }

  /** Scrapes an arbitrary URL.
   *
   * @param driver the [WebDriver] to use for scraping
   * @param url the URL to scrape
   * @return the [BibtexEntry] that was scraped
   */
  fun dispatch(driver: Driver, url: URI): BibtexEntry {
    driver.get(url.toString())
    val domain = URI(driver.currentUrl).host

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
      for (scraperDomain in scraper.domains) {
        if (domain.contains("\\b ${Regex.escape(scraperDomain)} $".ri)) {
          return scraper.scrape(driver)
        }
      }
    }

    throw UnsupportedDomainException(domain, url)
  }
}
