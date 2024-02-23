package land.sungbin.example.kotlinirtesting.test

import land.sungbin.example.kotlinirtesting.AbstractCompilerTest
import land.sungbin.example.kotlinirtesting.facade.SourceFile
import org.jetbrains.kotlin.ir.util.dump
import org.junit.Test

class CompilerTest(useFir: Boolean) : AbstractCompilerTest(useFir = useFir) {
  @Test fun test() {
    val ir = compileToIr(
      sourceFiles = listOf(
        SourceFile(
          name = "main.kt",
          source = """
          fun main() {
            println("Hello, world!")
          }
          """.trimIndent(),
        )
      ),
    )
    println(ir.dump())
  }
}
