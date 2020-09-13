import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.visitor.GenericVisitor
import com.github.javaparser.ast.visitor.GenericVisitorAdapter
import com.github.javaparser.resolution.declarations.ResolvedClassDeclaration
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration

/**
 * EvaluatorUtil can evaluate some expressions, currently it just has to trace static final string variables.
 */
class EvaluatorUtil {
    companion object {


        private val evaluatorVisitor = object : GenericVisitorAdapter<Any?, Unit?>() {
            override fun visit(n: StringLiteralExpr, arg: Unit?): Any? {
                return n.value
            }

            // we evalueate static field access expressions
            override fun visit(n: FieldAccessExpr, arg: Unit?): Any? {
                val resolvedFieldAccess = n.resolve()
                if (resolvedFieldAccess is ResolvedFieldDeclaration) {
                    val declaringType = resolvedFieldAccess.declaringType()

                    if (declaringType is JavaParserClassDeclaration) {

                        val theField = declaringType.wrappedNode.fields.filter { it.isStatic }.flatMap { it.variables }
                            .find { it.nameAsString == n.nameAsString }
                        if (theField == null) {
//                            Log.warn(
//                                "Access of non-existent field %s on %s while evaluating expr. Ignoring".format(
//                                    n.nameAsString,
//                                    declaringType.qualifiedName
//                                )
//                            )
                            return null
                        }

                       if(theField.initializer.isPresent) {
                           return theField.initializer.get().accept(this, null)
                       }

                    }
                }
                return null
            }

        }

        fun evalToString(expr: Expression): String? {
            return expr.accept(evaluatorVisitor, null) as? String
        }
    }
}