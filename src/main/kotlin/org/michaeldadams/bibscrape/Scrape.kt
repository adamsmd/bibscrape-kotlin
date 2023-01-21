package org.michaeldadams.bibscrape

import org.openqa.selenium.WebDriver
import org.openqa.selenium.By

class Scrape {

  fun dispatch(driver: WebDriver, url: String): Unit { // BibTexEntry
    // val newUrl = url.subst("""^doi:(https?://(dx\.)?doi.org/)?""".toRegex(), "https://doi.org/")
    // driver.get(url)

    // val domain = driver.currentUrl.match("""^[^/]*//([^/]*/""".toRegex())
  }
  // sub dispatch(Str:D $url is copy --> BibScrape::BibTeX::Entry:D) {
  //   # Support 'doi:' as a url type
  //   $url ~~ s:i/^ 'doi:' [ 'http' 's'? '://' 'dx.'? 'doi.org/' ]? /https:\/\/doi.org\//;
  //   $web-driver.get($url);
  
  //   # Get the domain after following any redirects
  //   my Str:D $domain = ($web-driver%<current_url> ~~ m[ ^ <-[/]>* "//" <( <-[/]>* )> "/"]).Str;
  //   return do given $domain {
  //     when m[ « 'acm.org'             $] { scrape-acm(); }
  //     when m[ « 'arxiv.org'           $] { scrape-arxiv(); }
  //     when m[ « 'cambridge.org'       $] { scrape-cambridge(); }
  //     when m[ « 'computer.org'        $] { scrape-ieee-computer(); }
  //     when m[ « 'ieeexplore.ieee.org' $] { scrape-ieee-explore(); }
  //     when m[ « 'iospress.com'        $] { scrape-ios-press(); }
  //     when m[ « 'jstor.org'           $] { scrape-jstor(); }
  //     when m[ « 'oup.com'             $] { scrape-oxford(); }
  //     when m[ « 'sciencedirect.com'   $]
  //       || m[ « 'elsevier.com'        $] { scrape-science-direct(); }
  //     when m[ « 'link.springer.com'   $] { scrape-springer(); }
  //     default { die "Unsupported domain: $domain"; }
  //   };
  // }

  fun scrapeAcm(driver: WebDriver): Unit { // BibTeXEntry 
    if ("Association for Computing Machinery" != driver.findElement(By.className("publisher__name")).getDomProperty("innterHTML")) {
      // my Str:D @url = $web-driver.find_elements_by_class_name( 'issue-item__doi' )».get_attribute( 'href' );
    }
  }
}


//   if 'Association for Computing Machinery' ne
//       $web-driver.find_element_by_class_name( 'publisher__name' ).get_property( 'innerHTML' ) {
//     my Str:D @url = $web-driver.find_elements_by_class_name( 'issue-item__doi' )».get_attribute( 'href' );
//     if @url { return dispatch(@url.head); }
//     else { say "WARNING: Non-ACM paper at ACM link, and could not find link to actual publisher"; }
//   }

//   ## BibTeX
//   $web-driver.find_element_by_css_selector( 'a[data-title="Export Citation"]').click;
//   my Str:D @citation-text =
//     await({
//       $web-driver.find_elements_by_css_selector( '#exportCitation .csl-right-inline' ) })
//         .map({ $_ % <text> });

//   # Avoid SIGPLAN Notices, SIGSOFT Software Eng Note, etc. by prefering
//   # non-journal over journal
//   my Array:D[BibScrape::BibTeX::Entry:D] %entry = @citation-text
//     .flatmap({ bibtex-parse($_).items })
//     .grep({ $_ ~~ BibScrape::BibTeX::Entry })
//     .classify({ .fields<journal>:exists });
//   my BibScrape::BibTeX::Entry:D $entry = (%entry<False> // %entry<True>).head;

