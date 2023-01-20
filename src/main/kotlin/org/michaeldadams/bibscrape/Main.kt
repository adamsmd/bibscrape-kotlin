package org.michaeldadams.bibscrape

import org.openqa.selenium.firefox.FirefoxDriver

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import java.nio.file.Path

fun main(args: Array<String>): Unit = Main().main(args)

class Inputs : OptionGroup(name = "INPUTS") {

  val key: List<List<String>> by option(
    "-k", "--key",
    help = """
      Keys to use in the output BibTeX.

      Successive keys are used for successive BibTeX entries.

      If omitted or an empty string, the key will be copied from the existing
      BibTeX entry or automatically generated if there is no existing BibTeX
      entry.
      """
  ).split(",".toRegex()).multiple()

  val names: List<List<Path>> by option(
    help = """
      The names files to use.
      See the NAMES FILES and LIST FLAGS sections for details.
      The file name "." means "names.cfg" in the user-configuration directory.
      """
  ).path(mustExist = true, canBeDir = false).split(";".toRegex()).multiple()
  // TODO: default to "." and support "."

  val name: List<List<String>> by option(
    help = """
      Treat <Str> as if it were the content of a names file.
      See the NAMES FILES section for details about names files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).split(";".toRegex()).multiple()

  val nouns: List<List<Path>> by option(
    help = """
      The nouns files to use.
      See the NOUNS FILES and LIST FLAGS sections for details.
      The file name "." means "nouns.cfg" in the user-configuration directory.
      """
  ).path(mustExist = true, canBeDir = false).split(";".toRegex()).multiple()
  // TODO: default to "." and support "."

  val noun: List<List<String>> by option(
    help = """
      Treat <Str> as if it were the content of a nouns file.
      See the NOUNS FILES section for details about nouns files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).split(";".toRegex()).multiple()

  val stopWords: List<List<Path>> by option(
    help = """
      The nouns files to use.
      See the STOP-WORDS FILES and LIST FLAGS sections for details.
      The file name "." means "stop-words.cfg" in the user-configuration directory.
      """
  ).path(mustExist = true, canBeDir = false).split(";".toRegex()).multiple()
  // TODO: default to "." and support "."

  val stopWord: List<List<String>> by option(
    help = """
      Treat <Str> as if it were the content of a stop-words file.
      See the STOP-WORDS FILES section for details about stop-words files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).split(";".toRegex()).multiple()
}

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
    "-S", "--scrape",
    help = """
      Scrape BibTeX entries from publisher's pages.
      """
  ).flag("-/S", "--no-scrape", default = true)

  val fix: Boolean by option(
    "-F", "--fix",
    help = """
      Fix mistakes found in BibTeX entries.
      """
  ).flag("+F", "--no-fix", default = true)
}

enum class MediaType { print, online, both }
enum class IsbnType { isbn13, isbn10, preserve }

class GeneralOptions : OptionGroup(name = "GENERAL OPTIONS") {
  fun mediaHelpString(name: String): String = """
    Whether to use print or online ${name}s.

    ```
    - If <MediaType> is "print", use only the print ${name}.
    - If <MediaType> is "online", use only the online ${name}.
    - If <MediaType> is "both", use both the print and online ${name}s.
    ```

    If only one type of ${name} is available, this option is ignored.
    """

  val window: Boolean by option(
    "-w", "--window",
    help = """
      Show the browser window while scraping.  This is useful for debugging or
      determining why BibScrape hangs on a particular publisher's page.
      """
  ).flag("--no-window")

  val timeout: Double by option(
    "-t", "--timeout",
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

// # Haven't found any use for this yet, but leaving it here in case we ever do
// #  Bool:D :v(:$verbose) = False,
// ##={Print verbose output.}

// Bool:D :V(:$version) = False,
// #={Print version information.}

// Bool:D :h(:$help) = False,
// #={Print this usage message.}
}

class BibtexFieldOptions : OptionGroup(name = "BIBTEX FIELD OPTIONS") {

// Str:D :f(:@field) = Array[Str:D](<
//   key author editor affiliation title
//   howpublished booktitle journal volume number series
//   type school institution location conference_date
//   chapter pages articleno numpages
//   edition day month year issue_date
//   organization publisher address
//   language isbn issn doi url eprint archiveprefix primaryclass
//   bib_scrape_url
//   note annote keywords abstract>)
//   but Sep[','],
// #={The order that fields should placed in the output.}

// Str:D :@no-encode = Array[Str:D](<doi url eprint bib_scrape_url>) but Sep[','],
// #={Fields that should not be LaTeX encoded.}

// Str:D :@no-collapse = Array[Str:D](< >) but Sep[','],
// #={Fields that should not have multiple successive whitespaces collapsed into a
// single whitespace.}

// Str:D :o(:@omit) = Array[Str:D](< >) but Sep[','],
// #={Fields that should be omitted from the output.}

// Str:D :@omit-empty = Array[Str:D](<abstract issn doi keywords>) but Sep[','],
// #={Fields that should be omitted from the output if they are empty.}
}

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

  val inputs by Inputs()
  val operatingModes by OperatingModes()
  val generalOptions by GeneralOptions()
  val bibtexFieldOptions by BibtexFieldOptions()

  override fun run() {
    val driver = FirefoxDriver()
    driver.get("https://selenium.dev")
    driver.quit()
  }
}
