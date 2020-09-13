import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.visitor.VoidVisitorAdapter

class ApiInterfaceEndpointExtractor(
    val schema: SwaggerSchema,
    val apiInterfaceFile: JavaSourceFile,
    val retrofitAnnotationLocator: RetrofitAnnotationLocator,
    val typeProcessor: TypeProcessor
) {
    var interfacesCount = 0
    var endpointsCount = 0
    lateinit var interfaceFqn: String
    fun extractEndpoints() {
        val ast = apiInterfaceFile.ast

        object : VoidVisitorAdapter<Unit?>() {
            override fun visit(n: ClassOrInterfaceDeclaration, arg: Unit?) {

                if (n.isInterface) {
                    interfaceFqn = n.fullyQualifiedName.get()
                    innerInterfaceVisitor.visit(n, n.name.identifier)
                    interfacesCount++
                }
                super.visit(n, arg)
            }
        }.visit(ast, null)

    }

    val innerInterfaceVisitor = object : VoidVisitorAdapter<String>() {
        override fun visit(n: MethodDeclaration, ifaceName: String) {


            var methodAnnotation: AnnotationExpr? = null
            val httpMethod = HTTP_METHODS.find {
                val foundAnnotation = containsRetrofitAnnotation(it, n.annotations)
                if (foundAnnotation != null) {
                    methodAnnotation = foundAnnotation
                    return@find true
                }
                return@find false
            } ?: return

            endpointsCount++
            val operation = SwaggerSchema.PathOperation(
                summary = ifaceName + "." + n.name.identifier,
                xRetrofitInterface = interfaceFqn
            )

            val rawUrl = extractValueStringArgumentFromAnnotation(methodAnnotation!!)
            if (rawUrl == null) {
                Log.warn("Failed to extract endpoint url at %s".format(operation.summary))
                return
            }
            val url = Util.prependSlashIfNeeded(rawUrl)

            filterRetrofitAnnotations("Headers", n.annotations).forEach { headersAnnotation ->
                extractValueArrayStringsArgumentFromAnnotation(headersAnnotation)?.map { hdr ->
                    hdr.split(
                        ":",
                        limit = 2
                    )
                }?.filter { pair -> pair.size >= 1 }?.forEach {
                    operation.parameters.add(
                        SwaggerSchema.PathOperation.Parameter(
                            name = it.first(),
                            paramIn = SwaggerSchema.PathOperation.Parameter.ParameterPlace.Header,
                            required = true,
                            description = "Static header added by @Headers({...})",
                            schema = SwaggerSchema.SwaggerType(type = "string", example = it.getOrNull(1)?.trimStart())
                        )
                    )
                }

            }

            if (containsRetrofitAnnotation("Mutlipart", n.annotations) != null) {
                //TODO: add multipart support
            }
            endpointMethodDeclarationVisitor.visit(n, operation)

            operation.responses["default"] = SwaggerSchema.PathOperation.ResponseBody(
                description = "Response body type was declared as `%s`".format(n.type),
                content = SwaggerSchema.PathOperation.MediaType(
                    json = SwaggerSchema.PathOperation.MediaType.MediaTypeInner(
                        schema = typeProcessor.getTypeInPlace(n.type)
                    )
                )
            )

            schema.paths.getOrPut(
                url,
                { mutableMapOf<String, SwaggerSchema.PathOperation>() })[httpMethod.toLowerCase()] = operation


        }
    }

    val endpointMethodDeclarationVisitor = object : VoidVisitorAdapter<SwaggerSchema.PathOperation>() {
        override fun visit(n: Parameter, operation: SwaggerSchema.PathOperation) {
            n.annotations.forEach { annotationExpr ->
                when {
                    isRetrofitAnnotation("Path", annotationExpr) -> {
                        operation.parameters.add(
                            SwaggerSchema.PathOperation.Parameter(
                                name = extractValueStringArgumentFromAnnotation(annotationExpr) ?: return@forEach,
                                paramIn = SwaggerSchema.PathOperation.Parameter.ParameterPlace.Path,
                                required = true,
                                description = operation.summary + "." + n.name.asString(),
                                schema = typeProcessor.getTypeInPlace(n.type),
                            )
                        )
                    }
                    isRetrofitAnnotation("Query", annotationExpr) -> {
                        operation.parameters.add(
                            SwaggerSchema.PathOperation.Parameter(
                                name = extractValueStringArgumentFromAnnotation(annotationExpr) ?: return@forEach,
                                paramIn = SwaggerSchema.PathOperation.Parameter.ParameterPlace.Query,
                                required = false,
                                description = operation.summary + "." + n.name.asString(),
                                schema = typeProcessor.getTypeInPlace(n.type),
                            )
                        )
                    }
                    isRetrofitAnnotation("Header", annotationExpr) -> {
                        operation.parameters.add(
                            SwaggerSchema.PathOperation.Parameter(
                                name = extractValueStringArgumentFromAnnotation(annotationExpr) ?: return@forEach,
                                paramIn = SwaggerSchema.PathOperation.Parameter.ParameterPlace.Header,
                                required = false,
                                description = operation.summary + "." + n.name.asString(),
                                schema = typeProcessor.getTypeInPlace(n.type),
                            )
                        )
                    }
                    isRetrofitAnnotation("Body", annotationExpr) -> {

                        operation.requestBody = SwaggerSchema.PathOperation.RequestBody(
                            SwaggerSchema.PathOperation.MediaType(
                                SwaggerSchema.PathOperation.MediaType.MediaTypeInner(
                                    schema = typeProcessor.getTypeInPlace(
                                        n.type
                                    )
                                )
                            )
                        )
                    }
                }
            }
//            super.visit(n, operation) don't descend lower
        }
    }


    fun containsRetrofitAnnotation(annoName: String, annotations: List<AnnotationExpr>): AnnotationExpr? {
        return annotations.find { annotationExpr ->
            if (retrofitAnnotationLocator.annotationToSymbolsMap.getOrElse(annoName, { listOf() }).any {
                    return@any annotationExpr.resolve().qualifiedName == it.describe()
                }) {
                return@find true
            }
            return@find false
        }
    }

    fun filterRetrofitAnnotations(annoName: String, annotations: List<AnnotationExpr>): List<AnnotationExpr> {
        return annotations.filter { annotationExpr ->
            if (retrofitAnnotationLocator.annotationToSymbolsMap.getOrElse(annoName, { listOf() }).any {
                    return@any annotationExpr.resolve().qualifiedName == it.describe()
                }) {
                return@filter true
            }
            return@filter false
        }
    }

    fun isRetrofitAnnotation(annotationName: String, annotation: AnnotationExpr): Boolean {
        return retrofitAnnotationLocator.annotationToSymbolsMap.getOrElse(annotationName, { listOf() }).any {
            return@any annotation.resolve().qualifiedName == it.describe()
        }
    }

    fun extractValueStringArgumentFromAnnotation(annotation: AnnotationExpr): String? {
        val memberValue = AnnotationUntil.extractValueExprFromAnnotation(annotation)
        if (memberValue !is StringLiteralExpr) {
            Log.warn(
                "Found an annotation whose member is not a StringLiteralExpr: %s".format(
                    annotation.toString()
                )
            )
            return null
        }
        return memberValue.asString()
    }

    fun extractValueArrayStringsArgumentFromAnnotation(annotation: AnnotationExpr): List<String>? {
        val memberValue = AnnotationUntil.extractValueExprFromAnnotation(annotation)
        if (memberValue !is ArrayInitializerExpr) {
            Log.warn(memberValue?.javaClass?.canonicalName)
            Log.warn(
                "Found an annotation whose member is not a ArrayInitializerExpr: %s".format(
                    annotation.toString()
                )
            )
            return null
        }
        return memberValue.values.mapNotNull { arrVal ->
            if (arrVal !is StringLiteralExpr) {
                Log.warn(
                    "Found an annotation whose array value is not a StringLiteralExpr: %s".format(
                        annotation.toString()
                    )
                )
                return@mapNotNull null
            }
            return@mapNotNull arrVal.asString()
        }

    }


}

