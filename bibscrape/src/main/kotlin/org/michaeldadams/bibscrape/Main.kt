/** Main entry point for bibscrape. */

package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexPerson
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.michaeldadams.bibscrape.Bibtex.Fields as F
import org.michaeldadams.bibscrape.Bibtex.Names as N

/** Runs the main entry point of the application. */
fun main(args: Array<String>): Unit = Main().main(args)

/** Returns the class of the entry point of the application. */
@Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
val mainClass: Class<*> = object {}.javaClass.enclosingClass

/** Option group controlling configuration inputs. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class Inputs : OptionGroup(name = "INPUTS") {
  val key: List<String> by option(
    "-k",
    "--key",
    help = """
      Keys to use in the output BibTeX.

      Successive keys are used for successive BibTeX entries.

      If omitted or an empty string, the key will be copied from the existing
      BibTeX entry or automatically generated if there is no existing BibTeX
      entry.
      """
  ).list({ "" }, { it })

  val name: Map<String, BibtexPerson> by option(
    help = """
      Treat <Str> as if it were the content of a names file.
      See the NAMES FILES section for details about names files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).map(
    userConfig(NAMES_FILENAME),
    { N.bibtexPerson(it, "bibscrape name configuration") },
    { N.simpleName(N.bibtexPerson(it, "bibscrape name configuration")).lowercase() }
  )

  val noun: Map<String, String> by option(
    help = """
      Treat <Str> as if it were the content of a nouns file.
      See the NOUNS FILES section for details about nouns files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).map(userConfig(NOUNS_FILENAME), { it }, { it })

  val stopWord: Set<String> by option(
    help = """
      Treat <Str> as if it were the content of a stop-words file.
      See the STOP-WORDS FILES section for details about stop-words files.
      Semicolons in <Str> are interpreted as newlines.
      """
  ).set(userConfig(STOP_WORDS_FILENAME), { it.lowercase() })

  companion object {
    private val windows = File.separator == "\\" // TODO: apache for isWindows and/or userconfigdir
    private val userConfigDir =
      if (windows) {
        System.getenv("APPDATA") ?: System.getenv("APPDATA") + "/AppData/Roaming"
      } else {
        System.getenv("XDG_CONFIG_HOME") ?: System.getenv("HOME") + "/.config"
      }
    val bibscrapeConfigDir = File(userConfigDir + "/bibscrape")

    const val NAMES_FILENAME = "names.cfg"
    const val NOUNS_FILENAME = "nouns.cfg"
    const val STOP_WORDS_FILENAME = "stop-words.cfg"

    /** Read the contents of [filename] in [bibscrapeConfigDir] if it exists or from the Java resources in the app.
     *
     * @param filename the name of the file to read
     * @return the contents of the file
     */
    fun userConfig(filename: String): () -> String = {
      val file = bibscrapeConfigDir.resolve(filename)
      if (file.exists()) file.readText() else Inputs::class.java.getResource("/${filename}").readText()
    }
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

  val printConfigDir: Boolean by option(
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

  // val examplez by this.option( // TODO: remove
  //   "-x",
  //   "--xx",
  //   )
  //   .flag("+x",
  //   "++xx")
  //   .boolean()
  //   // override val parser: OptionParser = FFlagOptionParser

  val fix: Boolean by option(
    "-F",
    "--fix",
    help = """
      Fix mistakes found in BibTeX entries.
      """
  ).flag("--no-fix", default = true)
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

  val verbose: Boolean by option(
    "-v",
    "--verbose",
    help = """
      TODO: help test for verbose
      """
  ).flag("--no-verbose")

  @Suppress("MAGIC_NUMBER")
  val timeout: Duration by option(
    "-t",
    "--timeout",
    help = """
      Browser timeout in seconds for individual page loads.
      """
  ).seconds().restrictTo(min = 0.0.seconds).default(60.0.seconds)

  val escapeAcronyms: Boolean by option(
    help = """
      In BibTeX titles, enclose detected acronyms (e.g., sequences of two or more
      uppercase letters) in braces so that BibTeX preserves their case.
      """
  ).flag("--no-escape-acronyms", default = true)

  val isbnMedia: MediaType by option(
    help = mediaHelpString("ISBN")
  ).lowercaseEnum<MediaType>().default(MediaType.BOTH)

  val isbnType: IsbnType by option(
    help = """
      Whether to convert ISBNs to ISBN-13 or ISBN-10.

      ```
      - If <IsbnType> is "isbn13", always convert ISBNs to ISBN-13.
      - If <IsbnType> is "isbn10", convert ISBNs to ISBN-10 but only if possible.
      - If <IsbnType> is "preserve", do not convert ISBNs.
      ```
      """
  ).lowercaseEnum<IsbnType>().default(IsbnType.PRESERVE)

  val isbnSep: String by option(
    help = sepHelpString("ISBN")
  ).default("-")

  val issnMedia: MediaType by option(
    help = mediaHelpString("ISSN")
  ).lowercaseEnum<MediaType>().default(MediaType.BOTH)

  val issnSep: String by option(
    help = sepHelpString("ISSN")
  ).default("-")

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

  private fun sepHelpString(name: String): String = """
    The string to separate parts of an ${name}.
    Hyphen and space are the most common.
    Use an empty string to specify no separator.
    """
}

