import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumConstantDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter
import com.github.javaparser.ast.visitor.VoidVisitorAdapter

class JsonTypeEnum(
    val sourceDecl: EnumDeclaration,
    val commonTypeWrappersLocator: CommonTypeWrappersLocator,
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

    fun toSwaggerType(): SwaggerSchema.SwaggerType {

        val enumVisitor = object : GenericListVisitorAdapter<String, Unit?>() {
            override fun visit(n: EnumConstantDeclaration, arg: Unit?): MutableList<String>? {
                return mutableListOf(n.nameAsString)
            }
        }
        return SwaggerSchema.SwaggerType(
            type = "string",
            enum = sourceDecl.accept(enumVisitor, null)
        )
    }
}