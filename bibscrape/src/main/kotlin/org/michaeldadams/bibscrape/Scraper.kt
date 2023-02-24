package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import java.net.URI
import kotlin.time.Duration
import org.michaeldadams.bibscrape.Bibtex.Fields as F

/** Scraping functions for BibTeX data from publisher websites, but without
 * making an effort to format them nicely.
 */
object Scraper {
  /** Scrapes an arbitrary URL.
   *
   * @param url the URL to scrape
   * @param window whether to show the browser window while scraping
   * @param verbose whether to print debugging output
   * @param timeout the timeout in seconds to use
   * @return the [BibtexEntry] that was scraped
   */
  fun scrape(url: String, window: Boolean, verbose: Boolean, timeout: Duration): BibtexEntry =
    // TODO: option for withLogFile
    // TODO: option for verbose
    Driver.make(headless = !window, verbose = verbose, timeout = timeout).use { driver ->
      val entry = dispatch(driver, url)
      entry[F.BIB_SCRAPE_URL] = url
      entry
    }

  /** Scrapes an arbitrary URL.
   *
   * @param driver the [WebDriver] to use for scraping
   * @param url the URL to scrape
   * @return the [BibtexEntry] that was scraped
   * @throws UnsupportedDomainException thrown if there is no scraper for the domain after all redirects in [url]
   */
  fun dispatch(driver: Driver, url: String): BibtexEntry {
    driver.get(url.replace("^ doi: ( http s? :// (dx\\.)? doi\\.org/ )?".ri, "https://doi.org/"))
    val domain = URI(driver.currentUrl).host

    @Suppress("ktlint:trailing-comma-on-call-site")
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

  /** Exception thrown when asked to scrape a domain for which there is no scraper.
   *
   * @property domain the requested domain
   * @property url the url that resulted in the requested domain
   */
  data class UnsupportedDomainException(val domain: String, val url: String) :
    Exception("Unsupported domain '${domain}' while scraping '${url}'")
}
