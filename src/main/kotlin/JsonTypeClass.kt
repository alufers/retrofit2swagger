import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter
import java.lang.UnsupportedOperationException

public class JsonTypeClass(
    val sourceDecl: ClassOrInterfaceDeclaration,
    val typeProcessor: TypeProcessor,
    val genericCallback: ((genericTypeIndex: Int) -> SwaggerSchema.SwaggerType)? = null,
    val commonTypeWrappersLocator: CommonTypeWrappersLocator
) {
    var niceName: String
    var fullyQualifiedName: String

    init {
        var compiledFromName: String? = null
        if (sourceDecl.comment.isPresent) {
            val com = sourceDecl.comment.get()
            val match = Regex("compiled from: ([A-Za-z0-9\\._]+)").find(com.content)
            if (match != null && match?.destructured.toList().isNotEmpty()) {
                compiledFromName = match?.destructured.toList().first()
            }
        }
        niceName = Util.chooseBetterName(listOfNotNull(sourceDecl.nameAsString, compiledFromName))
        fullyQualifiedName = sourceDecl.fullyQualifiedName.orElse("unknown")
    }

    class ExtractedProperty(
        val javaName: String,
        val annotatedNames: MutableList<String> = mutableListOf(),
        val type: SwaggerSchema.SwaggerType,
        val aliasedName: String?
    ) {}

    val propertyExtractorVisitor = object : GenericListVisitorAdapter<ExtractedProperty, ExtractedProperty?>() {
        override fun visit(n: ClassOrInterfaceDeclaration, arg: ExtractedProperty?): MutableList<ExtractedProperty> {
            if (n.fullyQualifiedName == sourceDecl.fullyQualifiedName) { // we only want do descend into the fields of the root class, not the
                return super.visit(n, arg)
            }
            return mutableListOf()
        }

        override fun visit(n: FieldDeclaration, arg: ExtractedProperty?): MutableList<ExtractedProperty> {
            val result = mutableListOf<ExtractedProperty>()
            if (n.isStatic) {
                return result
            }
            n.variables?.forEach {

//                println("FQ " + fullyQualifiedName)
//                println(it.type.javaClass.canonicalName)

                // TODO: fix this ugly-ass blacklist
                if (it.type is ClassOrInterfaceType && listOf(
                        "Parcelable",
                        "Parcelable.Creator",
                        "Creator"
                    ).contains((it.type as ClassOrInterfaceType).name.asString())
                ) {
                    return@forEach
                }
//Log.debug("afert bl")

                try {
                    result.add(
                        ExtractedProperty(
                            javaName = it.nameAsString,
                            type =  typeProcessor.getTypeInPlace(it.type, genericCheck = ::genericCheck),
                            aliasedName = extractAliasedName(n.annotations)
                        )
                    )
                } catch (e: UnsupportedOperationException) {
                    println(e)
                    throw e
                }

            }
//            super.visit(n, arg)
            return result
        }
    }

    private fun extractAliasedName(annotations: NodeList<AnnotationExpr>): String? {
        val aliastAnnotationQualifiedNames =
            commonTypeWrappersLocator.getCommonTypeFQNsByKind(CommonTypeWrappersLocator.CommonTypeKind.ALIAS_ANNOTATION)
        annotations.forEach { anno ->
            if(anno.toString() == "@SerializedName(\"Code\")") {
                Log.debug("I guess i will die now")
            }
            if (aliastAnnotationQualifiedNames.contains(anno.resolve().qualifiedName)) {
                val valueExpr = AnnotationUntil.extractValueExprFromAnnotation(anno)
                if (valueExpr is StringLiteralExpr) {
                    return valueExpr.value
                }
            }
        }
        return null
    }

    private fun genericCheck(typeName: String): SwaggerSchema.SwaggerType? {
        val genericTypeIndex = sourceDecl.typeParameters.map { tp -> tp.name.asString() }
            .indexOf(typeName)
        if (genericTypeIndex != -1) {
            if (genericCallback == null) {
                Log.warn(
                    "A generic type %s was used without the generic argument. All generic fields and superclasses of this class will be a free-form object.".format(
                        sourceDecl.fullyQualifiedName.orElse("<unknown>")
                    )
                )
                return SwaggerSchema.SwaggerType(
                    type = "object",
                    additionalProperties = SwaggerSchema.SwaggerType()
                )
            }
            val genericCallbackNotNull = genericCallback!!
            return genericCallbackNotNull(genericTypeIndex)
        }
        return null
    }

    /**
     * Generates a swagger type containing the properties of this class.
     * @TODO: add handling of annotations which change serialized names
     */
    fun toSwaggerType(): SwaggerSchema.SwaggerType {
        val t = SwaggerSchema.SwaggerType(
            type = "object",
            properties = mutableMapOf()
        )
//    Log.debug("begfore extreaktion")
        val extractedProps = sourceDecl.accept(propertyExtractorVisitor, null)

        extractedProps.forEach {
            t.properties[it.aliasedName ?: it.javaName] = it.type
        }
//        Log.debug("type extraction finished")
        // we can use  sourceDecl.extendedTypes.first() since classes can only extend one other class, not multiple
        if (sourceDecl.extendedTypes.isNonEmpty && commonTypeWrappersLocator.getSignatureByFQNAndKind(
                CommonTypeWrappersLocator.CommonTypeKind.IGNORE_WHEN_EXTENDED,
                sourceDecl.extendedTypes.first().resolve().qualifiedName
            ) == null
        ) {
            return SwaggerSchema.SwaggerType(
                allOf = mutableListOf(
                    typeProcessor.getTypeInPlace(sourceDecl.extendedTypes.first(), genericCheck = ::genericCheck),
                    t
                )
            )
        }
        return t
    }
}
