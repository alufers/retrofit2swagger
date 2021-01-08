import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr

class AnnotationUntil {
    companion object {

        fun extractValueExprFromAnnotation(annotation: AnnotationExpr): Expression? {
            when (annotation) {
                is SingleMemberAnnotationExpr -> {
                    return (annotation as SingleMemberAnnotationExpr).memberValue
                }
                is NormalAnnotationExpr -> {
                    val valuePair = annotation.pairs.find { it.name.asString() == "value" }
                    if (valuePair == null) {
                        Log.warn(
                            "value parameter not found in annotation: %s".format(
                                annotation.toString()
                            )
                        )
                        return null
                    }
                    return valuePair.value

                }
                else -> {

                    Log.warn(
                        "Found an annotation which is not a SingleMemberAnnotationExpr or a NormalAnnotationExpr, but a %s: %s in %s".format(
                            annotation.javaClass.canonicalName,
                            annotation.toString(),
                            Util.getNodeFilename(annotation)
                        )
                    )
                }
            }

            return null
        }
    }
}