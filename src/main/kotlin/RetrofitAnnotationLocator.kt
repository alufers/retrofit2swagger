import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.visitor.GenericVisitorAdapter
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.resolution.UnsolvedSymbolException
import com.github.javaparser.resolution.types.ResolvedType
import java.lang.Exception

val HTTP_METHODS = arrayOf(
    "GET",
    "HEAD",
    "POST",
    "PUT",
    "DELETE",
    "CONNECT",
    "OPTIONS",
    "TRACE",
    "PATCH"
)

val COMMON_HTTP_METHODS = arrayOf(
    "GET",
    "HEAD",
    "POST",
    "PUT",
    "DELETE",
)

/**
 * This class finds the RequestFactory class from retrofit in the decompiled sources by searching for a string.
 * Then it finds the methods which process annotations and resolve the annotation types for later use when decompiling the Api interfaces.
 */
class RetrofitAnnotationLocator(val smf: SourceFilesManager) {
    val annotationToSymbolsMap: MutableMap<String, MutableList<ResolvedType>> = mutableMapOf()


    fun locateRetrofitAnnotations() {
        //TODO: add support for both retrofit 2 and and retrofit 1 (change this to use common types locator)
        val requestFactoryFilePath = smf.getFilesWithStringsInside(arrayListOf("\"Only one encoding annotation is allowed.\"", "\"@Headers annotation is empty.\"")).firstOrNull()
            ?: throw Exception("Failed to locate retrofit RequestFactory")
        val ast = requestFactoryFilePath.ast

        val parseMethodAnnotation = findParseMethodAnnotation(ast)
        findHttpMethodAndHeadersAnnotations(parseMethodAnnotation)
        val parseParameterAnnotation = findParseParameterAnnotation(ast)
        findParameterAnnotations(parseParameterAnnotation)
        Log.info(
            "Found %d retrofit annotations: %s".format(
                annotationToSymbolsMap.keys.size,
                annotationToSymbolsMap.keys.joinToString { it })
        )
        val missingMethods =
            COMMON_HTTP_METHODS.filter {
                !annotationToSymbolsMap.keys.map { annoName -> annoName.toUpperCase() }.contains(it)
            }
        if (missingMethods.isNotEmpty()) {
            Log.warn(
                "The annotations for the following HTTP methods were not found: %s. Extraction may be limited. ".format(
                    missingMethods.joinToString(", ")
                )
            )
        }

    }

    fun printAnnotationMap() {
        annotationToSymbolsMap.forEach {
            println("@%s:".format(it.key))
            it.value.forEach { println("  %s".format(it)) }
        }
    }

    private fun addAnnotationSymbol(annotationName: String, type: ResolvedType) {
        var list = annotationToSymbolsMap[annotationName]
        if (list == null) {
            list = mutableListOf()
            annotationToSymbolsMap[annotationName] = list
        }
        list.add(type)
    }

    val instanceofFinderVisitor = object : GenericVisitorAdapter<InstanceOfExpr?, Unit?>() {
        override fun visit(n: InstanceOfExpr, arg: Unit?): InstanceOfExpr? {
            return n;
        }
    }

    private fun findParameterAnnotations(parseParameterAnnotation: MethodDeclaration) {
        val annotationLiteralSamples = mapOf<String, List<String>>(
            "Path" to listOf<String>("A @Path parameter must not come after a @Query."),
            "QueryMap" to listOf<String>("@QueryMap keys must be of type String: "),
            "Field" to listOf<String>("@Field parameters can only be used with form encoding."),
            "FieldMap" to listOf<String>("@FieldMap parameter type must be Map."),
            "PartMap" to listOf<String>("@PartMap parameter type must be Map."),
            "Part" to listOf<String>("@Part parameters using the MultipartBody.Part must not include a part name in the annotation."),
            "Body" to listOf<String>("Multiple @Body method annotations found."),
            "Header" to listOf<String>(),
            "QueryName" to listOf<String>(),
            "Query" to listOf<String>()
        )
        object : VoidVisitorAdapter<InstanceOfExpr?>() {
            override fun visit(n: IfStmt, arg: InstanceOfExpr?) {
                val foundInstanceOf = n.condition.accept(instanceofFinderVisitor, null)
                if (foundInstanceOf != null) { // find if(xyz instanceof abc) on the top level of the method
                    if (annotationLiteralSamples.any {

                            if (it.key == foundInstanceOf.type.toString()) { // we find it the easy way, just looking for the name, won't work when obfuscated
                                addAnnotationSymbol(it.key, foundInstanceOf.type.resolve())
                                return@any true
                            }
                            return@any false
                        }) {
                        super.visit(n, null)
                        return
                    }

                    super.visit(n, foundInstanceOf)
                    return
                }
                super.visit(n, arg)
            }

            override fun visit(n: StringLiteralExpr?, arg: InstanceOfExpr?) {
                if (arg != null) {


                    annotationLiteralSamples.any {
                        if (it.value.contains(n!!.value) && arg.type!!.toString() != "ParameterizedType") {

//                            println("DDD 2ADDDING " +  )
                            addAnnotationSymbol(it.key, arg.type!!.resolve())
                            return@any true
                        }
                        return@any false
                    }
                }
                super.visit(n, arg)
            }
        }.visit(parseParameterAnnotation, null)
    }

