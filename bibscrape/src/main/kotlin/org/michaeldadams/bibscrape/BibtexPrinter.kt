package org.michaeldadams.bibscrape

import bibtex.dom.BibtexAbstractValue
import bibtex.dom.BibtexEntry
import bibtex.dom.BibtexFile
import bibtex.dom.BibtexMacroReference
import bibtex.dom.BibtexString
import bibtex.parser.BibtexParser
import java.io.Reader
import java.io.StringReader
import java.io.PrintStream

/** BibTeX utility functions. */
class BibtexPrinter(val fields: List<String>) {
  fun print(stream: PrintStream, entry: BibtexEntry): Unit {
    stream.println("@${entry.entryType}{${entry.entryKey},")
    for (field in fields) {
      entry[field]?.let {
        stream.println("  ${field} = ${it},")
      }
    }
    // for (f in newEntry.fields) {
    //   if (f not in field) {
    //     println("  ${field} = {${newEntry[field]}}")
    //   }
    // }
    stream.println("}")
  }
}
