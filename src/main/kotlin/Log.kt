sealed class Log(val scope: String = "") {
    companion object : Log() {
    }

    fun warn(message: Any?) {
        print("[\u001B[38;2;255;165;0mWARN\u001B[0m] ")
        println(message)
    }

    fun todo(message: Any?) {
        print("[\u001B[38;2;255;0;255mTODO\u001B[0m] ")
        println(message)
    }

    fun info(message: Any?) {
        print("[\u001B[38;2;0;190;255mINFO\u001B[0m] ")
        println(message)
    }

    fun http(message: Any?, addReturn: Boolean = false) {
        print("[\u001B[38;2;0;50;39mHTTP\u001B[0m] ")
        print(message.toString() + (if (addReturn) "\r" else "\n"))
    }

    fun debug(message: Any?) {
        val stacktrace = Thread.currentThread().stackTrace
        val e = stacktrace[2]

        val callSite = e.fileName + ":" + e.lineNumber + " [" + e.className + "." + e.methodName + "]"

        print("[\u001B[38;2;170;190;255mDBUG\u001B[0m] ")
        print(message)
        println("     \u001B[38;2;140;140;170m$callSite\u001B[0m")
    }

    fun error(message: Any?) {
        print("[\u001B[38;2;255;0;0mERRO\u001B[0m] ")
        println(message)
    }

    var progressOrbPos = 0

    fun rgb(r: Int, g: Int, b: Int): String {
        return "\u001B[38;2;%d;%d;%dm".format(r, g, b)
    }

    fun reset(): String {
        return "\u001B[0m"
    }

    fun qualifiedName(name: String): String {
        val segments = name.split(".")
        return segments.mapIndexed { i, s ->
            if (i == segments.size - 1) rgb(240, 240, 255) + s + reset() else rgb(
                200,
                200,
                200
            ) + s
        }
            .joinToString(rgb(170, 170, 170) + ".")
    }

    fun progress(current: Double, total: Double, operationDescription: String, what: String = "") {

        val progressBar = StringBuilder()
        val progressLength = 20;

        for (i in 0..progressLength) {
            if (i.toDouble() / progressLength.toDouble() <= current / total) {
                progressBar.append(
                    "\u001b[38;2;40;%.0f;249m".format(i.toDouble() / progressLength.toDouble() * 255)
                )
                progressBar.append("=")
            } else {

                progressBar.append("\u001b[0m ")
            }
        }
        if (current == total) {
            progressOrbPos = 999
        }
        val orb = when (progressOrbPos) {
            0 -> "*   "
            1 -> " *  "
            2 -> "  * "
            3 -> "   *"
            4 -> "  * "
            5 -> " *  "
            else -> "----"
        }
        progressOrbPos++
        if (progressOrbPos > 5) {
            progressOrbPos = 0
        }


        print(
            "\r[%s] \u001B[38;2;190;190;190m%s\u001B[0m \u001B[38;2;255;255;255m%.2f%%\u001B[0m [%s\u001B[0m] \u001B[38;2;140;140;180m %.0f/%.0f %s\u001B[0m\r".format(
                orb,
                operationDescription,
                (current / total) * 100,
                progressBar.toString(),
                current,
                total,
                what
            )
        )
    }


}