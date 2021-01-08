import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node

class Util {
    companion object {

        private val badPrefixesForSlash = listOf("/", "http://", "https://")
        fun prependSlashIfNeeded(url: String): String {
            return if (badPrefixesForSlash.any { url.startsWith(it) }) url else "/$url";
        }

        fun joinUrls(a: String, b: String): String {
            return a.trimEnd('/') + "/" + b.trimStart('/')
        }

        fun isGarbageName(name: String): Boolean {
            return when {
                name.isEmpty() -> true
                (name.filter { c -> c.isDigit() }.length.toDouble() / name.length.toDouble()) > 0.5 -> true
                name.first().isLowerCase() && name.length < 4 -> true
                else -> false
            }
        }

        fun chooseBetterName(possibleNames: List<String>): String {
            val better = possibleNames.find { !isGarbageName(it) } ?: return possibleNames.first()
            if (better.endsWith(".kt")) {
                return better.removeSuffix(".kt")
            }
            return better
        }

        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        fun randomString(length: Int = 8): String {
            return (1..length)
                .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
                .map(charPool::get)
                .joinToString("");
        }

        fun generateJSONGarbage(depth: Int = 0): String {
            if (depth > 3) return "."
            return when (kotlin.random.Random.nextInt(8)) {
                0 -> "[[[[" + generateJSONGarbage(depth + 1)
                1 -> generateJSONGarbage(depth + 1) + ":" + generateJSONGarbage(depth + 1)
                2 -> "\"" + generateJSONGarbage(depth + 1)
                3 -> kotlin.random.Random.nextInt(2137).toString() + "e3333e33-3-"
                4 -> "{" + generateJSONGarbage(depth + 1) + ":" + generateJSONGarbage(depth + 1)
                5 -> "-" + generateJSONGarbage(depth + 1)
                6 -> "null"
                else -> randomString()
            }
        }

        fun getNodeFilename(node: Node?): String {
            if (node == null) {
                return "<unknown>"
            }
            if (node is CompilationUnit) {
                return node.storage.unwrap()?.path.toString() ?: "<unknown path>";
            }
            return getNodeFilename(node.parentNode.unwrap())
        }
    }
}