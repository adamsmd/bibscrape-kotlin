package org.michaeldadams.bibscrape

/** Conversions from Unicode to LaTeX and back. */
object Unicode {
  /** Largest code point that if not encoded we do not warn about. */
  private const val ASCII_MAX = 0x7F

  /** Constants for [CCC].
   *
   * @see https://www.unicode.org/reports/tr44/#Canonical_Combining_Class_Values
   */
  private const val CCC_ATTACHED_BELOW = 202
  private const val CCC_BELOW = 220
  private const val CCC_ABOVE = 230
  private const val CCC_DOUBLE_ABOVE = 234

  /** Map from code points to combining character class. */
  @Suppress(
    "ktlint:trailing-comma-on-call-site",
    "LONG_NUMERICAL_VALUES_SEPARATED",
    "MAGIC_NUMBER",
    "MagicNumber",
    "VARIABLE_NAME_INCORRECT_FORMAT",
  )
  val CCC: Map<Int, Int> = mapOf(
    0x0300 to CCC_ABOVE,
    0x0301 to CCC_ABOVE,
    0x0302 to CCC_ABOVE,
    0x0303 to CCC_ABOVE,
    0x0304 to CCC_ABOVE,
    0x0305 to CCC_ABOVE,
    0x0306 to CCC_ABOVE,
    0x0307 to CCC_ABOVE,
    0x0308 to CCC_ABOVE,
    0x0309 to CCC_ABOVE,
    0x030a to CCC_ABOVE,
    0x030b to CCC_ABOVE,
    0x030c to CCC_ABOVE,
    0x030d to CCC_ABOVE,
    0x030e to CCC_ABOVE,
    0x030f to CCC_ABOVE,
    0x0311 to CCC_ABOVE,
    0x0323 to CCC_BELOW,
    0x0324 to CCC_BELOW,
    0x0325 to CCC_BELOW,
    0x0326 to CCC_BELOW,
    0x0327 to CCC_ATTACHED_BELOW,
    0x0328 to CCC_ATTACHED_BELOW,
    0x0329 to CCC_BELOW,
    0x032d to CCC_BELOW,
    0x032e to CCC_BELOW,
    0x0330 to CCC_BELOW,
    0x0331 to CCC_BELOW,
    0x0340 to CCC_ABOVE,
    0x0341 to CCC_ABOVE,
    0x0344 to CCC_ABOVE,
    0x20d7 to CCC_ABOVE,
    0x20db to CCC_ABOVE,
    0x20dc to CCC_ABOVE,
  )

  // TODO: these large tables are slow to compile, so load from a resource file
  private fun resourceToCodes(resource: String): Map<Int, String> =
    this::class.java.getResourceAsStream(resource).bufferedReader().readLines()
    .filter { it.isNotBlank() }
    .filter { it.trimStart().startsWith('#') }
    .map {
      val (int, string) = it.split(" ", limit = 2)
      Integer.decode(int) to string
    }.toMap()

  /** Map from code points to LaTeX in a math context.
   * Based on table 131 in the Comprehensive Latex Symbol List.
   */
  @Suppress("VARIABLE_NAME_INCORRECT_FORMAT")
  val MATH: Map<Int, String> = resourceToCodes("/unicode-math.txt")

  /** Map from code point to LaTeX in a non-math context. */
  @Suppress("VARIABLE_NAME_INCORRECT_FORMAT")
  val CODES: Map<Int, String> = resourceToCodes("/unicode-codes.txt")

  /** Converts Unicode to LaTeX.
   *
   * @param string the string to convert
   * @param math whether the string is in a math context
   * @param ignore a predicate indicating characters to not convert
   * @return the result of converting the string
   */
  fun unicodeToTex(string: String, math: Boolean = false, ignore: (Char) -> Boolean = { false }): String {
    val result: MutableList<String> = mutableListOf()
    for (char in string) {
      val ord = char.code
      when {
        ignore(char) -> result += char.toString()
        CCC.containsKey(ord) -> {
          val old = result.removeLastOrNull()
            ?: "{}".also { println("WARNING: Combining character at start of string: %s (U+%04x)".format(char, ord)) }
          val new = CODES[ord]!!.replace("\\{\\}".r, old)
          val fixed =
            if (CCC[ord] in setOf(CCC_ABOVE, CCC_DOUBLE_ABOVE)) new.replace("\\{ ( [ij] ) \\}".r, "{\\$1}") else new
          result += "{${fixed}}"
        }
        math && MATH.containsKey(ord) -> result += "{${MATH[ord]}}"
        CODES.containsKey(ord) -> result += "{${CODES[ord]}}"
        else -> {
          if (ord > ASCII_MAX) { println("WARNING: Unknown Unicode character: %s (U+x%04x)".format(char, ord)) }
          result += char.toString()
        }
      }
    }

    return result
      .joinToString("")
      .replace("\\ + \\{~\\}".r, "{~}") // Trim spaces before NBSP (otherwise they have no effect in LaTeX)
  }

  // # This function doesn't work very well.  It is just good enough for most author names.
  // sub tex2unicode(Str:D $str is copy --> Str:D) is export {
  //   if $str ~~ / '{' / {
  //     for %CODES.pairs.sort(*.value.chars).reverse».key -> Int:D(Str:D) $key {
  //       my Str:D $value = %CODES{$key};
  //       if $value ~~ / "\\" / {
  //         $str ~~ s:g/ '{' $value '}' /{$key.chr}/;
  //       }
  //     }
  //     for %CODES.kv -> Int:D(Str:D) $key, Str:D $value {
  //       if $value !~~ / "\\" / and $value ~~ / <-alpha> / {
  //         $str ~~ s:g/ '{' $value '}' /{$key.chr}/;
  //       }
  //     }
  //   }
  //   $str;
  // }
}
