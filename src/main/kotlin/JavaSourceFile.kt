import com.github.javaparser.ParseProblemException
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import java.io.File

class JavaSourceFile(val filePath: String) {
    private var innerAst: CompilationUnit? = null
    val ast: CompilationUnit
        get() {
            performParse()
            return this!!.innerAst!!
        }

    private fun performParse() {
        try {
            innerAst = StaticJavaParser.parse(File(filePath));
        } catch (e: ParseProblemException) {
            Log.error("Error while parsing %s: %s".format(filePath, e))
            throw e
        }

    }
}