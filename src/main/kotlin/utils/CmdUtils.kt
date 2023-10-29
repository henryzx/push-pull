package utils

object CmdUtils {

    fun groupArguments(args: Array<String>): HashMap<String, String> = HashMap<String, String>().apply {
        val argsList = args.toMutableList()
        while (true) {
            val key = argsList.removeFirstOrNull() ?: break
            if (key.startsWith("-")) {
                val value = argsList.firstOrNull()
                if (value != null && !value.startsWith("-")) {
                    this[key.drop(1)] = value
                }
            }
        }
    }

}

