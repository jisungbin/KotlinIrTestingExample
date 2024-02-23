@file:Suppress("MemberVisibilityCanBePrivate")

package land.sungbin.example.kotlinirtesting

import land.sungbin.example.kotlinirtesting.facade.SourceFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.util.dump
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

abstract class AbstractIrTransformTest(useFir: Boolean) : AbstractCodegenTest(useFir) {
  @get:Rule
  val classesDirectory: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

  @get:Rule
  val goldenTransformRule = GoldenTransformRule("src/test/resources/golden")

  fun transform(
    @Language("kotlin")
    source: String,
    @Language("kotlin")
    extra: String = "",
    validator: (element: IrElement) -> Unit = {},
    dumpTree: Boolean = false,
    truncateTracingInfoMode: TruncateTracingInfoMode = TruncateTracingInfoMode.TRUNCATE_KEY,
    additionalPaths: List<File> = emptyList(),
  ): String {
    fun IrElement.validate(): IrElement = also(validator)

    val keySet = mutableListOf<Int>()
    val files = listOf(SourceFile("Test.kt", source), SourceFile("Extra.kt", extra))
    val irModule = compileToIr(sourceFiles = files, additionalPaths = additionalPaths)

    val actualTransformed =
      irModule
        .files[0]
        .validate()
        .dumpSrc(useFir)
        .replace('$', '%')
        // replace source keys for start group calls
        .replace(Regex("(%composer\\.start(Restart|Movable|Replaceable)Group\\()-?((0b)?[-\\d]+)")) { match ->
          val stringKey = match.groupValues[3]
          val key = if (stringKey.startsWith("0b")) Integer.parseInt(stringKey.drop(2), 2)
          else stringKey.toInt()
          if (key in keySet) {
            "${match.groupValues[1]}<!DUPLICATE KEY: $key!>"
          } else {
            keySet.add(key)
            "${match.groupValues[1]}<>"
          }
        }
        .replace(Regex("(sourceInformationMarkerStart\\(%composer, )([-\\d]+)")) { match ->
          "${match.groupValues[1]}<>"
        }
        // replace traceEventStart values with a token
        // TODO(174715171): capture actual values for testing
        .replace(Regex("traceEventStart\\(-?\\d+, (%dirty|%changed|-1), (%dirty1|%changed1|-1), (.*)")) { match ->
          when (truncateTracingInfoMode) {
            TruncateTracingInfoMode.TRUNCATE_KEY ->
              "traceEventStart(<>, ${match.groupValues[1]}, ${match.groupValues[2]}, <>)"

            TruncateTracingInfoMode.KEEP_INFO_STRING ->
              "traceEventStart(<>, ${match.groupValues[1]}, ${match.groupValues[2]}, ${match.groupValues[3]}"
          }
        }
        // replace source information with source it references
        .replace(Regex("(%composer\\.start(Restart|Movable|Replaceable)Group\\([^\"\\n]*)\"(.*)\"\\)")) { match ->
          "${match.groupValues[1]}\"${generateSourceInfo(match.groupValues[4], source)}\")"
        }
        .replace(Regex("(sourceInformation(MarkerStart)?\\(.*)\"(.*)\"\\)")) { match ->
          "${match.groupValues[1]}\"${generateSourceInfo(match.groupValues[3], source)}\")"
        }
        .replace(@Suppress("RegExpSimplifiable") Regex("(composableLambda[N]?\\([^\"\\n]*)\"(.*)\"\\)")) { match ->
          "${match.groupValues[1]}\"${generateSourceInfo(match.groupValues[2], source)}\")"
        }
        .replace(Regex(@Suppress("RegExpSimplifiable") "(rememberComposableLambda[N]?)\\((-?\\d+)")) { match ->
          "${match.groupValues[1]}(<>"
        }
        // replace source keys for joinKey calls
        .replace(Regex("(%composer\\.joinKey\\()([-\\d]+)")) { match ->
          "${match.groupValues[1]}<>"
        }
        // composableLambdaInstance(<>, true)
        .replace(Regex("(composableLambdaInstance\\()([-\\d]+, (true|false))")) { match ->
          val callStart = match.groupValues[1]
          val tracked = match.groupValues[3]
          "$callStart<>, $tracked"
        }
        // composableLambda(%composer, <>, true)
        .replace(Regex("(composableLambda\\(%composer,\\s)([-\\d]+)")) { match ->
          "${match.groupValues[1]}<>"
        }
        .trimIndent()
        .trimTrailingWhitespacesAndAddNewlineAtEOF()

    if (dumpTree) println(irModule.dump())

    return actualTransformed
  }

