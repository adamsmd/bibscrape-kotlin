package org.michaeldadams.bibscrape

import com.github.ajalt.clikt.completion.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.core.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.parameters.groups.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.parameters.options.* // ktlint-disable no-wildcard-imports
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parsers.* // ktlint-disable no-wildcard-imports
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
): NullableOption<T, T> = this.enum<T>(ignoreCase = ignoreCase, key = { key(it).lowercase() })

// // Duration flags

/** Converts the option values to a [Duration] in seconds. */
fun RawOption.seconds(): OptionWithValues<Duration?, Duration, Duration> = convert({ localization.floatMetavar() }) {
  it.toDoubleOrNull()?.seconds ?: throw BadParameterValue(context.localization.floatConversionError(it))
}

// // Collection flags (e.g., Map, List and Set)

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
    val lines = string
      .split("\\R".r)
      .map { it.replace("\\s* \\# .* $".r, "").replace("^ \\s+".r, "") }
    for (line in lines) {
      when {
        line.isEmpty() ->
          first = null
        line.startsWith('@') ->
          dir.resolve(line.substring(1)).let {
            go(it.parent, String(Files.readAllBytes(it)))
          }
        line == "-" ->
          acc = empty()
        line == "--" ->
          go(dir, default())
        else -> {
          val minus = line.startsWith("-")
          val trimmedLine = if (minus) line.substring(1) else line
          if (first == null) { first = parseFirst(trimmedLine) }
          val l = parseLine(trimmedLine)
          acc = if (minus) remove(acc, l) else add(acc, l, first!!)
        }
      }
    }
  }

  go(Paths.get("."), string)
  acc
}

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
): OptionWithValues<C, String, String> =
  this.transformAll { strings ->
    val run = parseBlocks(empty, default, parseFirst, parseLine, add, remove)
    strings.map { it.replace(";", "\n") }.fold(run(empty(), default()), run)
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
fun <K, V> NullableOption<String, String>.map(
  default: () -> String,
  parseFirst: (String) -> V,
  parseLine: (String) -> K
): OptionWithValues<Map<K, V>, String, String> =
  this.collection(
    ::emptyMap,
    default,
    parseFirst,
    parseLine,
    { a, l, f -> a + (l to f) },
    { a, l -> a - l }
  )

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
): OptionWithValues<List<A>, String, String> =
  this.collection(
    ::emptyList,
    default,
    parseLine,
    parseLine,
    { a, l, _ -> a + l },
    { a, l -> a - l }
  )

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
): OptionWithValues<Set<A>, String, String> =
  this.collection(
    ::emptySet,
    default,
    parseLine,
    parseLine,
    { a, l, _ -> a + l },
    { a, l -> a - l }
  )
