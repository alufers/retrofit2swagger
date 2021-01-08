import com.github.javaparser.ParseProblemException
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.utils.StringEscapeUtils
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.*
import org.apache.commons.io.IOUtils

class ActiveServerDetector(private val sourceFilesManager: SourceFilesManager) {

    val urlHeuristicThreshold = -10.0
    lateinit var probableAPIUrls: MutableMap<String, Double>
    fun detectServers(schema: SwaggerSchema) {
        extractProbableServerURLs()


        val interfaceNames =
            schema.paths.flatMap { it.value.entries.map { e -> e.value } }.map { it.xRetrofitInterface }.toSet()
                .toList()

        var interfacesWithUrls = 0
        var interfacesWithoutUrls = 0
        interfaceNames.forEach { ifaceName ->

            // we sort the urls for every interface since the urls can get penalized during probing
            val sortedUrls =
                probableAPIUrls.entries.sortedByDescending { it.value }.filter { it.value > urlHeuristicThreshold }
                    .map { it.key }

            val didFindServerURL = sortedUrls.any { url ->
                tryUrlOnInterface(schema, url, ifaceName!!)
            }
            if (!didFindServerURL) {
                Log.warn("Server URL not found for %s".format(ifaceName))
                interfacesWithoutUrls++
            } else {
                interfacesWithUrls++
            }
//            Thread.sleep(100)
        }

        Log.info(
            "API interfaces with found urls: %d, not found: %d. Success rate: %.2f%%".format(
                interfacesWithUrls,
                interfacesWithoutUrls,
                100 * interfacesWithUrls.toDouble() / (interfacesWithUrls.toDouble() + interfacesWithoutUrls.toDouble())
            )
        )

    }

    data class EndpointWithMethod(val method: String, val path: String);
    fun tryUrlOnInterface(schema: SwaggerSchema, url: String, interfaceFqn: String): Boolean {

        val endpoints =
            schema.paths.flatMap { pathEntry ->
                pathEntry.value.mapNotNull {
                    if (it.value.xRetrofitInterface == interfaceFqn) EndpointWithMethod(it.key, pathEntry.key) else null
                }
            }
        val scoredEndpoints = endpoints.associate {
            var score = 0.0
            if (it.method.toUpperCase() == "GET") {
                score -= 20.0;
            }
            if (it.method.toUpperCase() == "DELETE") {
                score -= 60.0;
            }
            if (it.method.toUpperCase() == "POST") {
                score += 5.0;
            }
            score -= Regex("\\{.*?\\}").findAll(it.path).count() * 10.0;
            Pair(it, score)
        }
        val sortedEndpoints = scoredEndpoints.entries.sortedByDescending { it.value }.map { it.key }.take(3)
        for (endpointToTry in sortedEndpoints) {
            var garbledStatus = 0
            // we change more and more parts of the url to random letters, staring from the end of the path
            // we do it up to 4 parts or there are none left
            for (garbleLevel in 1..(minOf(4, endpointToTry.path.split("/").filter { !it.startsWith("{") }.size))) {
                garbledStatus = makeProbeRequest(
                    endpointToTry.method,
                    Util.joinUrls(url, fillUrlParams(garbleUrl(endpointToTry.path, garbleLevel))),
                    url,
                    true
                )
                if (garbledStatus == 404) {
                    break
                }
            }
            if (garbledStatus != 404) {
                Log.info(
                    "Server URL %s does not respond with 404 when sending it garbage endpoints based on %s (lastStatus: %d)".format(
                        url,
                        endpointToTry.path,
                        garbledStatus
                    )
                )
                heuristicPenalty(url, 2.0)
                break
            }
            val goodStatus = makeProbeRequest(
                endpointToTry.method,
                Util.joinUrls(url, fillUrlParams(endpointToTry.path)),
                url,
                false
            )
            if (goodStatus != garbledStatus) {
                Log.info("Found server URL %s for Api interface %s".format(url, interfaceFqn))
                val operationsOwnedByInterface = schema.paths.flatMap { pathEntry ->
                    pathEntry.value.filter {
                        it.value.xRetrofitInterface == interfaceFqn
                    }.map { opEntry -> opEntry.value }
                }

                operationsOwnedByInterface.forEach {
                    it.servers = mutableListOf(SwaggerSchema.ServerSpec(url.trimEnd('/')))
                }

                return true
            }
        }
        return false
    }

