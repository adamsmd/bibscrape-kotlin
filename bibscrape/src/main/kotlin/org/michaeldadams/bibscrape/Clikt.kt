package org.michaeldadams.bibscrape

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.options.OptionDelegate
import com.github.ajalt.clikt.parameters.options.FlagOption
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parsers.OptionParser.ParseResult
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parsers.*
import kotlin.reflect.KProperty
import kotlin.properties.ReadOnlyProperty
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.github.ajalt.clikt.completion.*
import com.github.ajalt.clikt.parameters.groups.*

// // Enum flags

inline fun <reified T : Enum<T>> RawOption.lowercaseEnum(
  ignoreCase: Boolean = true,
  key: (T) -> String = { it.name }
): NullableOption<T, T> = enum<T>(ignoreCase = ignoreCase, key = { key(it).lowercase() })

// // Boolean flags

fun FlagOption<Boolean>.boolean(): BooleanFlag = BooleanFlag(this)

class BooleanFlag(val that: FlagOption<Boolean>) : OptionDelegate<Boolean> by that {
  override val parser: OptionParser
    get() = BooleanFlagOptionParser

  override fun finalize(context: Context, invocations: List<OptionParser.Invocation>): Unit {
    if (invocations.size > 0) {
      val values = invocations.last().values
      if (values.size == 1) {
        val value = that.transformEnvvar(OptionTransformContext(this, context), values.first())
        val flag = if (value) that.names.first() else that.secondaryNames.first()
        that.finalize(context, listOf(OptionParser.Invocation(flag, emptyList())))
        return
      }
    }
    that.finalize(context, invocations)
  }

  override operator fun provideDelegate(
    thisRef: ParameterHolder,
    prop: KProperty<*>
  ): ReadOnlyProperty<ParameterHolder, Boolean> {
    thisRef.registerOption(this)
    return this
  }
}

object BooleanFlagOptionParser : OptionParser {
  override fun parseLongOpt(
    option: Option,
    name: String,
    argv: List<String>,
    index: Int,
    explicitValue: String?
  ): ParseResult {
    if (explicitValue != null) {
      return ParseResult(1, name, listOf(explicitValue))
    } else {
      return FlagOptionParser.parseLongOpt(option, name, argv, index, explicitValue)
    }
  }

  override fun parseShortOpt(
    option: Option,
    name: String,
    argv: List<String>,
    index: Int,
    optionIndex: Int
  ): ParseResult =
    FlagOptionParser.parseShortOpt(option, name, argv, index, optionIndex) // TODO: use "by" and "super"
}

// // Collection flags (e.g., Map, List and Set)

private fun <A, F, L> parseBlocks(
  empty: () -> A,
  default: () -> String,
  parseFirst: (String) -> F,
  parseLine: (String) -> L,
  add: (A, L, F) -> A,
  remove: (A, L) -> A
): (A, String) -> A = { initial, string ->
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
          val l = parseLine(trimmedLine)
          if (first == null) { first = parseFirst(trimmedLine) }
          acc = if (minus) remove(acc, l) else add(acc, l, first!!)
        }
      }
    }
  }

  go(Paths.get("."), string)
  acc
}

fun <A, F, L> NullableOption<String, String>.collection(
  empty: () -> A,
  default: () -> String,
  parseFirst: (String) -> F,
  parseLine: (String) -> L,
  add: (A, L, F) -> A,
  remove: (A, L) -> A
): OptionWithValues<A, String, String> =
  this.transformAll { strings ->
    val run = parseBlocks(empty, default, parseFirst, parseLine, add, remove)
    strings.map { it.replace(";", "\n") }.fold(run(empty(), default()), run)
  }

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
