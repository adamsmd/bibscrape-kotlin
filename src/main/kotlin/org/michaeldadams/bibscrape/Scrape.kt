package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry

/** Scraping functions for BibTeX data from publisher websites, but without
 * making an effort to format them nicely. */
object Scrape {
  /** Scrapes an arbitrary URL.
   *
   * @param driver the [WebDriver] to use for scraping
   * @param url the URL to scrape
   * @return the [BibtexEntry] that was scraped
   */
  fun dispatch(driver: Driver, url: String): BibtexEntry {
    val newUrl = url.replace("^ doi: (http s? :// (dx\\.)? doi.org/)?".r, "https://doi.org/")
    driver.get(newUrl)

    val domainMatchResult = driver.currentUrl.find("^ [^/]* // ([^/]*) /".r)

    if (domainMatchResult == null) { throw Error("TODO") }
    val domain = domainMatchResult.groupValues[1]

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
        if ("\\b ${Regex.escape(d)} $".r.containsMatchIn(domain)) {
          return scraper.scrape(driver)
        }
      }
    }

    throw Error("Unsupported domain: $domain")
  }
}
