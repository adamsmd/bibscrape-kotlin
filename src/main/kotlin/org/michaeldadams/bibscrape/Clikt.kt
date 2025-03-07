/** Additional option types for command-line parsing with Clikt. */

package org.michaeldadams.bibscrape

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.types.enum
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// // Enum flags

/** Converts an option to the values of an enum using the lowercase names of the
 *  enum values.
 *
 * @see com.github.ajalt.clikt.parameters.types.enum
 */
inline fun <reified T : Enum<T>> RawOption.lowercaseEnum(
  ignoreCase: Boolean = true,
  key: (T) -> String = { it.name }
): NullableOption<T, T> =
  this.enum<T>(ignoreCase = ignoreCase, key = { key(it).lowercase() })

// // Duration flags

/** Converts the option values to a [Duration] in seconds. */
fun RawOption.seconds(): OptionWithValues<Duration?, Duration, Duration> =
  convert({ localization.floatMetavar() }) {
    it.toDoubleOrNull()?.seconds ?: throw BadParameterValue(context.localization.floatConversionError(it))
  }

// // Collection flags (e.g., Map, List and Set)
typealias CollectionOption<C> = OptionWithValues<C, String, String>

/** Implements the parsing for [collection].
 *
 * @see collection
 */
private fun <C, F, L> parseBlocks(
  empty: () -> C,
  default: () -> String,
  parseFirst: (String) -> F,
  parseLine: (String) -> L,
  add: (C, L, F) -> C,
  remove: (C, L) -> C
): (C, String) -> C = { initial, string ->
  var acc = initial
  var first: F? = null

  fun go(dir: Path, string: String): Unit {
    val lines = string.lines().map { it.remove("\\s* \\# .* $".r).remove("^ \\s+".r) }
    for (line in lines) {
      when {
        line.isEmpty() -> first = null
        line.startsWith('@') -> dir.resolve(line.substring(1)).let { go(it.parent, String(Files.readAllBytes(it))) }
        line == "-" -> acc = empty()
        line == "--" -> go(dir, default())
        else -> {
          val isRemove = line.startsWith("-")
          val trimmedLine = if (isRemove) line.substring(1) else line
          if (first == null) { first = parseFirst(trimmedLine) }
          val parsedLine = parseLine(trimmedLine)
          acc = if (isRemove) remove(acc, parsedLine) else add(acc, parsedLine, first!!)
        }
      }
    }
  }

  go(Paths.get("."), string)
  acc
}

// TODO: escape in config blocks

// TODO: regex for lines in config blocks
// prelim = @ | - | --
// @ body comment
// - body comment
// - comment
// -- comment
// body comment
// body = (char | string)*
// string = " (non-quote)* "
// non-quote = ... | \" | \\

/** Converts an option to one supporting a collection type in "block" notation.
 *
 * In "block" notation, blank lines separate "blocks" with the first line of a
 * block considered special.
 *
 * Comments start with "#" anywhere in a line.
 * Lines starting with "@" are an include directive relative to the current file.
 * Lines consisting of just "-", reset the collection to an empty value.
 * Lines consisting of just "--", apply the "default" input.
 * Lines starting with "-", remove an element from a collection.
 * Any other lines, add an element to a collection.
 *
 * When at the commandline (but not in a file), ";" is treated as a linebreak.
 *
 * This format may change in the future as there are many edge cases not supported by it.
 *
 * @param C the collection type
 * @param F the type resulting from parsing the first line in a block
 * @param L the type resulting from parsing a line in a block
 * @param empty how to create an empty instance of the collection type
 * @param default the default input to be parsed
 * @param parseFirst how to parse the first line in a block into an [F]
 * @param parseLine how to parse a line in a block into an [L]
 * @param add how to add to a [C] an [L] that is in a block starting with an [F]
 * @param remove how to remove from a [C] an [L]
 * @return the result of converting the option
 */
fun <C, F, L> NullableOption<String, String>.collection(
  empty: () -> C,
  default: () -> String,
  parseFirst: (String) -> F,
  parseLine: (String) -> L,
  add: (C, L, F) -> C,
  remove: (C, L) -> C
): CollectionOption<C> =
  this.transformAll { strings ->
    val parser = parseBlocks(empty, default, parseFirst, parseLine, add, remove)
    strings.map { it.replace(";", "\n") }.fold(parser(empty(), default()), parser)
  }

/** Converts an option to one for [Map<K, V>] using "block" notation.
 *
 * Each represents a mapping from the [K] for that line to the [V] for the first
 * line in a block.  Note that the [K] for the first line also maps to the [V]
 * for the first line.
 *
 * @param K the type of keys in the map
 * @param V the type of values in the map
 * @param default the default input to be parsed
 * @param parseFirst how to parse the first line in a block into a [V]
 * @param parseLine how to parse a line in a block into a [K]
 * @return the result of converting the option
 * @see collection
 */
@Suppress("TYPE_ALIAS")
fun <K, V> NullableOption<String, String>.map(
  default: () -> String,
  parseFirst: (String) -> V,
  parseLine: (String) -> K
): CollectionOption<Map<K, V>> =
  this.collection(::emptyMap, default, parseFirst, parseLine, { a, l, f -> a + (l to f) }, { a, l -> a - l })

/** Converts an option to one for [List<A>] using "block" notation.
 *
 * Each lines adds to the end of the list.  Separation into blocks has no
 * significance.
 *
 * @param A the type of elements in the list
 * @param default the default input to be parsed
 * @param parseLine how to parse a line into an [A]
 * @return the result of converting the option
 * @see collection
 */
fun <A> NullableOption<String, String>.list(
  default: () -> String,
  parseLine: (String) -> A
): CollectionOption<List<A>> =
  this.collection(::emptyList, default, parseLine, parseLine, { a, l, _ -> a + l }, { a, l -> a - l })

/** Converts an option to one for [Set<A>] using "block" notation.
 *
 * Each lines adds to the set.  Separation into blocks has no significance.
 *
 * @param A the type of elements in the set
 * @param default the default input to be parsed
 * @param parseLine how to parse a line into an [A]
 * @return the result of converting the option
 * @see collection
 */
fun <A> NullableOption<String, String>.set(
  default: () -> String,
  parseLine: (String) -> A
): CollectionOption<Set<A>> =
  this.collection(::emptySet, default, parseLine, parseLine, { a, l, _ -> a + l }, { a, l -> a - l })
