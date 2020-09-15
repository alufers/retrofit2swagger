import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import kotlinx.cli.*
import java.io.File

class ExtractSubcommand: Subcommand("extract", "Extracts API endpoints from java sourcecode to an OpenAPI schema file") {
    val output by option(ArgType.String, "output", "o", "Output file")
    val enableActiveServerDetection by option(ArgType.Boolean, "enable-active-server-detection", "a", "Enable active server detection (detect the root server URLs by making real requests)")
    val decompiledAppDir by argument(ArgType.String, "app-dir", description = "The directory where the app was decompiled with JADX")
    override fun execute() {
        setUpTypeSolvers(decompiledAppDir)

        val sfManager = SourceFilesManager()
        sfManager.loadFilePaths(decompiledAppDir)

        val annotationLocator = RetrofitAnnotationLocator(sfManager)
        annotationLocator.locateRetrofitAnnotations()
        val commonTypeWrappersLocator = CommonTypeWrappersLocator(sfManager)
        commonTypeWrappersLocator.findCommonTypeWrappers()
        commonTypeWrappersLocator.dumpFoundTypes()

        GsonCustomTypeLocator(commonTypeWrappersLocator).locateCustomTypes()

        val retrofitApiLocator = ProbableRetrofitApiLocator(annotationLocator, sfManager)
        val schema = SwaggerSchema(openapi = "3.0.0")
        val typeProcessor = TypeProcessor(commonTypeWrappersLocator)
        var totalInterfaces = 0
        var totalEndpoints = 0
        val apiFiles = retrofitApiLocator.getApiFiles()
        apiFiles.forEach {
            val extractor = ApiInterfaceEndpointExtractor(schema, it, annotationLocator, typeProcessor)
            extractor.extractEndpoints()
            totalEndpoints += extractor.endpointsCount
            totalInterfaces += extractor.interfacesCount
            Log.progress(totalInterfaces.toDouble() + 1, apiFiles.size.toDouble(), "Processing retrofit API files", "files")
        }
        println()
        Log.info("Extracted %d endpoints from %d retrofit interfaces".format(totalEndpoints, totalInterfaces))
        typeProcessor.extractDependencyTypes(schema)
        if(totalEndpoints <= 0) {
            Log.error("No endpoints found!")
            kotlin.system.exitProcess(1);
            return
        }
        if(enableActiveServerDetection == true) {
            ActiveServerDetector(sfManager).detectServers(schema)
        } else {
            Log.info("TIP: You can enable active server detection by passing the --enable-active-server-detection (-a for short) so that you get full URLs, not just the paths.")
            Log.warn("This will however make a fair amount of requests the the servers mentioned in the apps source code. Use with caution.")
        }

        val friendlyAppName =  decompiledAppDir.split("/").reversed().first { it != "sources" }
        schema.info.title = friendlyAppName + " [retrofit2swagger]]"
        val result = Yaml(configuration = YamlConfiguration(encodeDefaults = false)).encodeToString(
            SwaggerSchema.serializer(),
            schema
        )


        val outPath = friendlyAppName + "-out.swagger.yml"
        Log.info("Writing output to: %s".format(outPath))
        File(outPath).writeText(result)
    }

    fun setUpTypeSolvers(sourcesPath: String) {
        val reflectionTypeSolver = ReflectionTypeSolver()
        val javaParserTypeSolver = JavaParserTypeSolver(sourcesPath, StaticJavaParser.getConfiguration())

//    reflectionTypeSolver.parent = reflectionTypeSolver
        val combinedSolver = CombinedTypeSolver()
        combinedSolver.add(reflectionTypeSolver);
        combinedSolver.add(javaParserTypeSolver);

        val symbolSolver = JavaSymbolSolver(combinedSolver)
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver)
    }
}