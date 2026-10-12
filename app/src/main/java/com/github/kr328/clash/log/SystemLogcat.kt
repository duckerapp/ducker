package com.github.kr328.clash.log

object SystemLogcat {
    private const val SOURCE_CHARS = 1024 * 1024
    private val command = arrayOf(
        "logcat",
        "-d",
        "-s",
        "Go",
        "DEBUG",
        "AndroidRuntime",
        "Ducker",
        "LwIP",
    )

    fun dumpCrash(): String {
        return try {
            val process = Runtime.getRuntime().exec(command)

            val result = process.inputStream.use { stream ->
                stream.reader(Charsets.UTF_8).use readLog@ { reader ->
                    val tail = StringBuilder()
                    val buffer = CharArray(4096)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        tail.append(buffer, 0, read)
                        if (tail.length > SOURCE_CHARS) {
                            // A raw tail may begin inside a multiline secret.
                            process.destroy()
                            return@readLog "[system log exceeds safe input limit]"
                        }
                    }
                    tail.toString()
                }.lineSequence()
                    .filterNot { it.startsWith("------") }
                    .joinToString("\n")
            }

            process.waitFor()

            result.trim()
        } catch (e: Exception) {
            ""
        }
    }
}
