import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand

class DumpUrlsSubcommand :Subcommand("dump-urls", "lists all URLs found in the app's source code sorted by their probability of being an API") {
    val enableActiveServerDetection by option(ArgType.Boolean, "all", "a", "List all URLs, including those scoring low")
    val decompiledAppDir by argument(ArgType.String, "app-dir", description = "The directory where the app was decompiled with JADX")
    override fun execute() {
        val sfManager = SourceFilesManager()
        sfManager.loadFilePaths(decompiledAppDir)
        val detector = ActiveServerDetector(sfManager)
        detector.extractProbableServerURLs()
        detector.probableAPIUrls.entries.sortedByDescending { it.value }.forEach {
            println(it.key)
        }
    }

}