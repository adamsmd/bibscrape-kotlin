package org.michaeldadams.bibscrape

import bibtex.parser.BibtexParser
import bibtex.dom.BibtexFile
import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexString
import org.openqa.selenium.WebDriver
import org.openqa.selenium.By
import kotlin.text.toRegex
import kotlin.math.roundToLong
import java.time.Duration
import kotlinx.datetime.Clock
import java.io.StringReader

typealias HtmlMetaTable = Map<String, List<String>>

object HtmlMeta {

  fun parse(driver: WebDriver): HtmlMetaTable =
    driver
      .findElements(By.cssSelector("meta[name]"))
      .groupBy({ it.getAttribute("name") }, { it.getAttribute("content") })

  fun bibtex(entry: BibtexEntry, meta: HtmlMetaTable, vararg fields: Pair<String, Boolean>) {
    //   sub html-meta-bibtex(
    //     BibScrape::BibTeX::Entry:D $entry,
    //     HtmlMeta:D $html-meta,
    //     *%fields where { $_.values.all ~~ Bool:D }
    //     --> HtmlMeta:D) is export {
    //   my BibScrape::BibTeX::Value:D %values;
    val fieldsMap = fields.toMap()
    val values = mutableMapOf<String, String>()
    fun getFirst(vararg fields: String): String? =
      fieldsMap.flatMap { meta.getOrDefault(it, listOf()) }.firstOrNull()

    fun set(field: String, value: String?): Unit {
      if (value != null) { values.set(field, value) }
    }
    //   sub set(Str:D $field, $value where Any:U | Str:_ | BibScrape::BibTeX::Piece:_ --> Any:U) {
    //     if $value {
    //       %values{$field} = BibScrape::BibTeX::Value.new($value);
    //     }
    //     return;
    //   }

    // The meta-data is highly redundent and multiple fields contain
    // similar information.  In the following we choose fields that
    // work for all publishers, but note what other fields also contain
    // that information.

    // 'author', 'dc.contributor', 'dc.creator', 'rft_aufirst', 'rft_aulast', and 'rft_au'
    // also contain authorship information
    //   my Str:D @authors;
    //   if %meta<citation_author>:exists { @authors = @(%meta<citation_author>) }
    //   elsif %meta<citation_authors> { @authors = %meta<citation_authors>.head.split(';') }
    //   set( 'author', @authors.map({ s:g/^ ' '+//; s:g/ ' '+ $//; $_ }).join( ' and ' ))
    //     if @authors;

    // 'title', 'rft_title', 'dc.title', 'twitter:title' also contain title information
    set("title", getFirst("citation_title"))

    // test/acm-17.t has the article number in 'citation_firstpage' but no 'citation_firstpage'
    // test/ieee-computer-1.t has 'pages' but empty 'citation_firstpage'
    //   if %meta<citation_firstpage>:exists and %meta<citation_firstpage>.head
    //       and %meta<citation_lastpage>:exists and %meta<citation_lastpage>.head {
    //     set( 'pages',
    //       %meta<citation_firstpage>.head ~
    //       (%meta<citation_firstpage>.head ne %meta<citation_lastpage>.head
    //         ?? "--" ~ %meta<citation_lastpage>.head
    //         !! ""));
    //   } else {
    //     set( 'pages', %meta<pages>.head);
    //   }
    
    set("volume", getFirst("citation_volume"))
    set("number", getFirst("citation_issue"))
    
    // 'keywords' also contains keyword information
    if (meta.containsKey("citation_keywords")) {
      set("keywords",
        meta
          .getOrDefault("citation_keywords", listOf())
          .map {
            """^\s*;*""".toRegex().replace(
              """;*\s*$""".toRegex().replace(
                it, ""), "")
          }.joinToString("; "))
    }
    //   set( 'keywords',
    //     %meta<citation_keywords>
    //     .map({ s/^ \s* ';'* //; s/ ';'* \s* $//; $_ })
    //     .join( '; ' ))
    //     if %meta<citation_keywords>:exists;
    
    // 'rft_pub' also contains publisher information
    set("publisher", getFirst("citation_publisher", "dc.publisher", "st.publisher"))
    
    // 'dc.date', 'rft_date', 'citation_online_date' also contain date information
    //   if %meta<citation_publication_date>:exists {
    //     if (%meta<citation_publication_date>.head ~~ /^ (\d\d\d\d) <[/-]> (\d\d) [ <[/-]> (\d\d) ]? $/) {
    //       my Str:D ($year, $month) = ($0.Str, $1.Str);
    //       set( 'year', $year);
    //       set( 'month', num2month($month));
    //     }
    //   } elsif %meta<citation_date>:exists {
    //     if %meta<citation_date>.head ~~ /^ (\d\d) <[/-]> \d\d <[/-]> (\d\d\d\d) $/ {
    //       my Str:D ($month, $year) = ($0.Str, $1.Str);
    //       set( 'year', $year);
    //       set( 'month', num2month($month));
    //     } elsif %meta<citation_date>.head ~~ /^ <[\ 0..9-]>*? <wb> (\w+) <wb> <[\ .0..9-]>*? <wb> (\d\d\d\d) <wb> / {
    //       my Str:D ($month, $year) = ($0.Str, $1.Str);
    //       set( 'year', $year);
    //       set( 'month', str2month($month));
    //     }
    //   }
    
    // 'dc.relation.ispartof', 'rft_jtitle', 'citation_journal_abbrev' also contain collection information
    for ((k, b) in listOf(
        "citation_conference" to "booktitle",
        "citation_journal_title" to "journal",
        "citation_inbook_title" to "booktitle",
        "citation_inbook_title" to "journal")) {
      if (meta.containsKey(k)) {
        set(b, getFirst(k))
        break
      }
    }
    // if %meta<citation_conference>:exists { set( 'booktitle', %meta<citation_conference>.head) }
    // elsif %meta<citation_journal_title>:exists { set( 'journal', %meta<citation_journal_title>.head) }
    // elsif %meta<citation_inbook_title>:exists { set( 'booktitle', %meta<citation_inbook_title>.head) }
    // elsif %meta<st.title>:exists { set( 'journal', %meta<st.title>.head) }
    
    // 'rft_id' and 'doi' also contain doi information
    //   if %meta<citation_doi>:exists { set( 'doi', %meta<citation_doi>.head )}
    //   elsif %meta<st.discriminator>:exists { set( 'doi', %meta<st.discriminator>.head) }
    //   elsif %meta<dc.identifier>:exists and %meta<dc.identifier>.head ~~ /^ 'doi:' (.+) $/ { set( 'doi', $1) }
    
    // If we get two ISBNs then one is online and the other is print so
    // we don't know which one to use and we can't use either one
    if (meta.getOrDefault("citation_isbn", listOf()).size == 1) {
      set("isbn", getFirst("citation_isbn"))
    }
    //   if %meta<citation_isbn>:exists and 1 == %meta<citation_isbn>.elems {
    //     set( 'isbn', %meta<citation_isbn>.head);
    //   }
    
    // 'rft_issn' also contains ISSN information
    //   if %meta<st.printissn>:exists and %meta<st.onlineissn>:exists {
    //     set( 'issn', %meta<st.printissn>.head ~ ' (Print) ' ~ %meta<st.onlineissn>.head ~ ' (Online)');
    //   } elsif %meta<citation_issn>:exists and 1 == %meta<citation_issn>.elems {
    //     set( 'issn', %meta<citation_issn>.head);
    //   }
    
    set("language", getFirst("citation_language", "dc.language"))
    
    // 'dc.description' also contains abstract information
    //   for (%meta<description>, %meta<Description>).flat -> Array:_[Str:D] $d {
    //     set( 'abstract', $d.head) if $d.defined and $d !~~ /^ [ '' $ | '****' | 'IEEE Xplore' | 'IEEE Computer Society' ] /;
    //   }
    
    if (meta.containsKey("citation_author_institution")) {
      set("affiliation", meta.get("citation_author_institution")!!.joinToString(" and "))
    }
    //   set( 'affiliation', %meta<citation_author_institution>.join( ' and ' ))
    //     if %meta<citation_author_institution>:exists;
    
    // Copy results from values to entry
    for ((k, v) in values) {
      if (fieldsMap.getOrDefault(k, entry.getFieldValue(k) == null)) {
        entry.setField(k, entry.getOwnerFile().makeString(v)) // TODO: BibtexPersonList
      }
    }
    //   for %values.kv -> Str:D $key, BibScrape::BibTeX::Value:D $value {
    //     if %fields{$key}:exists ?? %fields{$key} !! not $entry.fields{$key}:exists {
    //       $entry.fields{$key} = $value;
    //     }
    //   }
    // }
    
    // ### Other fields
    // 
    // Some fields that we are not using but could include the following.
    // (The numbers in front are how many tests could use that field.)
    // 
    // # Article
    //     12 citation_author_email (unused: author e-mail)
    // 
    // # URL (unused)
    //      4 citation_fulltext_html_url (good: url)
    //      7 citation_public_url (unused: page url)
    //     10 citation_springer_api_url (broken: url broken key)
    //     64 citation_abstract_html_url (good: url may dup)
    //     69 citation_pdf_url (good: url may dup)
    // 
    // # Misc (unused)
    //      7 citation_section
    //      7 issue_cover_image
    //      7 citation_id (unused: some sort of id)
    //      7 citation_id_from_sass_path (unused: some sort of id)
    //      7 citation_mjid (unused: some sort of id)
    //      7 hw.identifier
    //     25 rft_genre (always "Article")
    //      8 st.datatype (always "JOURNAL")
    //     25 rft_place (always "Cambridge")
    //        citation_fulltext_world_readable (always "")
    //      9 article_references (unused: textual version of reference)
    // 
    // ### Non-citation related
    //      7 hw.ad-path
    //      8 st.platformapikey (unused: API key)
    //      7 dc.type (always "text")
    //     14 dc.format (always "text/html")
    //      7 googlebot
    //      8 robots
    //      8 twitter:card
    //      8 twitter:image
    //      8 twitter:description
    //      8 twitter:site
    //     17 viewport
    //     25 coins
    //     10 msapplication-tilecolor
    //     10 msapplication-tileimage
    //     25 test
    //     25 verify-v1
    //     35 format-detection

    // pbContext -> ;
    // page:string:Article/Chapter View;
    // subPage:string:Abstract;
    // wgroup:string:ACM Publication Websites;
    // 
    // issue:issue:doi\:10.1145/800125;
    // 
    // groupTopic:topic:acm-pubtype>proceeding;
    // topic:topic:conference-collections>stoc;
    // csubtype:string:Conference Proceedings;
    // 
    // article:article:doi\:10.1145/800125.804056;
    // 
    // website:website:dl-site;
    // ctype:string:Book Content;
    // journal:journal:acmconferences;
    // pageGroup:string:Publication Pages
    // 
    // dc.Format -> text/HTML
    // dc.Language -> EN
    // dc.Coverage -> world
    // robots -> noarchive
    // viewport -> width=device-width,initial-scale=1
    // msapplication-TileColor -> #00a300
    // theme-color -> #ffffff

    // Highwire Press tags (e.g., citation_title)
    // Eprints tags (e.g., eprints.title)
    // BE Press tags (e.g., bepress_citation_title)
    // PRISM tags (e.g., prism.title)
    //  Dublin Core tags (e.g., DC.title)

  }
}
