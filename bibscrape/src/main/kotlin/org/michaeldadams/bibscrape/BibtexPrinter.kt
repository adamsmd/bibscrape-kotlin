package org.michaeldadams.bibscrape

import bibtex.dom.BibtexEntry
import java.io.PrintStream

/** Printer for [BibtexEntry].
 *
 * @property fields the fields to print in the order in which they should be printed
 */
class BibtexPrinter(val fields: List<String>) {
  /** Prints [entry] to [stream].
   *
   * @param stream the stream to which to print
   * @param entry the entry to print
   */
  fun print(stream: PrintStream, entry: BibtexEntry): Unit {
    stream.println("@${entry.entryType}{${entry.entryKey},")
    for (field in fields) {
      entry[field]?.let {
        stream.println("  ${field} = ${it},")
      }
    }
    stream.println("}")
  }
}
