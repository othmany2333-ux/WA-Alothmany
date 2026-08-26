package com.alothmany.wa.system.shizuku

import android.content.Context
import android.system.Os
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

@Keep
class TurboUserService() : ITurboUserService.Stub() {

    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun uid(): Int = Os.getuid()

    override fun pid(): Int = Os.getpid()

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) return ""
        return try {
            val process = ProcessBuilder("sh", "-c", "($command) 2>&1")
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(12, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                return "__WA_TIMEOUT__"
            }

            process.inputStream.bufferedReader().use { it.readText() }.trimEnd()
        } catch (t: Throwable) {
            "__WA_ERROR__:${t.javaClass.simpleName}:${t.message.orEmpty()}"
        }
    }
}