    private fun makeProbeRequest(
        method: String,
        url: String,
        baseServerUrl: String? = null,
        allowRetry: Boolean,
        isRetryAfterBadRequest: Boolean = false
    ): Int {
        val u = URL(url)
        try {

            with(u.openConnection() as HttpURLConnection) {
                requestMethod = method.toUpperCase()
                setRequestProperty("Content-Type", "application/json");
                setRequestProperty("charset", "utf-8");
                if (!listOf("GET", "OPTIONS").contains(method.toUpperCase())) {
                    doOutput = true
                    val wr = OutputStreamWriter(outputStream);
                    if (isRetryAfterBadRequest) {
                        wr.write("{}")
                    } else {
                        wr.write(Util.generateJSONGarbage());
                    }

                    wr.flush();
                }
                Log.http("%s %s -- %d".format(method.toUpperCase(), url, responseCode), responseCode == 404)
                if (responseCode == 400 && allowRetry && !isRetryAfterBadRequest) {
                    Log.warn("retrying with valid JSON... (prepare for unforeseen consequences)")
                    return makeProbeRequest(method, url, baseServerUrl, allowRetry, true)
                }
                if (responseCode == 200) {
                    val ipStream: InputStream = inputStream
                    var encoding: String = contentEncoding ?: "UTF-8"

                    val body: String = IOUtils.toString(ipStream, encoding)
                    if (body.split("<script").size > 7) { // probably a big webpage
                        heuristicPenalty(baseServerUrl, 5.0)
                    }
                    if (body.split("<div").size > 80) { // probably a big webpage
                        heuristicPenalty(baseServerUrl, 5.0)
                    }
                }
                return responseCode
            }
        } catch (e: ProtocolException) {
            Log.http("%s %s -- %s".format(method.toUpperCase(), url, e.message))
        } catch (e: UnknownHostException) {
            Log.http("%s %s -- %s".format(method.toUpperCase(), url, e.message))
            heuristicPenalty(baseServerUrl, 20.0)
        }
        return 0
    }

    private fun heuristicPenalty(baseServerUrl: String?, penalty: Double) {
        if (baseServerUrl != null && probableAPIUrls.containsKey(baseServerUrl)) {
            Log.warn(
                "Adding heuristic penalty of %s for %s".format(
                    penalty.toString(),
                    baseServerUrl
                )
            )
            probableAPIUrls[baseServerUrl] = probableAPIUrls[baseServerUrl]!! - penalty
        }
    }

    fun fillUrlParams(input: String): String {
        return input.replace(Regex("\\{.*?\\}")) { Util.randomString(9) }
    }

    fun garbleUrl(input: String, garbleLevel: Int = 1): String {
        var garbleLimit = garbleLevel
        val parts = input.split("/").reversed().map {
            if (it.startsWith("{") || garbleLimit <= 0) {
                return@map it
            }
            garbleLimit--
            return@map Util.randomString(8)
        }.reversed()
        return parts.joinToString("/")
    }

