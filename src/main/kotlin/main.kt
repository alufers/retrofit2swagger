import kotlinx.cli.ArgParser

fun main(args: Array<String>) {
    val parser = ArgParser("retrofit2swagger")
    parser.subcommands(ExtractSubcommand(), DumpUrlsSubcommand())

    parser.parse(args)

}