/** Option group controlling BibTeX fields. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class BibtexFieldOptions : OptionGroup(name = "BIBTEX FIELD OPTIONS") {
  val field: List<String> by option(
    help = """The order that fields should placed in the output."""
  ).list(
    {
      @Suppress("ktlint:trailing-comma-on-call-site")
      listOf(
        F.KEY, F.AUTHOR, F.EDITOR, F.AFFILIATION, F.TITLE,
        F.HOWPUBLISHED, F.BOOKTITLE, F.JOURNAL, F.VOLUME, F.NUMBER, F.SERIES,
        F.TYPE, F.SCHOOL, F.INSTITUTION, F.LOCATION, F.CONFERENCE_DATE,
        F.CHAPTER, F.PAGES, F.ARTICLENO, F.NUMPAGES,
        F.EDITION, F.DAY, F.MONTH, F.YEAR, F.ISSUE_DATE,
        F.ORGANIZATION, F.PUBLISHER, F.ADDRESS,
        F.LANGUAGE, F.ISBN, F.ISSN, F.DOI, F.URL, F.EPRINT, F.ARCHIVEPREFIX, F.PRIMARYCLASS,
        F.BIB_SCRAPE_URL,
        F.NOTE, F.ANNOTE, F.KEYWORDS, F.ABSTRACT,
      ).joinToString("\n")
    },
    { it } // TODO: lowercase?
  )

  val noEncode: Set<String> by option(
    help = """Fields that should not be LaTeX encoded."""
  ).set(
    { setOf(F.DOI, F.URL, F.EPRINT, F.BIB_SCRAPE_URL).joinToString("\n") },
    { it } // TODO: lowercase?
  )

  val noCollapse: Set<String> by option(
    help = """Fields that should not have multiple successive whitespaces collapsed into a single whitespace."""
  ).set({ "" }, { it }) // TODO: lowercase?

  val omit: Set<String> by option(
    help = """Fields that should be omitted from the output."""
  ).set({ "" }, { it }) // TODO: lowercase?

  val omitEmpty: Set<String> by option(
    help = """Fields that should be omitted from the output if they are empty."""
  ).set(
    { listOf(F.ABSTRACT, F.ISSN, F.DOI, F.KEYWORDS).joinToString("\n") },
    { it } // TODO: lowercase?
  )
}

/** Option group for running self tests. */
@Suppress("TrimMultilineRawString", "UndocumentedPublicProperty", "MISSING_KDOC_CLASS_ELEMENTS")
class TestingOptions : OptionGroup(name = "TESTING OPTIONS") {
  val test: Boolean by option(
    helpTags = mapOf(HelpFormatter.Tags.REQUIRED to ""),
    help = """
      Run in testing mode where each ARG is treated as a test file.
      """
  ).flag()

  val testUrl: Boolean by option(
    help = """
      Test the use of an explicit URL as input
      """
  ).flag("--no-test-url", default = true)

  val testFilename: Boolean by option(
    help = """
      Test the use of a BibTeX file as input
      """
  ).flag("--no-test-filename", default = true)

  val testNonscraping: Boolean by option(
    help = """
      Test the use of fixing without scraping
      """
  ).flag("--no-test-nonscraping", default = true)

  val retries: Int by option(
    help = """
      How many times to retry a test. A value of zero retries infinite times.
      """
  ).int().restrictTo(min = 0).default(1)

  @Suppress("MAGIC_NUMBER")
  val testTimeout: Duration by option(
    help = """
      TODO: document --test-timeout
      TODO: 0 means infinite (dangerous)
      """
  ).seconds().restrictTo(min = 0.0.seconds).default(60.0.seconds)

  @Suppress("MAGIC_NUMBER")
  val testHardTimeout: Duration by option(
    help = """
      TODO: document --test-timeout
      TODO: 0 means infinite (dangerous)
      """
  ).seconds().restrictTo(min = 0.0.seconds).default(60.0.seconds)