    // various heuristics for detecting  api urls, mainly blacklisting analytics and common documentation urls
    val wholeURLHeuristicScores = mapOf<Regex, Double>(
        Regex("\\%s") to -20.0,
        Regex("\\.(png|jpg|jpeg|pdf)$") to -20.0, // we don't want image urls lol
        Regex("=$") to -2.0,
        Regex("\\?(.)+=$") to -10.0, // match any requests with query parameters since appending anything to them will likely cause bad stuff to happen
        Regex("\\?(.)+=") to -5.0,
        Regex("#([A-Za-z0-9_\$\\-\\%])+") to -20.25, // links with hashes are almost always to library documentation or support

        Regex("\\/v([0-9]+)\\/") to 4.0, // v1, v2, v3 ... api version indicators
        Regex("\\/api\\/") to 4.0,
        Regex("callback") to -0.25, // probably means some kind of authorization callback, not an api base url

        Regex("https://s3.amazonaws.com/android-beacon-library/android-distance.json", RegexOption.LITERAL) to -40.0,

        // instagram profile links
        Regex("instagram\\.com\\/([A-Za-z0-9_\\.]*)") to -40.0,

        // com.google.android.gms:play-services-auth.IdentityProviders has links to login pages of various webpages, we blacklist those

        Regex("https:\\/\\/www\\.facebook\\.com") to -30.0,
        Regex("https:\\/\\/accounts\\.google\\.com") to -30.0,
        Regex("https:\\/\\/www\\.linkedin\\.com") to -30.0,
        Regex("https:\\/\\/login\\.live\\.com") to -30.0,
        Regex("https:\\/\\/www\\.paypal\\.com") to -30.0,
        Regex("https:\\/\\/twitter\\.com") to -30.0,
        Regex("https:\\/\\/login\\.yahoo\\.com") to -30.0
    )

    val domainHeuristicScores = mapOf<Regex, Double>(
        // social and store links
        Regex("facebook\\.com$") to -50.0,
        Regex("play\\.google\\.com$") to -50.0,
        Regex("fb\\.gg$") to -15.0,

        // analytics
        Regex("digits\\.com$") to -20.0,
        Regex("app\\.adjust\\.com$") to -50.0,
        Regex("gdpr\\.adjust\\.com$") to -50.0,
        Regex("app\\.igodigital\\.com$") to -30.0, // salesforce marketing cloud
        Regex("google-analytics\\.com$") to -50.0,
        Regex("cformanalytics\\.com$") to -40.0, // some kind of crash handling or  analytics (CrashShieldHandler???)
        Regex("emarsys\\.net$") to -40.0, // The only omnichannel customer engagement platform built to accelerate business outcomes

        // ads
        Regex("googleadservices\\.com$") to -20.0,
        Regex("googlesyndication\\.com$") to -20.0,
        Regex("googleads\\.g\\.doubleclick\\.net$") to -50.0,
        Regex("doubleclick\\.net$") to -10.0,

        // error reporting
        Regex("overmind\\.datatheorem\\.com$") to -40.0,
        Regex("settings\\.crashlytics\\.com$") to -50.0,
        Regex("e\\.crashlytics\\.com$") to -50.0,
        Regex("crashlytics\\.com$") to -10.0,

        // example and testing stuff
        Regex("localhost$") to -50.0,
        Regex("hostname$") to -50.0,
        Regex("127\\.0\\.0\\.1$") to -50.0,
        Regex("10\\.0\\.2\\.2$") to -50.0,
        Regex("\\.local$") to -11.0,
        Regex("example\\.com$") to -50.0,
        Regex("not\\.existing\\.url") to -50.0,

        // google stuff
        Regex("android\\.com$") to -20.0,
        Regex("googleapis\\.com$") to -20.0,
        Regex("googletagmanager\\.com$") to -20.0,
        Regex("plus\\.google\\.com$") to -50.0,
        Regex("firebaseremoteconfig\\.googleapis\\.com$") to -50.0,
        Regex("google\\.com$") to -50.0,
        Regex("app-measurement\\.com$") to -20.0,
        Regex("csi\\.gstatic\\.com$") to -30.0, // some kind of internal google logging and analytics stuff

        // Links to schema documentations and other documentations
        Regex("schemas\\.xmlsoap\\.org$") to -20.0,
        Regex("w3\\.org$") to -20.0,
        Regex("slf4j\\.org$") to -20.0,
        Regex("schemas\\.android\\.com$") to -50.0,
        Regex("schema\\.org$") to -35.0,
        Regex("xmlpull\\.org$") to -35.0,
        Regex("dashif\\.org$") to -35.0,
        Regex("ns\\.adobe\\.com$") to -35.0,
        Regex(
            "javax\\.xml\\.xmlconstants$",
            RegexOption.IGNORE_CASE
        ) to -40.0, //  newInstance.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true); --- WTF? jackson
        Regex("pay\\.cards$") to -35.0, // sdk for credit card reognition, this is just a homepage for an open-source project
        Regex("tempuri\\.org$") to -35.0, // tempuri.org is the test default namespace URI used by Microsoft development products https://en.wikipedia.org/wiki/Tempuri
        Regex("tempuri\\.com$") to -35.0, // Tempuri.com
        Regex("schemas\\.microsoft\\.com") to -35.0,

        // various ads and 3rdparty stuff
        Regex("google") to -1.25,
        Regex("syndication") to -1.25,
        Regex("adservices") to -1.25,

        // login domains
        Regex("login\\.yahoo\\.com$") to -10.0,
        Regex("login\\.live\\.com$") to -10.0,
        Regex("accounts\\.google\\.com$") to -10.0,

        // templates and malformed hosts
        Regex("\\%s") to -30.0,
        Regex("^$") to -80.0, // empty hostname
        Regex("www\\.$") to -80.0, // only www. and nothing
        Regex("\\.$") to -5.0, // dot at the end of the hostname is valid, but is usually indicative of a string that is concatenated with something

        // common, well-documented apis that we don't want to bombard with requests

        Regex("^api\\.instagram\\.com$") to -5.0,

        // the good stuff
        Regex("api") to 5.0,
        Regex("app") to 5.0,

        Regex("^malformedhostmalformedhost$") to -70.0,
    )

