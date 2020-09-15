import kotlinx.cli.Subcommand

class DumpUrlsSubcommand :Subcommand("dump-urls", "lists all URLs found in the app's source code sorted by their probability of being an API") {
    override fun execute() {
       Log.todo("Implement me")
    }

}