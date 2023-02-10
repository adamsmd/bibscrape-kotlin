package org.michaeldadams.bibscrape

//import com.github.ajalt.clikt.parameters.internal.NullableLateinit
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
import com.github.ajalt.clikt.output.HelpFormatter
import kotlin.properties.ReadOnlyProperty
import com.github.ajalt.clikt.completion.*
import com.github.ajalt.clikt.parameters.groups.*

// // Enum flags

inline fun <reified T : Enum<T>> RawOption.lowercaseEnum(
    ignoreCase: Boolean = true,
    key: (T) -> String = { it.name },
): NullableOption<T, T> = enum<T>(ignoreCase = ignoreCase, key = { key(it).lowercase() })

// // Boolean flags

fun FlagOption<Boolean>.boolean(): BooleanFlag = BooleanFlag(this)

class BooleanFlag(val that: FlagOption<Boolean>) : OptionDelegate<Boolean> by that {
  override fun finalize(context: Context, invocations: List<OptionParser.Invocation>) {
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
  override operator fun provideDelegate(thisRef: ParameterHolder, prop: KProperty<*>):
    ReadOnlyProperty<ParameterHolder, Boolean> {
    thisRef.registerOption(this)
    return this
  }

  override val parser: OptionParser
    get() = BooleanFlagOptionParser
}

object BooleanFlagOptionParser : OptionParser {
  override fun parseLongOpt(
    option: Option, name: String, argv: List<String>,
    index: Int, explicitValue: String?,
  ): ParseResult {
    if (explicitValue != null) {
        return ParseResult(1, name, listOf(explicitValue))
    } else {
      return FlagOptionParser.parseLongOpt(option, name, argv, index, explicitValue)
    }
  }

  override fun parseShortOpt(
      option: Option, name: String, argv: List<String>,
      index: Int, optionIndex: Int,
  ): ParseResult {
    return FlagOptionParser.parseShortOpt(option, name, argv, index, optionIndex)
  }
}

// TODO: FileList

// TODO: word lists: -x,x,,.,@file,-@file
// fun OptionWithValues<List<String>>.list(
//   sep: String = ","
// ): OptionsWithValues<List<String>> =
//   this.copy(transformAll = { values ->
//     var results
//   }
//   )

// fun transformallListOptionWithValues(old: List<String>, add: Boolean, arg: String): List<String> {
//   if (arg.startsWith("-"))
// }

// TODO: block lists: john;Johne;;jane;jannet;@file;-john;-@file
// : CallsTransformer<EachT, AllT>, 