  val useTestArg: Boolean by option(
    hidden = true,
    help = """
      (Internal option used by testing.) Use the values passed to --use-test-arg in place of ARG.
      """
  ).flag()

  val testArg: List<String> by option(
    hidden = true,
    help = """
      (Internal option used by testing.) Value to use in place of ARG.
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

    Use --flag, --flag=true, --flag=t, --flag=1, --flag=yes, --flag=y or --flag=on
    to set a boolean flag to True.

    Use --/flag, --flag=false, --flag=t, --flag=0, --flag=no, --flag=n or --flag=off
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

  override fun run(): Unit {
    // TODO: warn if no args
    val options = testingOptions
    when {
      options == null -> runBibscrape(arg)
      options.useTestArg -> runBibscrape(options.testArg)
      else -> runTests(options)
    }
  }

  /** Runs the main (i.e., non-test) code for scraping.
   *
   * @param args the files or URLs to operate on
   */
  fun runBibscrape(args: List<String>): Unit {
    if (operatingModes.printConfigDir) { println("User-configuration directory: ${Inputs.bibscrapeConfigDir}") }

    if (operatingModes.init) {
      Inputs.bibscrapeConfigDir.mkdirs()
      for (src in listOf(Inputs.NAMES_FILENAME, Inputs.NOUNS_FILENAME, Inputs.STOP_WORDS_FILENAME)) {
        val dst = Inputs.bibscrapeConfigDir.resolve(src)
        if (dst.exists()) {
          println("Not copying default ${src} since ${dst} already exists")
        } else {
          dst.writeText(javaClass.getResource("/${src}").readText())
          println("Copied default ${src} to ${dst}")
        }
      }
    }

    var key: List<String> = inputs.key

    val fixer = Fixer(
      names = inputs.name,
      nouns = inputs.noun,
      stopWords = inputs.stopWord,
      escapeAcronyms = generalOptions.escapeAcronyms,
      isbnMedia = generalOptions.isbnMedia,
      isbnType = generalOptions.isbnType,
      isbnSep = generalOptions.isbnSep,
      issnMedia = generalOptions.issnMedia,
      issnSep = generalOptions.issnSep,
      noEncode = bibtexFieldOptions.noEncode,
      noCollapse = bibtexFieldOptions.noCollapse,
      omit = bibtexFieldOptions.omit,
      omitEmpty = bibtexFieldOptions.omitEmpty
    )

    val printer = Bibtex.Printer(bibtexFieldOptions.field)

    val keepScrapedKey = false // TODO: as flag
    val keepReadKey = true // TODO: as flag
    for (a in args) {
      fun scrape(url: String): BibtexEntry =
        Scraper.scrape(url, generalOptions.window, generalOptions.verbose, generalOptions.timeout)

      fun fix(keepKey: Boolean, readKey: String?, entry: BibtexEntry): Unit {
        val newEntry = if (operatingModes.fix) fixer.fix(entry) else entry.clone()
        // TODO: setEntryKey lower cases but BibtexEntry() does not
        // TODO: don't keepKey when scraping
        newEntry.entryKey = key.firstOrNull() ?: if (keepKey) readKey ?: entry.entryKey else newEntry.entryKey
        key = key.drop(1) // TODO: dropFirst?
        printer.print(System.out, newEntry)
      }

      if (a.contains("^ http: | https: | doi: ".ri)) {
        // Perform scraping if it is a URL
        if (!operatingModes.scrape) { error("Scraping disabled but given URL: ${a}") }
        fix(keepScrapedKey, null, scrape(a))
      } else {
        // Not a URL so try reading it as a file
        val reader = if (a == "-") InputStreamReader(System.`in`) else FileReader(a)
        val entries = reader.use(Bibtex::parse).entries

        entries.forEach entry@{ entry ->
          if (entry !is BibtexEntry) {
            printer.print(System.out, entry)
          } else if (!operatingModes.scrape) {
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
            fix(keepReadKey, entry.entryKey, entry)
          } else {
            entry[F.BIB_SCRAPE_URL]?.let {
              fix(keepReadKey, entry.entryKey, scrape(it.string))
            } ?: entry[F.DOI]?.let {
              val doi = "doi:${it.string.remove("^ doi:".r)}"
              fix(keepReadKey, entry.entryKey, scrape(doi))
            } ?: run {
              for (field in listOf(F.URL, F.HOWPUBLISHED)) {
                entry[field]?.let {
                  runCatching { scrape(it.string) }.getOrNull() // Intentionally ignore if scrape fails
                }?.let {
                  fix(keepReadKey, entry.entryKey, it)
                  return@entry
                }
              }

              println("WARNING: Not changing entry '${entry.entryKey}' because could not find URL for the paper")
              println(entry)
            }
          }
        }
        // TODO: use URI in more places
      }
    }
  }

  /** Runs a program with a timeout and capturing its output to a string.
   *
   * @param timeout time to wait for the program to end before calling `destroy()` on it
   * @param hardTimeout time to wait for the program to end after calling
   *   `destroy()` before calling `destroyForcibly()` on it
   * @param command the command and its arguments
   * @return a pair of the output from the program (including both stdout and stderr) and its [Process]
   */
  fun runCommand(timeout: Duration, hardTimeout: Duration, command: List<String>): Pair<String, Process> {
    val process = ProcessBuilder(command)
      .redirectErrorStream(true)
      .start()
    var output: AtomicReference<String?> = AtomicReference()
    var reader = thread { output.set(String(process.inputStream.readAllBytes())) }
    if (!process.waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)) {
      process.destroy()
      if (!process.waitFor(hardTimeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)) {
        process.destroyForcibly()
      }
    }
    process.waitFor()
    reader.join()

    // println("EXITED ABNORMALLY: $i using a $type")
    if (process.exitValue() != 0) { println("EXITED ABNORMALLY ${process.exitValue()}") }
    // TODO: destroy process children

    // var endMillis = System.currentTimeMillis() + 60_000
    // while (process.isRunning && System.currentTimeMillis() < endMillis) {
    //   Thread.sleep(10)
    // }
    // if (process.isRunning) { process.stop() }
    // reader.join()
    // if (reader.isAlive) { reader.interrupt() }
    // val isAlive = reader.isAlive
    // try {
    //   reader.join(60_000)
    // } catch {
    // process.waitFor(60, TimeUnit.SECONDS)

    return Pair(output.get()!!, process)
  }

  /** Run bibscrape in testing mode.
   *
   * @param options the testing options
   */
  fun runTests(options: TestingOptions): Unit {
    val javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
    val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    val classpath = System.getProperty("java.class.path")
    val mainClassName = mainClass.name
    val originalArgv = this.currentContext.originalArgv

    // if test $# -eq 0; then
    //   echo "ERROR: No test files specified"
    //   exit 1
    // fi

    for (a in arg) {
      println("****************************************************************")
      println("** Testing ${a}")
      println("****************************************************************")
      val lines = File(a).readLines()

      val url = lines[0]
      // val comment = lines[1]
      val endOfFlags = 2 + lines.drop(2).indexOfFirst { it.contains("^ \\s* $".r) }
      val flags = lines.subList(2, endOfFlags)
      val expected = lines.subList(endOfFlags + 1, lines.size).joinToString("\n", postfix = "\n")

      val command =
        listOf(javaExe) +
          jvmArgs +
          listOf("-classpath", classpath, mainClassName, "--use-test-arg", "--test-arg", url) +
          flags +
          originalArgv

      val retries = if (options.retries == 0) -1 else options.retries
      val result = retry(retries, { it.second.exitValue() == 0 }) {
        runCommand(options.testTimeout, options.testHardTimeout, command)
      }

      if (result.second.exitValue() != 0) {
        for (line in expected.lines()) {
          println("[~~${line}~~]")
        }
        for (line in result.first.lines().orEmpty()) {
          println("[++${line}++]")
        }
      } else {
        val diffRowGenerator = DiffRowGenerator
          .create()
          .showInlineDiffs(true) // Use word diffs
          .ignoreWhiteSpaces(false) // Default
          .reportLinesUnchanged(false) // Default
          .oldTag { f -> if (f) "[~~" else "~~]" }
          .newTag { f -> if (f) "[++" else "++]" }
          .processDiffs(null) // No extra diff processing. Default
          .columnWidth(0) // No line wrapping. Default
          .mergeOriginalRevised(true) // Show diffs inline instead of two column
          .decompressDeltas(true) // Default
          .inlineDiffByWord(true) // Use char-gradularity "word" diffs.
          // .inlineDiffBySplitter() // Handled by inlineDiffByWord()
          .lineNormalizer { it } // Don't muck with the inputs
          // .equalizer() // Handled by ignoreWhiteSpaces()
          .replaceOriginalLinefeedInChangesWithSpaces(false) // Default
          .build()
        val diffRows = diffRowGenerator.generateDiffRows(expected.lines(), result.first.lines().orEmpty())
        for (row in diffRows) {
          // TODO: || show-all-lines
          if (row.tag != DiffRow.Tag.EQUAL) { println(row.oldLine) }
        }
      }
    }

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
