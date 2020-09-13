import com.github.javaparser.ast.type.*
import com.github.javaparser.resolution.UnsolvedSymbolException
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserEnumDeclaration
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration
import java.lang.IllegalStateException
import java.util.*

class TypeProcessor(val commonTypeWrappersLocator: CommonTypeWrappersLocator) {
    private val typesToExtract = mutableListOf<SwaggerSchema.SwaggerType.TypeReferenceToResolve>()
    private val allReferences = mutableListOf<SwaggerSchema.SwaggerType.TypeReferenceToResolve>()
    private val typesAlreadyExtracted = mutableSetOf<String>() // ky by fully qualified name
    fun getTypeInPlace(
        t: Type,
        genericCheck: ((name: String) -> SwaggerSchema.SwaggerType?)? = null
    ): SwaggerSchema.SwaggerType {

        when (t) {
            is PrimitiveType -> {
                when (t.type) {
                    PrimitiveType.Primitive.BOOLEAN -> return SwaggerSchema.SwaggerType(type = "boolean")
                    PrimitiveType.Primitive.INT -> return SwaggerSchema.SwaggerType(type = "integer")
                    PrimitiveType.Primitive.SHORT -> return SwaggerSchema.SwaggerType(type = "integer")
                    PrimitiveType.Primitive.LONG -> return SwaggerSchema.SwaggerType(type = "integer")
                    PrimitiveType.Primitive.CHAR -> return SwaggerSchema.SwaggerType(type = "integer")
                    PrimitiveType.Primitive.FLOAT -> return SwaggerSchema.SwaggerType(type = "number")
                    PrimitiveType.Primitive.DOUBLE -> return SwaggerSchema.SwaggerType(type = "number")
                }
            }
            is VoidType -> {
                return SwaggerSchema.SwaggerType(type = "object")
            }
            is ArrayType -> {
                Log.warn("An ArrayType detected. This most likely means that the extractor is processing a type which should not end-up in the JSON (like a DateTime of some sorts) as arrays (e.g. int[]) aren't used frequently in JSON models.")
                return SwaggerSchema.SwaggerType(
                    type = "array",
                    items = getTypeInPlace(t.componentType, genericCheck = genericCheck)
                )
            }
            is WildcardType -> {
                if (t.superType.isPresent) {
                    Log.todo("Handle the situation where t.superType.isPresent. Is this even possible in java lol?") // TODO: this
                }
//                Log.debug("Found a WildcardType super:%s extended: %s".format(t.superType.orElse(null), t.extendedType.orElse(null)))
                return getTypeInPlace(t.extendedType.get(), genericCheck = genericCheck)
            }
            is ClassOrInterfaceType -> {
                if (genericCheck != null) {
                    val genericType = genericCheck(t.name.asString())
                    if (genericType != null) {
                        return genericType
                    }
                }
                when (t.name.asString()) {
                    "String" -> return SwaggerSchema.SwaggerType(type = "string")
                    "Integer" -> {
                        return SwaggerSchema.SwaggerType(type = "integer")
                    }
                }

                var resolved = try {
                    t.resolve()
                } catch (e: UnsolvedSymbolException) {
                    Log.warn("Failed to resolve type %s: %s".format(t, e))
                    Log.warn("... returning free-form object")
                    return SwaggerSchema.SwaggerType(
                        type = "object",
                        additionalProperties = SwaggerSchema.SwaggerType()
                    )
                }

                when (resolved.qualifiedName) {
                    "java.util.List", "java.util.ArrayList" -> return SwaggerSchema.SwaggerType(
                        type = "array",
                        items = getTypeInPlace(t.typeArguments.get().first(), genericCheck = genericCheck)
                    )
                    "java.util.HashMap", "java.util.Map" -> {
                        return SwaggerSchema.SwaggerType(
                            type = "object",
                            additionalProperties = getTypeInPlace(t.typeArguments.get()[1], genericCheck = genericCheck)
                        )
                    }
                    "java.lang.Boolean" -> {
                        return SwaggerSchema.SwaggerType(type = "boolean")
                    }
                    "java.util.Date" -> {
                        return SwaggerSchema.SwaggerType(type = "string", format = "date-time")
                    }
                    "java.lang.Integer", "java.lang.Long", "java.math.BigInteger" -> {
                        return SwaggerSchema.SwaggerType(type = "integer")
                    }
                    "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" -> {
                        return SwaggerSchema.SwaggerType(type = "number")
                    }
                    "java.lang.Object", "java.lang.Void" -> {
                        return SwaggerSchema.SwaggerType.FreeFormObject
                    }


                }
                // we might have found a type wrapper like Call<T>
                if (t.typeArguments.isPresent && t.typeArguments.get().size > 0) {


                    // check if it is a common wrapper
                    if (commonTypeWrappersLocator.getCommonTypeFQNsByKind(CommonTypeWrappersLocator.CommonTypeKind.TRANSPARENT_WRAPPER)
                            .contains(resolved.qualifiedName)
                    ) {
                        // just return what is inside
                        return getTypeInPlace(t.typeArguments.get().first(), genericCheck = genericCheck)
                    }

                    // if it is not a transparent wrapper we need to process this generic type in-place


                    val resolvedTypeDeclOpt = resolved.asReferenceType().typeDeclaration
                    if (resolvedTypeDeclOpt.isEmpty) {
                        println("[WARN] resolvedTypeDecl is empty, returning free-form object")
                        return SwaggerSchema.SwaggerType(
                            type = "object",
                            additionalProperties = SwaggerSchema.SwaggerType()
                        )
                    }
                    val resolvedTypeDecl = resolvedTypeDeclOpt.get()
                    if (resolvedTypeDecl is JavaParserClassDeclaration) {
                        val jtc = JsonTypeClass(
                            resolvedTypeDecl.wrappedNode,
                            this,
                            genericCallback = { idx ->
                                getTypeInPlace(
                                    t.typeArguments.get()[idx],
                                    genericCheck = genericCheck
                                )
                            },
                            commonTypeWrappersLocator
                        )
                        return jtc.toSwaggerType()
                    } else {
                        Log.warn(
                            "Generic class invocation is not a JavaParserClassDeclaration, but is a '%s', with qualified name of '%s'. Returning free-form object".format(
                                resolvedTypeDecl.javaClass.name,
                                resolvedTypeDecl.qualifiedName
                            )
                        )
                        return SwaggerSchema.SwaggerType(
                            type = "object",
                            additionalProperties = SwaggerSchema.SwaggerType()
                        )
                    }

                }

                val signature = commonTypeWrappersLocator.getSignatureByFQNAndKind(
                    CommonTypeWrappersLocator.CommonTypeKind.FORCE_SWAGGER_TYPE,
                    resolved.qualifiedName
                )
                if (signature != null) {
                    // just return what is inside
                    return signature.swaggerType!!
                }

            }

        }

        val st = SwaggerSchema.SwaggerType.TypeReferenceToResolve(t)
        // i add breakpoints here to track weird stuff
        if (t is ClassOrInterfaceType && t.name.asString().contains("OpeningHour")) {
            println("FFFFFF;" + t.name.asString())
        }
        typesToExtract.add(st)
        allReferences.add(st)
        return st
    }

