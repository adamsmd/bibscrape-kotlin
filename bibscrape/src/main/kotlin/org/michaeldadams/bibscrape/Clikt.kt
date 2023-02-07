package org.michaeldadams.bibscrape

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.types.enum

inline fun <reified T : Enum<T>> RawOption.lowercaseEnum(
    ignoreCase: Boolean = true,
    key: (T) -> String = { it.name },
): NullableOption<T, T> = enum<T>(ignoreCase = ignoreCase, key = { key(it).lowercase() })
