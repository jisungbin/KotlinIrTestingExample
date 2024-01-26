package land.sungbin.example.kotlinirtesting

import java.io.File
import land.sungbin.example.kotlinirtesting.facade.SourceFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.codegen.GeneratedClassLoader

var uniqueNumber = 0

// TODO
abstract class AbstractCodegenTest(useFir: Boolean) : AbstractCompilerTest(useFir) {
  private fun dumpClasses(loader: GeneratedClassLoader) {
    for (
    file in loader.allGeneratedFiles.filter {
      it.relativePath.endsWith(".class")
    }
    ) {
      println("------\nFILE: ${file.relativePath}\n------")
      println(file.asText())
    }
  }

  protected fun validateBytecode(
    @Language("kotlin")
    src: String,
    dumpClasses: Boolean = false,
    validate: (String) -> Unit,
  ) {
    val className = "Test_REPLACEME_${uniqueNumber++}"
    val fileName = "$className.kt"

    val loader = classLoader(
      """
           @file:OptIn(
             InternalComposeApi::class,
           )
           package test

           import androidx.compose.runtime.*

           $src

            fun used(x: Any?) {}
        """,
      fileName, dumpClasses
    )

    val apiString = loader
      .allGeneratedFiles
      .filter { it.relativePath.endsWith(".class") }.joinToString("\n") {
        it.asText().replace('$', '%').replace(className, "Test")
      }

    validate(apiString)
  }

  protected fun classLoader(
    @Language("kotlin")
    source: String,
    fileName: String,
    dumpClasses: Boolean = false,
  ): GeneratedClassLoader {
    val loader = createClassLoader(listOf(SourceFile(fileName, source)))
    if (dumpClasses) dumpClasses(loader)
    return loader
  }

  protected fun classLoader(
    sources: Map<String, String>,
    dumpClasses: Boolean = false,
  ): GeneratedClassLoader {
    val loader = createClassLoader(
      sources.map { (fileName, source) -> SourceFile(fileName, source) }
    )
    if (dumpClasses) dumpClasses(loader)
    return loader
  }

  protected fun classLoader(
    platformSources: Map<String, String>,
    commonSources: Map<String, String>,
    dumpClasses: Boolean = false,
  ): GeneratedClassLoader {
    val loader = createClassLoader(
      platformSources.map { (fileName, source) -> SourceFile(fileName, source) },
      commonSources.map { (fileName, source) -> SourceFile(fileName, source) }
    )
    if (dumpClasses) dumpClasses(loader)
    return loader
  }

  protected fun classLoader(
    sources: Map<String, String>,
    additionalPaths: List<File>,
    dumpClasses: Boolean = false,
    forcedFirSetting: Boolean? = null,
  ): GeneratedClassLoader {
    val loader = createClassLoader(
      sources.map { (fileName, source) -> SourceFile(fileName, source) },
      additionalPaths = additionalPaths,
      forcedFirSetting = forcedFirSetting
    )
    if (dumpClasses) dumpClasses(loader)
    return loader
  }

  protected fun testCompile(@Language("kotlin") source: String, dumpClasses: Boolean = false) {
    classLoader(source, "Test.kt", dumpClasses)
  }

  protected val COMPOSE_VIEW_STUBS_IMPORTS = """
        import android.view.View
        import android.widget.TextView
        import android.widget.Button
        import android.view.Gravity
        import android.widget.LinearLayout
        import androidx.compose.runtime.Composable
    """.trimIndent()

  protected val COMPOSE_VIEW_STUBS = """
        @Composable
        fun TextView(
            id: Int = 0,
            gravity: Int = Gravity.TOP or Gravity.START,
            text: String = "",
            onClick: (() -> Unit)? = null,
            onClickListener: View.OnClickListener? = null
        ) {
            emitView(::TextView) {
                if (id != 0) it.id = id
                it.text = text
                it.gravity = gravity
                if (onClickListener != null) it.setOnClickListener(onClickListener)
                if (onClick != null) it.setOnClickListener(View.OnClickListener { onClick() })
            }
        }

        @Composable
        fun Button(
            id: Int = 0,
            text: String = "",
            onClick: (() -> Unit)? = null,
            onClickListener: View.OnClickListener? = null
        ) {
            emitView(::Button) {
                if (id != 0) it.id = id
                it.text = text
                if (onClickListener != null) it.setOnClickListener(onClickListener)
                if (onClick != null) it.setOnClickListener(View.OnClickListener { onClick() })
            }
        }

        @Composable
        fun LinearLayout(
            id: Int = 0,
            orientation: Int = LinearLayout.VERTICAL,
            onClickListener: View.OnClickListener? = null,
            content: @Composable () -> Unit
        ) {
            emitView(
                ::LinearLayout,
                {
                    if (id != 0) it.id = id
                    if (onClickListener != null) it.setOnClickListener(onClickListener)
                    it.orientation = orientation
                },
                content
            )
        }
    """.trimIndent()

  protected fun testCompileWithViewStubs(source: String, dumpClasses: Boolean = false) =
    testCompile(
      """
            $COMPOSE_VIEW_STUBS_IMPORTS

            $source

            $COMPOSE_VIEW_STUBS
        """,
      dumpClasses
    )
}