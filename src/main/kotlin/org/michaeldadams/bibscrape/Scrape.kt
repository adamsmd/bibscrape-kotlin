package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry

/** Scraping functions for BibTeX data from publisher websites, but without
 * making an effort to format them nicely. */
object Scrape {
  fun scrape(url: String, window: Boolean, timeout: Double): BibtexEntry {
    // TODO: option for withLogFile
    return Driver.make(!window, true).use { Scrape.dispatch(it, url) }
  }

  // sub scrape(Str:D $url is copy, Bool:D :$window, Num:D :$timeout --> BibScrape::BibTeX::Entry:D) is export {
  //   $web-driver =
  //     BibScrape::WebDriver::WebDriver.new(:$window, :$timeout);
  //   LEAVE { $web-driver.close(); }
  //   $web-driver.set_page_load_timeout($timeout);
   
  //   my BibScrape::BibTeX::Entry:D $entry = dispatch($url);
   
  //   $entry.fields<bib_scrape_url> = BibScrape::BibTeX::Value.new($url);
   
  //   # Remove undefined fields
  //   $entry.set-fields($entry.fields.grep({ $_ }));
   
  //   $entry;
  // }

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
