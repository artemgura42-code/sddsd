package app.chessvision.engine

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

data class EngineResult(val bestMove: String, val info: String)

class Stockfish(private val context: Context) {
    fun analyze(fen: String, depth: Int): EngineResult {
        require(fen.split(" ").size >= 4) { "Некорректный FEN: ожидается минимум 4 поля" }
        val dir = File(context.filesDir, "stockfish").apply { mkdirs() }
        val engine = File(dir, "stockfish")
        val network = File(dir, "nn-1a298aa575a0.nnue")
        if (!engine.exists()) copyAssetOrFail("stockfish", engine)
        if (!network.exists()) copyAssetOrFail("nn-1a298aa575a0.nnue", network)
        engine.setExecutable(true, true)
        val process = ProcessBuilder(engine.absolutePath)
            .directory(dir).redirectErrorStream(true).start()
        val writer = process.outputStream.bufferedWriter()
        val reader = process.inputStream.bufferedReader()
        fun send(s: String) { writer.write(s); writer.newLine(); writer.flush() }
        send("uci")
        val uci = readUntil(reader, "uciok", 10000)
        send("isready")
        readUntil(reader, "readyok", 10000)
        send("position fen $fen")
        send("go depth ${depth.coerceIn(1,22)}")
        val output = readUntil(reader, "bestmove", 60000)
        send("quit")
        process.waitFor(2, TimeUnit.SECONDS)
        val line = (output + "\n" + uci).lineSequence().firstOrNull { it.startsWith("bestmove ") }
            ?: throw IllegalStateException("Движок не вернул bestmove: $output")
        val best = line.split(" ").getOrNull(1) ?: throw IllegalStateException("Пустой bestmove")
        return EngineResult(best, output.lineSequence().lastOrNull { it.startsWith("info ") } ?: "Stockfish 19")
    }

    private fun readUntil(reader: java.io.BufferedReader, token: String, timeoutMs: Long): String {
        val end = System.currentTimeMillis() + timeoutMs
        val out = StringBuilder()
        while (System.currentTimeMillis() < end) {
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                out.appendLine(line)
                if (line.contains(token)) return out.toString()
            } else Thread.sleep(30)
        }
        throw IllegalStateException("Тайм-аут ожидания $token")
    }

    private fun copyAssetOrFail(name: String, target: File) {
        context.assets.open(name).use { input -> target.outputStream().use(input::copyTo) }
    }
}