//   ## HTML Meta
//   my BibScrape::HtmlMeta::HtmlMeta:D $meta = html-meta-parse($web-driver);
//   html-meta-bibtex($entry, $meta, :!journal #`(avoid SIGPLAN Notices));

//   ## Abstract
//   my Str:_ $abstract = $web-driver
//     .find_elements_by_css_selector( '.abstractSection.abstractInFull' )
//     .reverse.head
//     .get_property( 'innerHTML' );
//   if $abstract.defined and $abstract ne '<p>No abstract available.</p>' {
//     $entry.fields<abstract> = BibScrape::BibTeX::Value.new($abstract);
//   }

//   ## Author
//   my Str:D $author = $web-driver.find_elements_by_css_selector( '.citation .author-name' )».get_attribute( 'title' ).join( ' and ' );
//   $entry.fields<author> = BibScrape::BibTeX::Value.new($author);

//   ## Title
//   my Str:D $title = $web-driver.find_element_by_css_selector( '.citation__title' ).get_property( 'innerHTML' );
//   $entry.fields<title> = BibScrape::BibTeX::Value.new($title);

//   ## Month
//   #
//   # ACM publication months are often inconsistent within the same page.
//   # This is a best effort at picking the right month among these inconsistent results.
//   if $entry.fields<issue_date>:exists {
//     my Str:D $month = $entry.fields<issue_date>.simple-str.split(rx/\s+/).head;
//     if str2month($month) {
//       $entry.fields<month> = BibScrape::BibTeX::Value.new($month);
//     }
//   } elsif not $entry.fields<month>:exists {
//     my Str:D $month =
//       $web-driver.find_element_by_css_selector( '.book-meta + .cover-date' ).get_property( 'innerHTML' ).split(rx/\s+/).head;
//     $entry.fields<month> = BibScrape::BibTeX::Value.new($month);
//   }

//   ## Keywords
//   my Str:D @keywords = $web-driver.find_elements_by_css_selector( '.tags-widget__content a' )».get_property( 'innerHTML' );
//   # ACM is inconsistent about the order in which these are returned.
//   # We sort them so that we are deterministic.
//   @keywords .= sort;
//   $entry.fields<keywords> = BibScrape::BibTeX::Value.new(@keywords.join( '; ' ))
//     if @keywords;

//   ## Journal
//   if $entry.type eq 'article' {
//     my Str:D @journal = $web-driver.metas( 'citation_journal_title' );
//     $entry.fields<journal> = BibScrape::BibTeX::Value.new(@journal.head)
//       if @journal;
//   }

//   my Str:D %issn =
//     $web-driver
//       .find_elements_by_class_name( 'cover-image__details' )
//       .classify({ .find_elements_by_class_name( 'journal-meta' ).so })
//       .map({ $_.key => $_.value.head.get_property( 'innerHTML' ) });
//   if %issn {
//     my Str:D $issn = %issn<False> // %issn<True>;
//     my Str:_ $pissn =
//       $issn ~~ / '<span class="bold">ISSN:</span><span class="space">' (.*?) '</span>' /
//         ?? $0.Str !! Str;
//     my Str:_ $eissn =
//       $issn ~~ / '<span class="bold">EISSN:</span><span class="space">' (.*?) '</span>' /
//         ?? $0.Str !! Str;
//     if $pissn and $eissn {
//       $entry.fields<issn> = BibScrape::BibTeX::Value.new("$pissn (Print) $eissn (Online)");
//     }
//   }

//   ## Pages
//   if $entry.fields<articleno>:exists
//       and $entry.fields<numpages>:exists
//       and not $entry.fields<pages>:exists {
//     my Str:D $articleno = $entry.fields<articleno>.simple-str;
//     my Str:D $numpages = $entry.fields<numpages>.simple-str;
//     $entry.fields<pages> = BibScrape::BibTeX::Value.new("$articleno:1--$articleno:$numpages");
//   }

//   $entry;
// }