  fun verifyComposeIrTransform(
    @Language("kotlin")
    source: String,
    expectedTransformed: String,
    @Language("kotlin")
    extra: String = "",
    validator: (element: IrElement) -> Unit = {},
    dumpTree: Boolean = false,
    truncateTracingInfoMode: TruncateTracingInfoMode = TruncateTracingInfoMode.TRUNCATE_KEY,
    additionalPaths: List<File> = emptyList(),
  ) {
    val actualTransformed = transform(
      source = source,
      extra = extra,
      validator = validator,
      dumpTree = dumpTree,
      truncateTracingInfoMode = truncateTracingInfoMode,
      additionalPaths = additionalPaths,
    )
    assertEquals(
      /* expected = */
      expectedTransformed
        .trimIndent()
        .trimTrailingWhitespacesAndAddNewlineAtEOF(),
      /* actual = */ actualTransformed,
    )
  }

  fun verifyGoldenComposeIrTransform(
    @Language("kotlin")
    source: String,
    @Language("kotlin")
    extra: String = "",
    validator: (element: IrElement) -> Unit = {},
    dumpTree: Boolean = false,
    truncateTracingInfoMode: TruncateTracingInfoMode = TruncateTracingInfoMode.TRUNCATE_KEY,
    additionalPaths: List<File> = emptyList(),
  ) {
    val actualTransformed = transform(
      source = source,
      extra = extra,
      validator = validator,
      dumpTree = dumpTree,
      truncateTracingInfoMode = truncateTracingInfoMode,
      additionalPaths = additionalPaths,
    )
    goldenTransformRule.verifyGolden(
      GoldenTransformTestInfo(
        source = source.trimIndent().trim(),
        transformed = actualTransformed.trimIndent().trim(),
      ),
    )
  }

  private fun MatchResult.isNumber() = groupValues[1].isNotEmpty()
  private fun MatchResult.number() = groupValues[1].toInt()
  private val MatchResult.text get() = groupValues[0]
  private fun MatchResult.isChar(c: String) = text == c
  private fun MatchResult.isFileName() = groups[4] != null

  private fun generateSourceInfo(sourceInfo: String, source: String): String {
    @Suppress("RegExpSimplifiable")
    val regex = Regex("(\\d+)|([,])|([*])|([:])|C(\\(.*\\))?|L|(P\\(*\\))|@")

    var current = 0
    var currentResult = regex.find(sourceInfo, current)
    var result = ""

    fun next(): MatchResult? {
      currentResult?.let { result ->
        current = result.range.last + 1
        currentResult = result.next()
      }
      return currentResult
    }

    // A location has the format: [<line-number>]['@' <offset> ['L' <length>]]
    // where the named productions are numbers
    fun parseLocation(): String? {
      var mr = currentResult
      if (mr != null && mr.isNumber()) {
        // line number, we ignore the value in during testing.
        mr = next()
      }
      if (mr != null && mr.isChar("@")) {
        // Offset
        mr = next()
        if (mr == null || !mr.isNumber()) return null
        val offset = mr.number()
        mr = next()
        var ellipsis = ""
        val maxFragment = 6
        val rawLength = if (mr != null && mr.isChar("L")) {
          mr = next()
          if (mr == null || !mr.isNumber()) return null
          mr.number().also { next() }
        } else {
          maxFragment
        }
        val eol = source.indexOf('\n', offset).let {
          if (it < 0) source.length else it
        }
        val space = source.indexOf(' ', offset).let {
          if (it < 0) source.length else it
        }
        val maxEnd = offset + maxFragment
        if (eol > maxEnd && space > maxEnd) ellipsis = "..."
        val length = minOf(maxEnd, minOf(offset + rawLength, space, eol)) - offset
        return "<${source.substring(offset, offset + length)}$ellipsis>"
      }
      return null
    }

    while (currentResult != null) {
      val mr = currentResult!!
      if (mr.range.first != current) return "invalid source info at $current: '$sourceInfo'"
      when {
        mr.isNumber() || mr.isChar("@") -> {
          val fragment = parseLocation() ?: return "invalid source info at $current: '$sourceInfo'"
          result += fragment
        }
        mr.isFileName() -> return result + ":" + sourceInfo.substring(mr.range.last + 1)
        else -> {
          result += mr.text
          next()
        }
      }
      require(mr != currentResult) { "regex didn't advance" }
    }
    if (current != sourceInfo.length) return "invalid source info at $current: '$sourceInfo'"
    return result
  }

  enum class TruncateTracingInfoMode {
    TRUNCATE_KEY, // truncates only the `key` parameter
    KEEP_INFO_STRING, // truncates everything except for the `info` string
  }
}

private fun String.trimTrailingWhitespaces(): String =
  split('\n').joinToString(separator = "\n", transform = String::trimEnd)

private fun String.trimTrailingWhitespacesAndAddNewlineAtEOF(): String =
  trimTrailingWhitespaces().let { result ->
    if (result.endsWith("\n")) result else result + "\n"
  }
