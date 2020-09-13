class ProbableRetrofitApiLocator(
    val annotationLocator: RetrofitAnnotationLocator,
    val sourceFilesManager: SourceFilesManager
) {
    fun getApiFiles(): List<JavaSourceFile> {
        val packageNames = annotationLocator.annotationToSymbolsMap.values.flatten().map { it.describe() }
        val files = sourceFilesManager.getFilesWithImports(packageNames)
        Log.info("Found %d probable retrofit Api files".format(files.size))
//        println(files.joinToString(separator = ", \n") { it.filePath })
        return files
    }
}