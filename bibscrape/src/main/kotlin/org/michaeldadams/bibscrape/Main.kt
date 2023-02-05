package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexPerson
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.arguments.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.parameters.groups.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.parameters.options.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.parameters.types.* // ktlint-disable no-wildcard-imports
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.net.URI
import java.nio.file.Path
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Names as N

/** Runs the main entry point of the application. */
fun main(args: Array<String>): Unit = Main().main(args)

// TODO: require Unit type on all functions

/** Returns the class of the entry point of the application. */
@Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
val mainClass: Class<*> = object {}.javaClass.enclosingClass

// The following enum values are lowercase because their names are used by the CLI
// TODO: make CLI lowercase the enum constants

/** What type of ISBN or ISSN media type to prefer. */
@Suppress("EnumNaming", "ENUM_VALUE", "BRACES_BLOCK_STRUCTURE_ERROR")
enum class MediaType { print, online, both } // ktlint-disable enum-entry-name-case

/** What type of ISBN to prefer. */
@Suppress("EnumNaming", "ENUM_VALUE", "BRACES_BLOCK_STRUCTURE_ERROR")
enum class IsbnType { isbn13, isbn10, preserve } // ktlint-disable enum-entry-name-case

/** Option group controlling configuration inputs. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class Inputs : OptionGroup(name = "INPUTS") {
  val key: List<List<String>> by option(
    "-k",
    "--key",
    help = """
      Keys to use in the output BibTeX.

      Successive keys are used for successive BibTeX entries.

      If omitted or an empty string, the key will be copied from the existing
      BibTeX entry or automatically generated if there is no existing BibTeX
      entry.
      """
  ).split(",".r).multiple()

  val names: List<List<Path>> by option(
    help = """
      The names files to use.
      See the NAMES FILES and LIST FLAGS sections for details.
      The file name "." means "names.cfg" in the user-configuration directory.
      """
  ).path(mustExist = true, canBeDir = false).split(";".r).multiple()
  // TODO: default to "." and support "."

  val name: List<List<String>> by option(
    help = """
      Treat <Str> as if it were the content of a names file.
      See the NAMES FILES section for details about names files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).split(";".r).multiple()

  val nouns: List<List<Path>> by option(
    help = """
      The nouns files to use.
      See the NOUNS FILES and LIST FLAGS sections for details.
      The file name "." means "nouns.cfg" in the user-configuration directory.
      """
  ).path(mustExist = true, canBeDir = false).split(";".r).multiple()
  // TODO: default to "." and support "."

  val noun: List<List<String>> by option(
    help = """
      Treat <Str> as if it were the content of a nouns file.
      See the NOUNS FILES section for details about nouns files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).split(";".r).multiple()

  val stopWords: List<List<Path>> by option(
    help = """
      The nouns files to use.
      See the STOP-WORDS FILES and LIST FLAGS sections for details.
      The file name "." means "stop-words.cfg" in the user-configuration directory.
      """
  ).path(mustExist = true, canBeDir = false).split(";".r).multiple()
  // TODO: default to "." and support "."

  val stopWord: List<List<String>> by option(
    help = """
      Treat <Str> as if it were the content of a stop-words file.
      See the STOP-WORDS FILES section for details about stop-words files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).split(";".r).multiple()

  companion object {
    fun parseBlocks(string: String): List<List<String>> {
      var blocks: List<List<String>> = emptyList()
      var block: List<String> = emptyList()

      val lines = string.split("\\R".r)
      for (line in lines) {
        if (line.matches("^ \\s* $".r)) {
          blocks += listOf(block)
          block = emptyList()
        }

        block += line.replace("# .*".r, "").replace("\\s+$".r, "")
      }

      blocks += listOf(block)

      return blocks.filter { it.isNotEmpty() }
    }

    fun <A, B, C> blocksToMap(makeKey: (A) -> B, makeValue: (A) -> C):
      (List<List<A>>, Map<B, C>) -> Map<B, C> = {
      blocks, initialMap ->
        var map: Map<B, C> = initialMap
        for (block in blocks) {
          val value = makeValue(block.first())
          for (key in block.map(makeKey)) {
            map += key to value
          }
        }
        map
      }

    fun <B, C> parseBlocksToMap(makeKey: (String) -> B, makeValue: (String) -> C): (String, Map<B, C>) -> Map<B, C> =
      { string, initialMap -> blocksToMap(makeKey, makeValue)(parseBlocks(string), initialMap) }

    val parseNames: (String, Map<String, BibtexPerson>) -> Map<String, BibtexPerson> =
      parseBlocksToMap({ N.simpleName(N.bibtexPerson(it, "TODO")).lowercase() }, { N.bibtexPerson(it, "TODO") })

    val parseNouns: (String, Map<String, String>) -> Map<String, String> =
      parseBlocksToMap({ it.lowercase() }, { it })

    val parseStopWords: (String, Set<String>) -> Set<String> =
      { string, initialSet -> initialSet + parseBlocks(string).flatten().map { it.lowercase() }.toSet() }
  }
}

/** Option group controlling what major modes of work is actually done. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class OperatingModes : OptionGroup(name = "OPERATING MODES") {
  val init: Boolean by option(
    help = """
      Create default names and nouns files in the user-configuration directory.
      """
  ).flag("--no-init")

  val configDir: Boolean by option(
    help = """
      Print the location of the user-configuration directory.
      """
  ).flag("--no-config-dir")

  val scrape: Boolean by option(
    "-S",
    "--scrape",
    help = """
      Scrape BibTeX entries from publisher's pages.
      """
  ).flag("-/S", "--no-scrape", default = true)

  val fix: Boolean by option(
    "-F",
    "--fix",
    help = """
      Fix mistakes found in BibTeX entries.
      """
  ).flag("+F", "--no-fix", default = true)
}

/** Option group controlling miscellaneous general settings. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class GeneralOptions : OptionGroup(name = "GENERAL OPTIONS") {
  val window: Boolean by option(
    "-w",
    "--window",
    help = """
      Show the browser window while scraping.  This is useful for debugging or
      determining why BibScrape hangs on a particular publisher's page.
      """
  ).flag("--no-window")

  @Suppress("MagicNumber", "MAGIC_NUMBER")
  val timeout: Double by option(
    "-t",
    "--timeout",
    help = """
      Browser timeout in seconds for individual page loads.
      """
  ).double().restrictTo(min = 0.0).default(60.0)

  val escapeAcronyms: Boolean by option(
    help = """
      In BibTeX titles, enclose detected acronyms (e.g., sequences of two or more
      uppercase letters) in braces so that BibTeX preserves their case.
      """
  ).flag("--no-escape-acronyms")

  val issnMedia: MediaType by option(
    help = mediaHelpString("ISSN")
  ).enum<MediaType>().default(MediaType.both)

  val isbnMedia: MediaType by option(
    help = mediaHelpString("ISBN")
  ).enum<MediaType>().default(MediaType.both)

  val isbnType: IsbnType by option(
    help = """
      Whether to convert ISBNs to ISBN-13 or ISBN-10.

      ```
      - If <IsbnType> is "isbn13", always convert ISBNs to ISBN-13.
      - If <IsbnType> is "isbn10", convert ISBNs to ISBN-10 but only if possible.
      - If <IsbnType> is "preserve", do not convert ISBNs.
      ```
      """
  ).enum<IsbnType>().default(IsbnType.preserve)

  val isbnSep: String by option(
    help = """
      The string to separate parts of an ISBN.
      Hyphen and space are the most common.
      Use an empty string to specify no separator.
      """
  ).default("-")

  // TODO: verbose flag
  // TODO: flag to force fresh key generation

  private fun mediaHelpString(name: String): String = """
    Whether to use print or online ${name}s.

    ```
    - If <MediaType> is "print", use only the print ${name}.
    - If <MediaType> is "online", use only the online ${name}.
    - If <MediaType> is "both", use both the print and online ${name}s.
    ```

    If only one type of ${name} is available, this option is ignored.
    """
}

/** Option group controlling BibTeX fields. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class BibtexFieldOptions : OptionGroup(name = "BIBTEX FIELD OPTIONS") {
  val field: List<String> = listOf(
    F.KEY, F.AUTHOR, F.EDITOR, F.AFFILIATION, F.TITLE,
    F.HOWPUBLISHED, F.BOOKTITLE, F.JOURNAL, F.VOLUME, F.NUMBER, F.SERIES,
    F.TYPE, F.SCHOOL, F.INSTITUTION, F.LOCATION, F.CONFERENCE_DATE,
    F.CHAPTER, F.PAGES, F.ARTICLENO, F.NUMPAGES,
    F.EDITION, F.DAY, F.MONTH, F.YEAR, F.ISSUE_DATE,
    F.ORGANIZATION, F.PUBLISHER, F.ADDRESS,
    F.LANGUAGE, F.ISBN, F.ISSN, F.DOI, F.URL, F.EPRINT, F.ARCHIVEPREFIX, F.PRIMARYCLASS,
    F.BIB_SCRAPE_URL,
    F.NOTE, F.ANNOTE, F.KEYWORDS, F.ABSTRACT
  )

  // Str:D :f(:@field) = Array[Str:D](<...>) but Sep[','],
  // #={The order that fields should placed in the output.}

  val noEncode: List<String> = listOf(F.DOI, F.URL, F.EPRINT, F.BIB_SCRAPE_URL)
  // Str:D :@no-encode = Array[Str:D](<...>) but Sep[','],
  // #={Fields that should not be LaTeX encoded.}

  val noCollapse: List<String> = listOf()
  // Str:D :@no-collapse = Array[Str:D](< >) but Sep[','],
  // #={Fields that should not have multiple successive whitespaces collapsed into a
  // single whitespace.}

  val omit: List<String> = listOf()
  // Str:D :o(:@omit) = Array[Str:D](< >) but Sep[','],
  // #={Fields that should be omitted from the output.}

  val omitEmpty: List<String> = listOf(F.ABSTRACT, F.ISSN, F.DOI, F.KEYWORDS)
  // Str:D :@omit-empty = Array[Str:D](<...>) but Sep[','],
  // #={Fields that should be omitted from the output if they are empty.}
}

/** Option group for running self tests. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class TestingOptions : OptionGroup(name = "TESTING OPTIONS") {
  val test: Boolean by option(
    helpTags = mapOf(HelpFormatter.Tags.REQUIRED to ""),
    help = """
      TODO
      // # This script is a test driver for bibscrape.
      // # To run it do:
      // #
      // #     $ ./test.sh <flag> ... <filename> ...
      // #
      // # where <flag> is a flag to pass to bibscrape and <filename> is the name of a
      // # test file. The flags end at the first argument to not start with `-` or after
      // # a `--` argument.
      // #
      // # For example, to run all ACM tests while showing the browser window, do:
      // #
      // #     $ ./test.sh --window tests/acm-*.t
      """
  ).flag()

  val testUrl: Boolean by option(
    help = """
      TODO: document --test-url
      """
  ).flag("--no-test-url", default = true)

  val testFilename: Boolean by option(
    help = """
      TODO: document --test-filename
      """
  ).flag("--no-test-filename", default = true)

  val testNonscraping: Boolean by option(
    help = """
      TODO: document --test-nonscraping
      """
  ).flag("--no-test-nonscraping", default = true)

  val retries: Int by option(
    help = """
      TODO: document --test-retries
      TODO: 0 means infinite (dangerous)
      """
  ).int().restrictTo(min = 0).default(1)

  val testTimeout: Double by option(
    help = """
      TODO: document --test-timeout
      TODO: 0 means infinite (dangerous)
      """
  ).double().restrictTo(min = 0.0).default(60.0)

  val useTestArg: Boolean by option(
    hidden = true,
    help = """
      TODO: document --use-test-arg
      """
  ).flag()

  val testArg: List<String> by option(
    hidden = true,
    help = """
      TODO: document --test-arg
      """
  ).multiple()
}

/** The main application. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class Main : CliktCommand(
  name = "bibscrape",
  printHelpOnEmptyArgs = true,
  help = """
    Collect BibTeX entries from the websites of academic publishers.

    See https://github.com/adamsmd/BibScrape/README.md for more details.
    """,
  epilog = """
    ```
    ------------------------
    BOOLEAN FLAGS
    ------------------------
    ```

    Use --flag, --flag=true, --flag=yes, --flag=y, --flag=on or --flag=1
    to set a boolean flag to True.

    Use --/flag, --flag=false, --flag=no, --flag=n, --flag=off or --flag=0
    to set a boolean flag to False.

    Arguments to boolean flags (e.g., 'true', 'yes', etc.) are case insensitive.

    ```
    ------------------------
    LIST FLAGS
    ------------------------
    ```

    Use --flag=<value> to add a value to a list flag.

    Use --/flag=<value> to remove a value from a list flag.

    Use --flag= to set a list flag to an empty list.

    Use --/flag= to set a list flag to its default list.

    ```
    ------------------------
    NAMES
    ------------------------
    ```

    BibScrape warns the user about author and editor names that publishers often get
    wrong.  For example, some publisher assume the last name of Simon Peyton Jones
    is "Jones" when it should be "Peyton Jones", and some publishers put author
    names in all upper case (e.g., "CONNOR MCBRIDE").

    We call these names "possibly incorrect", not because they are wrong but because
    the user should double check them.

    The only names we do not consider possibly incorrect are those in the names
    files (see the NAMES FILE section) or those that consist of a first name,
    optional middle initial and last name in any of the following formats:

    First name:

    ```
     - Xxxx
     - Xxxx-Xxxx
     - Xxxx-xxxx
     - XxxxXxxx
    ```

    Middle initial:

    ```
     - X.
    ```

    Last name:

    ```
     - Xxxx
     - Xxxx-Xxxx
     - d'Xxxx
     - D'Xxxx
     - deXxxx
     - DeXxxx
     - DiXxxx
     - DuXxxx
     - LaXxxx
     - LeXxxx
     - MacXxxx
     - McXxxx
     - O'Xxxx
     - VanXxxx
    ```

    This collection of name formats was chosen based the list of all authors in
    DBLP and tries to strike a ballance between names that publishers are unlikely
    to get wrong and prompting the user about too many names.

    ```
    ------------------------
    NAMES FILES
    ------------------------
    ```

    Names files specify the correct form for author names.

    Names files are plain text in Unicode format.
    Anything after # (hash) is a comment.
    Blank or whitespace-only lines separate blocks, and
    blocks consist of one or more lines.
    The first line in a block is the canonical/correct form for a name.
    Lines other than the first one are aliases that should be converted to the
    canonical form.

    When searching for a name, case distinctions and divisions of the name into
    parts (e.g., first versus last name) are ignored as publishers often get these
    wrong (e.g., "Van Noort" will match "van Noort" and "Jones, Simon Peyton" will
    match "Peyton Jones, Simon").

    The default names file provides several examples with comments and recommended
    practices.

    ```
    ------------------------
    NOUNS FILES
    ------------------------
    ```

    Nouns files specify words in titles that should be wrapped in curly braces so
    that BibTeX does not convert them to lowercase.

    Nouns files are plain text in Unicode format.
    Anything after # (hash) is a comment.
    Blank or whitespace-only lines separate blocks, and
    blocks consist of one or more lines.
    The first line in a block is the canonical/correct form for a noun.
    Typically, this first line includes curly braces,
    which tell BibTeX to not change the capitalization the text wrapped by the curly braces.
    Lines other than the first one are aliases that should be converted to the canonical form.

    Lines (including the first line) match both with and without the curly braces in them.
    Matching is case sensitive.

    The default nouns file provides several examples with comments and recommended
    practices.

    ```
    ------------------------
    STOP-WORDS FILES
    ------------------------
    ```

    Stop-words files specify words in titles that should be skipped when generating
    BibTeX keys.

    Stop-words files are plain text in Unicode format.
    Anything after # (hash) is a comment.
    Blank or whitespace-only lines are ignored.

    Each line represents one word.
    Matching is case insensitive.
    """
) {
  init {
    // TODO: Better placement of the default in the help text
    context { helpFormatter = CliktHelpFormatter(showDefaultValues = true) }
    versionOption(BuildInformation.version)
    completionOption(help = "Generate an autocomplete script for the given shell")
    // TODO: -? => help
  }

  // TODO: allow argument in option groups
  // TODO: allow 'object : OptionGroup' syntax
  // TODO: allow rename "Arguments" section
  // TODO: allow + ++ and list actions
  val arg: List<String> by argument(
    help = """
      The publisher's pages to be scraped or a BibTeX files to be read and
      re-scraped or fixed.

      ```
      - If an <arg> starts with 'http:' or 'https:', it is interpreted as a URL.
      - If an <arg> starts with 'doi:', it is interpreted as a DOI.
      - If an <arg> is '-', BibTeX entries are read from standard input.
      - Otherwise, an <arg> is a filename from which BibTeX entries are read.
      ```
      """
  ).multiple()

  // Option groups
  val inputs by Inputs()
  val operatingModes by OperatingModes()
  val generalOptions by GeneralOptions()
  val bibtexFieldOptions by BibtexFieldOptions()
  val testingOptions by TestingOptions().cooccurring()

  override fun run() {
    when {
      testingOptions == null -> run(arg)
      testingOptions!!.useTestArg -> {
        println("test ${testingOptions!!.testArg}")
        // run(testingOptions!!.testArg)
      }
      else -> runTests()
    }
  }

  fun run(args: List<String>) {
    //   my IO::Path:D $config-dir-path =
    //   ($*DISTRO.is-win
    //     ?? %*ENV<APPDATA> // %*ENV<USERPROFILE> ~ </AppData/Roaming/>
    //     !! %*ENV<XDG_CONFIG_HOME> // %*ENV<HOME> ~ </.config>).IO
    //     .add(<BibScrape>);
    // my Str:D constant $names-filename = 'names.cfg';
    // my Str:D constant $nouns-filename = 'nouns.cfg';
    // my Str:D constant $stop-words-filename = 'stop-words.cfg';

    // if $config-dir {
    //   say "User-configuration directory: $config-dir-path";
    // }

    // if $init {
    //   $config-dir-path.mkdir;
    //   for ($names-filename, $nouns-filename, $stop-words-filename) -> Str:D $src {
    //     my IO::Path:D $dst = $config-dir-path.add($src);
    //     if $dst.e {
    //       say "Not copying default $src since $dst already exists";
    //     } else {
    //       %?RESOURCES{$src}.copy($dst);
    //       say "Successfully copied default $src to $dst";
    //     }
    //   }
    // }

    // sub default-file(Str:D $type, Str:D $file --> Callable[IO::Path:D]) {
    //   sub (IO::Path:D $x --> IO::Path:D) {
    //     if $x ne '.' {
    //       $x
    //     } else {
    //       my IO::Path:D $io = $config-dir-path.add($file);
    //       if !$io.IO.e {
    //         die "$type file does not exist: $file.  Invoke bibscrape with --init to automatically create it.";
    //       }
    //       $io
    //     }
    //   }
    // }
    var key: List<String> = inputs.key.flatten()
    val names: List<List<String>> = listOf()
    // @names = @names.map(default-file('Names', $names-filename));
    val nouns: List<List<String>> = listOf()
    // @nouns = @nouns.map(default-file('Nouns', $nouns-filename));
    val stopWords: List<String> = listOf()
    // @stop-words = @stop-words.map(default-file('Stop-words', $stop-words-filename));

    val fixer = Fixer(
      names = emptyMap(),
      nouns = emptyMap(),
      stopWords = emptySet(),
      escapeAcronyms = generalOptions.escapeAcronyms,
      issnMedia = generalOptions.issnMedia,
      isbnMedia = generalOptions.isbnMedia,
      isbnType = generalOptions.isbnType,
      isbnSep = generalOptions.isbnSep,
      noEncode = bibtexFieldOptions.noEncode,
      noCollapse = bibtexFieldOptions.noCollapse,
      omit = bibtexFieldOptions.omit,
      omitEmpty = bibtexFieldOptions.omitEmpty
    )

    val printer = BibtexPrinter(bibtexFieldOptions.field)

    val keepScrapedKey = false
    val keepReadKey = true
    for (a in args) {
      fun scrape(url: String): BibtexEntry =
        Scraper.scrape(URI(url.replace("^ doi: \\s*".ri, "")), generalOptions.window, generalOptions.timeout)

      fun fix(keepKey: Boolean, entry: BibtexEntry) {
        println(entry)
        val newEntry = if (operatingModes.fix) fixer.fix(entry) else entry // TODO: clone?
        // TODO: setEntryKey lower cases but BibtexEntry() does not
        // TODO: don't keepKey when scraping

        newEntry.entryKey = key.firstOrNull() ?: if (keepKey) entry.entryKey else newEntry.entryKey
        key = key.drop(1)
        // if (key != null) { e.entryKey = key }
        printer.print(System.out, newEntry)
        // sub fix(Str:D $key, BibScrape::BibTeX::Entry:D $entry is copy --> Any:U) {
        //   if $fix { $entry = $fixer.fix($entry) }
        //   if $key { $entry.key = $key }
        //   print $entry.Str;
        //   return;
        // }
      }

      if (a.contains("^ http: | https: | doi: ".ri)) {
        // if $arg ~~ m:i/^ 'http:' | 'https:' | 'doi:' / {
        // It's a URL
        if (!operatingModes.scrape) { TODO("Scraping disabled but given URL: ${a}") }
        fix(keepScrapedKey, scrape(a))
        println()
      } else {
        // Not a URL so try reading it as a file
        val entries =
          (if (a == "-") InputStreamReader(System.`in`) else FileReader(a))
            .use(Bibtex::parse)
            .entries
        // my Str:D $str = ($arg eq '-' ?? $*IN !! $arg.IO).slurp;
        // my BibScrape::BibTeX::Database:D $bibtex = bibtex-parse($str);

        ENTRY@for (entry in entries) {
          if (entry !is BibtexEntry) {
            println(entry)
            //     ITEM: for $bibtex.items -> BibScrape::BibTeX::Item:D $item {
            //       if $item !~~ BibScrape::BibTeX::Entry:D {
            //         print $item.Str;
          } else if (!operatingModes.scrape) {
            //       } else {
            //         my $key = @key.shift || $item.key;
            // if !$scrape {
            // Undo any encoding that could get double encoded
            //   update($item, 'abstract', { s:g/ \s* "\{\\par}" \s* /\n\n/; }); # Must be before tex2unicode
            //   for $item.fields.keys -> Str:D $field {
            //     unless $field ∈ @no-encode {
            //       update($item, $field, { $_ = tex2unicode($_) });
            //       update($item, $field, { $_ = encode-entities($_); s:g/ '&#' (\d+) ';'/{$0.chr}/; });
            //     }
            //   }
            //   update($item, 'title', { s:g/ '{' (\d* [<upper> \d*] ** 2..*) '}' /$0/ });
            //   update($item, 'series', { s:g/ '~' / / });
            //   fix($key, $item);
            // fix(keepReadKey, entry)
          } else if (entry[F.BIB_SCRAPE_URL] != null) {
            // TODO: entry.ifField
            // } elsif $item.fields<bib_scrape_url> {
            fix(keepReadKey, scrape(entry[F.BIB_SCRAPE_URL]!!.string))
            //   fix($key, scr($item.fields<bib_scrape_url>.simple-str));
          } else if (entry[F.DOI] != null) {
            // TODO: entry.ifField
            // } elsif $item.fields<doi> {
            val doi = entry[F.DOI]!!.string
            //   my Str:D $doi = $item.fields<doi>.simple-str;
            //   $doi = "doi:$doi"
            //     unless $doi ~~ m:i/^ 'doi:' /;
            val prefixedDoi =
              if (doi.startsWith("doi:", ignoreCase = true)) doi else "doi:${doi}"
            //   fix($key, scr($doi));
            fix(keepReadKey, scrape(prefixedDoi))
          } else {
            // } else {
            for (field in listOf(F.URL, F.HOWPUBLISHED)) {
              // for <url howpublished> -> Str:D $field {
              val newEntry = entry.ifField(field) {
                runCatching { scrape(it.string) }.getOrNull()
              }
              //   // Intentionally ignore if this fails
              //   try { scrape(it.string) } catch (_: Throwable) { null }
              // }
              if (newEntry != null) {
                fix(keepReadKey, newEntry)
                continue@ENTRY
              }
              // if (entry.contains(field) {
              // entry.ifField(field) { // TODO: doesn't work due to break and continue
              //   next unless $item.fields{$field}:exists;
              // val newEntry = try {
              //   scrape(it.string)
              // } catch (_: Throwable) {
              //   // Intentionally ignore if this fails
              //   continue
              // }
              // fix(keepReadKey, newEntry)
              // continue@ENTRY
              //   my Str:D $value = $item.fields{$field}.simple-str;
              //   if $value ~~ m:i/^ 'doi:' | 'http' 's'? '://' 'dx.'? 'doi.org/' / {
              //     fix($key, scr($value));
              //     next ITEM;
              //   }
            }
            // }
            println("WARNING: Not changing entry '${entry.entryKey}' because could not find publisher URL")
            println(entry)
            //
            // say "WARNING: Not changing entry '{$item.key}' because could not find publisher URL";
            // print $item.Str;
          }
        }
        // val scrapedBibtex = Scrape.scrape(a, generalOptions.window, generalOptions.timeout)
        // val fixedBibtex = fixer.fix(scrapedBibtex)
        // println(fixedBibtex)
        // TODO: use URI in more places
      }
    }
  }

  fun runTests() {
    val javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
    val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    val classpath = System.getProperty("java.class.path")
    val mainClassName = mainClass.name
    val originalArgv = this.currentContext.originalArgv
    // val command =
    //   listOf(javaExe) +
    //   jvmArgs +
    //   listOf("-classpath", classpath, mainClassName, "--use-test-arg", "--test-arg", a) +
    //   originalArgv

    // "javaExe jvmFlags -classpath $classpath $mainClass $args --use-test-arg --test-arg $a"
    // // this::class.package.mainKt
    // println("javaBin: $javaBin")
    // println("classpath: $classpath")

    // // if test $# -eq 0; then
    // //   echo "ERROR: No test files specified"
    // //   exit 1
    // // fi

    // var processBuilder = ProcessBuilder
    // for (a in arg) {
    // }

    // COUNT=0
    // if test 0 -eq "$NO_URL"; then
    //   run test-url 'URLs' "$@"
    //   COUNT=$((COUNT+n))
    // fi
    // if test 0 -eq "$NO_FILENAME"; then
    //   run test-filename 'filenames' "$@"
    //   COUNT=$((COUNT+n))
    // fi
    // if test 0 -eq "$NO_WITHOUT_SCRAPING"; then
    //   run test-without-scraping 'filenames without scraping' "$@"
    //   COUNT=$((COUNT+n))
    // fi

    // setup() {
    //   fail() {
    //     echo "EXITED ABNORMALLY: $i using a $type"
    //     exit 1
    //   }
    //   trap fail EXIT
    //   # These variables are for use by the calling function
    //   type="$1"
    //   i="$2"
    //   FLAGS=$(head -n 2 "$i" | tail -1)
    // }

    // teardown() {
    //   err="$?"
    //   trap - EXIT
    //   return $err
    // }

    // test-url() {
    //   setup 'URL' "$@"
    //   (head -n 3 "$i"; eval "$BIBSCRAPE" $FLAGS "${GLOBAL_FLAGS[@]}" "\"$(head -n 1 "$i")\"" 2>&1) \
    //     | diff --unified --label "$i using a $type" "$i" - | wdiff -dt
    //   teardown
    // }

    // test-filename() {
    //   setup 'filename' "$@"
    //   eval "$BIBSCRAPE" $FLAGS "${GLOBAL_FLAGS[@]}" <(grep -v '^WARNING: ' "$i") 2>&1 \
    //     | diff --unified --label "$i using a $type" "$i" - | wdiff -dt
    //   teardown
    // }

    // test-without-scraping() {
    //   setup 'filename without scraping' "$@"
    //   eval "$BIBSCRAPE" --/scrape $FLAGS "${GLOBAL_FLAGS[@]}" <(grep -v '^WARNING: ' "$i") 2>&1 \
    //     | diff --unified --label "$i using a $type" \
    //         <(grep -v 'WARNING: Oxford imposes rate limiting.' "$i" \
    //           | grep -v 'WARNING: Non-ACM paper at ACM link') - \
    //     | wdiff -dt
    //   teardown
    // }

    // source "$(which env_parallel.bash)"

    // run() {
    //   FUNCTION="$1"; shift
    //   TYPE="$1"; shift
    //   echo "================================================"
    //   echo "Testing $TYPE"
    //   echo "================================================"
    //   echo
    //   # Other `parallel` flags we might use:
    //   #  --progress --eta
    //   #  --dry-run
    //   #  --max-procs 8
    //   #  --keep-order
    //   #  --nice n
    //   #  --quote
    //   #  --no-run-if-empty
    //   #  --shellquote
    //   #  --joblog >(cat)
    //   #  --delay 0.1
    //   #  --jobs n
    //   #  --line-buffer
    //   env_parallel --bar --retries "$RETRIES" --timeout "$TIMEOUT" "$FUNCTION" ::: "$@"
    //   n="$?"
    //   echo
    //   echo "================================================"
    //   if test 0 -eq "$n"; then
    //     echo "All tests passed for $TYPE"
    //   else
    //     echo "$n tests failed for $TYPE"
    //   fi
    //   echo "================================================"
    //   echo
    // }

    // exit "$COUNT"
  }
}

// $ ./gradlew installDist && ./build/install/bibscrape/bin/bibscrape --window 'https://doi.org/10.1145/1863543.1863551'
