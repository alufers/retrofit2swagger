class ProbableRetrofitApiLocator(
    val annotationLocator: RetrofitAnnotationLocator,
    val sourceFilesManager: SourceFilesManager
) {
    fun getApiFiles(): List<JavaSourceFile> {
        val packageNames = annotationLocator.annotationToSymbolsMap.values.flatten().map { it.describe() }
        var files = sourceFilesManager.getFilesWithImports(packageNames)
        Log.info("Found %d probable retrofit Api files".format(files.size))
        if(files.isEmpty()) {
            Log.warn("Retrofit files not found via imports, trying string search...")
            files = sourceFilesManager.getFilesWithStringsInside(annotationLocator.annotationToSymbolsMap
                .filter { COMMON_HTTP_METHODS.contains(it.key.toUpperCase()) }
                .values.flatten().map { "@" + it.describe().split(".").last() }
            )
            Log.info("Found %d probable retrofit Api files, by searching for strings like @GET, @POST etc.".format(files.size))
        }
//        println(files.joinToString(separator = ", \n") { it.filePath })
        return files
    }
}