    fun extractDependencyTypes(schema: SwaggerSchema) {
        var lastCountOfExtractedTypes = -1
        val fqnToRefMap = mutableMapOf<String, String>()
        while (typesToExtract.size > 0) {
            val copiedTypes = typesToExtract.toMutableList()
            typesToExtract.clear()
            copiedTypes.forEach {
                var resolvedTypeDecl: ResolvedReferenceTypeDeclaration? = null
                try {

                    resolvedTypeDecl = it.theType.resolve().asReferenceType().typeDeclaration.unwrap()
                } catch (e: UnsolvedSymbolException) {
                    Log.warn("failed to resolve dependent type %s: %s".format(it.theType, e))
                }

                if (resolvedTypeDecl == null) {
                    return@forEach
                }



                if (typesAlreadyExtracted.contains(resolvedTypeDecl.qualifiedName)) {
                    return@forEach
                }
                var niceTypeName = resolvedTypeDecl.qualifiedName
                val swaggerTypeToAdd = when (resolvedTypeDecl) {
                    is JavaParserClassDeclaration -> {
                        val jtc = JsonTypeClass(resolvedTypeDecl.wrappedNode, this, null, commonTypeWrappersLocator)
                        niceTypeName = jtc.niceName
                        jtc.toSwaggerType()
                    }
                    is JavaParserEnumDeclaration -> {
                        val jtc = JsonTypeEnum(resolvedTypeDecl.wrappedNode, commonTypeWrappersLocator)
                        niceTypeName = jtc.niceName
                        jtc.toSwaggerType()
                    }
                    else -> {
                        Log.warn("Unknown declaration of dependent type %s".format(resolvedTypeDecl))
                        Log.warn("ADDD FQN: " + resolvedTypeDecl.qualifiedName)
                        SwaggerSchema.SwaggerType.FreeFormObject
                    }
                }
                typesAlreadyExtracted.add(resolvedTypeDecl.qualifiedName)
                if (schema.components.schemas.containsKey(niceTypeName)) {
                    Log.warn(
                        "Schema already contains type with name %s, using FQN: %s".format(
                            niceTypeName,
                            resolvedTypeDecl.qualifiedName
                        )
                    )
                    niceTypeName = resolvedTypeDecl.qualifiedName
                }
                schema.components.schemas[niceTypeName] = swaggerTypeToAdd
                fqnToRefMap[resolvedTypeDecl.qualifiedName] = "#/components/schemas/$niceTypeName"
            }

            if (typesAlreadyExtracted.size == lastCountOfExtractedTypes) {
                Log.warn(
                    "[WARN] Dependency type extraction stuck in a loop. %d types left. Breaking...".format(
                        typesToExtract.size
                    )
                )
                break
            }
            lastCountOfExtractedTypes = typesAlreadyExtracted.size

        }

        Log.info("Extracted %d component types".format(typesAlreadyExtracted.size))
        allReferences.forEach {
            if (it.ref == null || it.ref == "") {
                it.ref = fqnToRefMap.getOrDefault(
                    it.theType.resolve().asReferenceType().typeDeclaration.get().qualifiedName,
                    "#/components/schemas/unknown"
                )
            }

        }
        Log.info("Filled %d references".format(allReferences.size))


    }
}