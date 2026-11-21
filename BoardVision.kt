package app.chessvision.vision

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min

data class BoardRect(val left: Int, val top: Int, val size: Int)
data class VisionResult(val board: BoardRect?, val fen: String?, val confidence: Float, val message: String)

/**
 * Experimental image-only detector. It locates a square checkerboard by sampling
 * alternating cell luminance. Piece classification is intentionally marked low
 * confidence: it uses silhouette/contrast heuristics and must be validated per theme.
 */
object BoardVision {
    fun analyze(bitmap: Bitmap): VisionResult {
        val rect = findBoard(bitmap) ?: return VisionResult(null, null, 0f, "Шахматная доска не найдена")
        val fen = buildFen(bitmap, rect)
        return VisionResult(rect, fen.first, fen.second, "Экспериментальное распознавание: проверь FEN вручную")
    }

    private fun findBoard(b: Bitmap): BoardRect? {
        val maxSide = min(b.width, b.height)
        val candidates = mutableListOf<Pair<Float, BoardRect>>()
        val steps = 12
        for (size in (maxSide * 0.48).toInt()..(maxSide * 0.98).toInt() step maxOf(8, maxSide / 100)) {
            val cell = size / 8
            if (cell < 12) continue
            val xs = (0..b.width-size step maxOf(4, size/30))
            val ys = (0..b.height-size step maxOf(4, size/30))
            for (y in ys) for (x in xs) {
                var score = 0f
                var count = 0
                for (r in 0 until 8) for (c in 0 until 8) {
                    val px = x + c*cell + cell/2
                    val py = y + r*cell + cell/2
                    val l = luminance(b.getPixel(px, py))
                    if (c < 7) {
                        val right = luminance(b.getPixel(px + cell, py))
                        score += abs(l-right)
                        count++
                    }
                    if (r < 7) {
                        val below = luminance(b.getPixel(px, py + cell))
                        score += abs(l-below)
                        count++
                    }
                }
                val normalized = (score / maxOf(1, count)) / 255f
                if (normalized > 0.035f) candidates.add(normalized to BoardRect(x,y,size))
            }
        }
        return candidates.maxByOrNull { it.first }?.second
    }

    private fun buildFen(b: Bitmap, rect: BoardRect): Pair<String,Float> {
        val cell = rect.size / 8
        val rows = mutableListOf<String>()
        var classified = 0
        for (r in 0 until 8) {
            var empty = 0
            val row = StringBuilder()
            for (c in 0 until 8) {
                val x = rect.left + c*cell
                val y = rect.top + r*cell
                val feature = squareFeature(b, x, y, cell)
                val piece = classify(feature)
                if (piece == null) empty++ else {
                    if (empty > 0) { row.append(empty); empty = 0 }
                    row.append(piece); classified++
                }
            }
            if (empty > 0) row.append(empty)
            rows.add(row.toString())
        }
        // This heuristic is not sufficiently reliable to assert side-to-move or castling rights.
        val fen = rows.joinToString("/") + " w - - 0 1"
        return fen to (classified / 32f).coerceIn(0f,1f)
    }

    private data class Feature(val occupancy: Float, val bright: Float, val dark: Float, val center: Float)
    private fun squareFeature(b: Bitmap, x: Int, y: Int, size: Int): Feature {
        var occupied = 0; var bright = 0; var dark = 0; var center = 0; var n = 0
        for (yy in 1 until 7) for (xx in 1 until 7) {
            val p = b.getPixel(x + xx*size/8, y + yy*size/8)
            val l = luminance(p)
            if (l < 65 || l > 220) occupied++
            if (l > 185) bright++
            if (l < 75) dark++
            if (xx in 2..5 && yy in 2..5 && (l < 65 || l > 220)) center++
            n++
        }
        return Feature(occupied.toFloat()/n, bright.toFloat()/n, dark.toFloat()/n, center.toFloat()/n)
    }
    private fun classify(f: Feature): Char? {
        if (f.occupancy < 0.16f || f.occupancy > 0.88f) return null
        // Color is only guessed from relative brightness; shape is not a trained classifier.
        val white = f.bright > f.dark
        return if (white) 'P' else 'p'
    }
    private fun luminance(color: Int): Int =
        (0.2126*Color.red(color) + 0.7152*Color.green(color) + 0.0722*Color.blue(color)).toInt()
}
