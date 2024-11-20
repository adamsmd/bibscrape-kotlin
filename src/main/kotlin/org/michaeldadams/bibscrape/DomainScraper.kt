package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry

/** Scrapes BibTeX data for the specified [domains]. */
interface DomainScraper {
  /** The domains implemented by this domain scraper. */
  val domains: List<String>

  /** Scrapes the given [domain] assuming [driver] is already pointing at the
   * correct page.
   *
   * @param driver the driver to use for scraping
   * @return the BibTeX entry that was scraped
   */
  fun scrape(driver: Driver): BibtexEntry
}
