import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.visitor.GenericVisitorAdapter
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.utils.StringEscapeUtils
import java.io.File
import kotlin.math.sign

/**
 * Finds classes like Observable<T>, Single<T>, Call<T> so that they can be stripped while generating swagger types.
 */
class CommonTypeWrappersLocator(val sourceFilesManager: SourceFilesManager) {
    enum class CommonTypeKind {
        TRANSPARENT_WRAPPER,
        FORCE_SWAGGER_TYPE,
        IGNORE_WHEN_EXTENDED,
        ALIAS_ANNOTATION
    }

    data class Signature(
        val kinds: List<CommonTypeKind>,
        val sourceLibrary: String? = null, // reference only
        val friendlyName: String,
        val unobfuscatedClassName: String = friendlyName,
        val probableRawStrings: Map<String, Double> = mapOf(),
        val rawStringsHeuristicOnly: Map<String, Double> = mapOf(),
        val probableStringLiterals: Map<String, Double> = mapOf(),
        val probableStringLiteralsHeuristicOnly: Map<String, Double> = mapOf(),
        val minHeuristicScore: Double = 20.0,
        val swaggerType: SwaggerSchema.SwaggerType? = null,
        val isInterfaceHeuristicScore: Double = 0.0,
        val hasTypeParametersHeuristicScore: Double = 0.0,
        val isAnnotationHeuristicScore: Double = -20.0,
        val performHeuristicSearch: Boolean = true
    ) {

    }

    class CommonType(
        val signature: Signature,
        val fullyQualifiedNames: MutableList<String> = mutableListOf(),
        val compilationUnits: MutableList<CompilationUnit> = mutableListOf()
    );


    val commonTypes = mutableListOf<CommonType>()

    fun getCommonTypeFQNsByKind(kind: CommonTypeKind): List<String> {
        return commonTypes.filter { it.signature.kinds.contains(kind) }.map { it.fullyQualifiedNames }.flatten()
    }

    fun getSignatureByFQNAndKind(kind: CommonTypeKind, fqn: String): Signature? {
        return commonTypes.find { it.signature.kinds.contains(kind) && it.fullyQualifiedNames.contains(fqn) }?.signature
    }

    private val filePathToMaxHeuristicScoreMap = mutableMapOf<String, Double>()
    private val friendlyNamePlusQNToMaxHeuristicScoreMap = mutableMapOf<String, Double>()


    fun dumpFoundTypes() {
        Log.info("Found the following common types:")
        commonTypes.forEach { ct ->
            println("  " + Log.qualifiedName(ct.signature.sourceLibrary + "." + ct.signature.friendlyName))
            ct.fullyQualifiedNames.forEach {
                println(
                    "    " + Log.qualifiedName(it) + Log.rgb(0, 170, 170) + " Heuristic score: " + Log.rgb(
                        0,
                        170,
                        255
                    ) + friendlyNamePlusQNToMaxHeuristicScoreMap[ct.signature.friendlyName + "." + it]
                )
            }
        }
    }

    fun findCommonTypeWrappers() {
//        CommonTypeWrappersLocator.signatures.forEach { findBySignature(it) }
        val searchStringsBySignature =
            commonTypeSignatures.filter { it.performHeuristicSearch }.associateBy({ it }, { signature ->
                signature.probableRawStrings.filter { kv -> kv.value > 0 }.keys.toList() + signature.probableStringLiterals.filter { kv -> kv.value > 0 /* we want to ignore negative heuristic values */ }
                    .map { kv ->
                        "\"" + StringEscapeUtils.escapeJava(kv.key) + "\""
                    }
            })

        var i = 0
        sourceFilesManager.sourceFilePaths.forEach { filePath ->
            val contents = File(filePath).readText()
            searchStringsBySignature.forEach { kv ->
                kv.value.find { searchStr ->
                    if (contents.contains(searchStr)) {
                        processProbableFile(kv.key, sourceFilesManager.getOrCreateJavaFile(filePath))
                        return@find true
                    }
                    return@find false
                }
            }
            i++
            if (i % 10 == 0 || i >= sourceFilesManager.sourceFilePaths.size - 1) {
                Log.progress(
                    i.toDouble(),
                    sourceFilesManager.sourceFilePaths.size.toDouble(),
                    "Scanning for common types",
                    "files"
                )
            }
        }
        println("")

    }

    private fun processProbableFile(signature: Signature, file: JavaSourceFile) {
        val contents = File(file.filePath).readText()
        var heuristicScore = 0.0
        (signature.probableRawStrings + signature.rawStringsHeuristicOnly).forEach {
            if (contents.contains(it.key)) {
                heuristicScore += it.value
            }
        }
        val ast = file.ast

        val fullyQualifiedNameVisitor = object : GenericVisitorAdapter<String?, Unit?>() {
            override fun visit(n: ClassOrInterfaceDeclaration, arg: Unit?): String? {
                if (n.name.asString() == signature.unobfuscatedClassName) {
                    heuristicScore += 20.0
                }
                heuristicScore += (if (n.isInterface) 1 else -1) * signature.isInterfaceHeuristicScore
                heuristicScore += (if (n.typeParameters.isNonEmpty) 1 else -1) * signature.hasTypeParametersHeuristicScore
                heuristicScore += -signature.isAnnotationHeuristicScore
                val otherRet = super.visit(n, arg)
                if (n.fullyQualifiedName.isPresent) {
                    return n.fullyQualifiedName.get()
                }
                return otherRet
            }

            override fun visit(n: AnnotationDeclaration, arg: Unit?): String? {
                if (n.name.asString() == signature.unobfuscatedClassName) {
                    heuristicScore += 20.0
                }
                heuristicScore += signature.isAnnotationHeuristicScore
                val otherRet = super.visit(n, arg)
                if (n.fullyQualifiedName.isPresent) {
                    return n.fullyQualifiedName.get()
                }
                return otherRet
            }


        }

        val fullyQualifiedName = fullyQualifiedNameVisitor.visit(ast, null) ?: return

        val stringLiteralVisitor = object : VoidVisitorAdapter<Unit?>() {
            override fun visit(n: StringLiteralExpr, arg: Unit?) {
                heuristicScore += signature.probableStringLiterals.getOrDefault(n.value, 0.0)
                heuristicScore += signature.probableStringLiteralsHeuristicOnly.getOrDefault(n.value, 0.0)
                super.visit(n, arg)
            }
        }

        stringLiteralVisitor.visit(ast, null)

        friendlyNamePlusQNToMaxHeuristicScoreMap[signature.friendlyName + "." + fullyQualifiedName] = heuristicScore
        if (filePathToMaxHeuristicScoreMap.getOrDefault(file.filePath, 0.0) > heuristicScore) {
            return
        }
        filePathToMaxHeuristicScoreMap[file.filePath] = heuristicScore;
        if (heuristicScore >= signature.minHeuristicScore) {
            var ct = commonTypes.find { it.signature == signature }
            if (ct == null) {
                ct = CommonType(signature)
                commonTypes.add(ct)
            }
            ct.fullyQualifiedNames.add(fullyQualifiedName)
            ct.compilationUnits.add(ast)
        }
    }

}