import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.visitor.GenericVisitorAdapter
import com.github.javaparser.ast.visitor.VoidVisitorAdapter

class GsonCustomTypeLocator(val commonTypeWrappersLocator: CommonTypeWrappersLocator) {
    fun locateCustomTypes() {
        val typeAdaptersCustomType =
            commonTypeWrappersLocator.commonTypes.find { it.signature.friendlyName == "TypeAdapters" }
        if (typeAdaptersCustomType == null) {
            Log.warn("TypeAdapters from gson not found, cannot locate @SerializedName")
            return
        }
        data class FoundCast(val heuristicScore: Double, val type: Type);
        val foundCasts = mutableListOf<FoundCast>()
        val visitor = object : VoidVisitorAdapter<Unit?>() {
            override fun visit(n: CastExpr, arg: Unit?) {
                if (n.type.isPrimitiveType || listOf(
                        "Class",
                        "Enum",
                        "Throwable",
                        "Date",
                        "String",
                        "Enum[]",
                        "Number",
                        "Integer",
                        "JsonElement"
                    ).contains(n.typeAsString)
                ) {
                    return
                }
                var heuristicScore = 0.0;
                if (n.typeAsString == "SerializedName") {
                    heuristicScore += 20.0;
                }
                if (n.expression.toString().contains("getAnnotation")) {
                    heuristicScore += 10.0;
                }
                if (n.expression.toString().contains("getField")) {
                    heuristicScore += 10.0;
                }
                foundCasts.add(FoundCast(heuristicScore, n.type))
                super.visit(n, arg)
            }
        }
        typeAdaptersCustomType.compilationUnits.first().accept(visitor, null)

        foundCasts.sortByDescending { it.heuristicScore }


        val signature = commonTypeSignatures.find { it.friendlyName == "SerializedName" }!!
        var ct = commonTypeWrappersLocator.commonTypes.find { it.signature == signature }
        if (ct == null) {
            ct = CommonTypeWrappersLocator.CommonType(signature)
            commonTypeWrappersLocator.commonTypes.add(ct)
        }
        ct.fullyQualifiedNames.add(foundCasts.first().type.resolve().asReferenceType().qualifiedName)

        Log.info("Found @SerializedName: %s".format(ct.fullyQualifiedNames))
    }
}