import java.io.File
import java.util.*
import kotlin.collections.ArrayList


class SourceFilesManager {
    var sourceFilePaths: MutableList<String> = ArrayList()
    var javaFileCache: MutableMap<String, JavaSourceFile> = mutableMapOf()

    fun loadFilePaths(dir: String) {
        File(dir).walk()
            .filter { it.isFile }
            .filter { it.name.endsWith(".java") }
            .forEach { sourceFilePaths.add(it.absolutePath) }
        Log.info("Detected %d java files".format(sourceFilePaths.size))
    }

    fun getOrCreateJavaFile(path: String): JavaSourceFile {
        return javaFileCache.getOrPut(path, { JavaSourceFile(path) })
    }

    fun getFilePathWithStringInside(searchStr: String): JavaSourceFile? {
        val path = sourceFilePaths.find {
            File(it).readText().contains(searchStr)
        } ?: return null
        return getOrCreateJavaFile(path)
    }

    fun getFilesWithStringsInside(searchStr: List<String>): List<JavaSourceFile> {
        return sourceFilePaths.filter {
            searchStr.any { str ->
                File(it).readText().contains(str)
            }
        }.map { getOrCreateJavaFile(it) }

    }

    fun getFilesWithImports(importedPackages: List<String>): List<JavaSourceFile> {
        return sourceFilePaths.filter { filePath ->
            val scan = Scanner(File(filePath))
            while (scan.hasNextLine()) {
                val line = scan.nextLine()

                if (importedPackages.any { line.contains("import $it;") }) {

                    return@filter true
                }
                if (line.startsWith("public class")) {
                    break
                }
                if (line.startsWith("public interface")) {
                    break
                }
            }
            return@filter false
        }.map { getOrCreateJavaFile(it) }
    }
}