    private fun findHttpMethodAndHeadersAnnotations(parseMethodAnnotationMethod: MethodDeclaration) {
        // locates and extracts expressions like this:   mo40879d("DELETE", ((DELETE) annotation).value(), false);
        object : VoidVisitorAdapter<String?>() {
            override fun visit(n: MethodCallExpr, arg: String?) {
                if (!n.arguments.isEmpty()) {
                    val firstArg = n.arguments.first.get()
                    val firstArgAsString = EvaluatorUtil.evalToString(firstArg)
                    if (firstArgAsString != null && HTTP_METHODS.contains(firstArgAsString.toUpperCase())) {
                        super.visit(
                            n,
                            firstArgAsString
                        ) // go inside of this call, a mapping will be created when we find a Cast
                        return
                    } else if (firstArg.toString() == "HttpRequest.METHOD_OPTIONS") {
                        Log.debug("Detected edge-case for HttpRequest.METHOD_OPTIONS")
                        super.visit(n, "OPTIONS") // go inside of this call
                        return
                    }

                }
                super.visit(n, arg)
            }

            override fun visit(n: VariableDeclarator, arg: String?) {
                if (n.type.toString() == "String[]") {
                    super.visit(n, "Headers")
                    return
                }
                if (n.type.toString() == "HTTP") {
                    super.visit(n, "HTTP")
                    return
                }
                super.visit(n, arg)
            }

            override fun visit(n: CastExpr, arg: String?) {
                if (arg != null) {
                    try {
                        addAnnotationSymbol(arg, n.type.resolve())

                    } catch (e: UnsolvedSymbolException) {
                        Log.warn(
                            "Failed to add annotation symbol %s, may miss some endpoints later on: %s ".format(
                                arg,
                                e.message
                            )
                        )
                    }
                    super.visit(n, null)
                    return
                }
                super.visit(n, arg)
            }

            override fun visit(n: InstanceOfExpr, arg: String?) {
                // TODO: this is a naive way of detecting these annotations and depends on the deobfuscator doing its job.
                if (n.type.toString() == "Multipart") {
                    addAnnotationSymbol("Multipart", n.type.resolve())
                }
                if (n.type.toString() == "FormUrlEncoded") {
                    addAnnotationSymbol("FormUrlEncoded", n.type.resolve())
                }
                super.visit(n, arg)
            }
        }.visit(parseMethodAnnotationMethod, null)
    }

    private fun findParseMethodAnnotation(ast: CompilationUnit): MethodDeclaration {

        val parseMethodAnnotationMethodDecl =
            findMethodWithStringLiterals(ast, arrayListOf("Only one encoding annotation is allowed.", "@Headers annotation is empty."))
                ?: throw Exception("parseMethodAnnotation from retrofit not found")
        Log.info("Found parseMethodAnnotation: %s".format(parseMethodAnnotationMethodDecl!!.declarationAsString))
        return parseMethodAnnotationMethodDecl!!
    }

    private fun findParseParameterAnnotation(ast: CompilationUnit): MethodDeclaration {

        // we want to find a method from retrofit so we can get the FQNs of the method annotations
        val parseParameterAnnotationMethodDecl =
            findMethodWithStringLiteral(ast, "A @Path parameter must not come after a @Query.")
                ?: throw Exception("parseMethodAnnotation method from retrofit not found in class %s. Code too obfuscated?".format(ast.storage.get().path))
        Log.info("Found parseParameterAnnotation: %s".format(parseParameterAnnotationMethodDecl!!.declarationAsString))
        return parseParameterAnnotationMethodDecl!!
    }


    private fun findMethodWithStringLiteral(ast: CompilationUnit, literalValue: String): MethodDeclaration? {
        var theMethod: MethodDeclaration? = null
        object : VoidVisitorAdapter<MethodDeclaration?>() {
            override fun visit(n: MethodDeclaration, d: MethodDeclaration?) {
                super.visit(n, n)
            }

            override fun visit(n: StringLiteralExpr, d: MethodDeclaration?) {
                if (n.value == literalValue) {
                    theMethod = d
                }
                super.visit(n, d)
            }
        }.visit(ast, null)
        return theMethod
    }

    /**
     * Returns the method if it contains eny of the passed strings
     */
    private fun findMethodWithStringLiterals(ast: CompilationUnit, literalValues: List<String>): MethodDeclaration? {
        var theMethod: MethodDeclaration? = null
        object : VoidVisitorAdapter<MethodDeclaration?>() {
            override fun visit(n: MethodDeclaration, d: MethodDeclaration?) {
                super.visit(n, n)
            }

            override fun visit(n: StringLiteralExpr, d: MethodDeclaration?) {
                if (literalValues.contains(n.value)) {
                    theMethod = d
                }
                super.visit(n, d)
            }
        }.visit(ast, null)
        return theMethod
    }
}