    private fun addStringAsUrl(str: String, additionalScore: Double) {
        if (!(str.startsWith("https://") || str.startsWith("http://"))) {
            return
        }
        probableAPIUrls[str] = additionalScore
        var host = "malformedhostmalformedhost"
        try {
            host = (URL(str).host).toLowerCase()
        } catch (e: MalformedURLException) {
            Log.debug("malformed: " + str)
            probableAPIUrls[str] = probableAPIUrls[str]!! - 30.0
        }
        wholeURLHeuristicScores.entries.filter { it.key.containsMatchIn(str) }
            .forEach { probableAPIUrls[str] = probableAPIUrls[str]!! + it.value }
        domainHeuristicScores.entries.filter {
            it.key.containsMatchIn(host)
        }.forEach { probableAPIUrls[str] = probableAPIUrls[str]!! + it.value }
    }

    /**
     * Returns a map of probable server URLs as keys and a heuristic score as the value
     */
    fun extractProbableServerURLs() {
        val sourceFiles = sourceFilesManager.getFilesWithStringsInside(listOf("\"http://", "\"https://"))
        probableAPIUrls = mutableMapOf<String, Double>()
        val literalExtractorVisitor = object : VoidVisitorAdapter<Double>() {
            override fun visit(n: CompilationUnit, arg: Double) {

                super.visit(n, arg)
            }

            override fun visit(n: StringLiteralExpr, additionalScore: Double) {
                val str = n.value;
                addStringAsUrl(str, additionalScore)
            }
        }

        sourceFiles.forEach { sf ->
            var additionalScore = 0.0
            if (sf.filePath.toLowerCase().contains("buildconfig")) {
                additionalScore += 1.45
            }
            if (sf.filePath.toLowerCase().contains("config")) {
                additionalScore += 0.10
            }
            if (sf.filePath.toLowerCase().contains("environment")) {
                additionalScore += 0.10
            }
            try {
                sf.ast.accept(literalExtractorVisitor, additionalScore)
            } catch (e: ParseProblemException) {
                val stringExtractionRegex = Regex("((?<![\\\\])['\"])((?:.(?!(?<![\\\\])\\1))*.?)\\1")
                val matches = stringExtractionRegex.findAll(File(sf.filePath).readText())
                matches.forEach {
                    addStringAsUrl(
                        StringEscapeUtils.unescapeJava(it.value.replace(Regex("^\"|\"$"), "")),
                        additionalScore
                    )
                }
            }

        }

    }

    fun printProbableServerURLs() {

        probableAPIUrls.entries.sortedByDescending { it.value }.forEach {
//            Log.debug(
//                "HOST: " + URL(it.key).host
//            )
            Log.debug("url: %s score: %s".format(it.key, it.value.toString()))
        }

    }
}