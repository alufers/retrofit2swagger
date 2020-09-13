import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import kotlinx.serialization.stringify
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage:")
        println("   retrofit2swagger <sourcesDir>")
        return
    }
    Log.info("Starting retrofit2swagger")
    val sourcesPath = args[0]
    setUpTypeSolvers(sourcesPath)
    val sfManager = SourceFilesManager()
    sfManager.loadFilePaths(sourcesPath)

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
        exitProcess(1);
        return
    }
    ActiveServerDetector(sfManager).detectServers(schema)

    val result = Yaml(configuration = YamlConfiguration(encodeDefaults = false)).encodeToString(
        SwaggerSchema.serializer(),
        schema
    )

    val outPath = args[0].split("/").reversed().first { it != "sources" } + "-out.swagger.yml"
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