package app.chessvision

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import app.chessvision.engine.Stockfish
import app.chessvision.overlay.ArrowOverlayService
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val captureRequest = 410
    private lateinit var fen: EditText
    private lateinit var status: TextView
    private lateinit var move: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8,12,9)
        window.navigationBarColor = android.graphics.Color.rgb(8,12,9)
        buildUi()
        if (!Settings.canDrawOverlays(this)) {
            status.text = "Разреши отображение поверх других приложений для стрелки."
        }
    }

    private fun buildUi() {
        val bg = android.graphics.Color.rgb(8,12,9)
        val lime = android.graphics.Color.rgb(190,244,75)
        val white = android.graphics.Color.rgb(241,245,238)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 26, 22, 22)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "CHESSVISION 2"
            textSize = 25f
            setTextColor(lime)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "SCREEN ANALYSIS  •  STOCKFISH 19"
            textSize = 11f
            setTextColor(0xff9aa594.toInt())
            setPadding(0, 4, 0, 18)
        }
        status = TextView(this).apply {
            text = "Готово. Для теста движка используй стартовый FEN."
            setTextColor(white); textSize = 13f; setPadding(0, 8, 0, 12)
        }
        fen = EditText(this).apply {
            setText("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
            setTextColor(white); setHintTextColor(0xff788274.toInt())
            setHint("FEN позиции"); textSize = 13f
            setBackgroundColor(0xff182219.toInt())
            setPadding(12, 10, 12, 10)
            minLines = 3; gravity = Gravity.TOP
        }
        move = TextView(this).apply {
            text = "Лучший ход появится здесь"
            textSize = 20f; setTextColor(lime)
            setPadding(0, 18, 0, 14)
        }
        layout.addView(title); layout.addView(subtitle); layout.addView(status)
        layout.addView(button("ЗАХВАТ ЭКРАНА") { requestCapture() })
        layout.addView(button("РАЗРЕШИТЬ СТРЕЛКУ") { requestOverlayPermission() })
        val label = TextView(this).apply {
            text = "FEN позиции"; setTextColor(white); textSize = 13f
            setPadding(0, 18, 0, 7)
        }
        layout.addView(label); layout.addView(fen)
        layout.addView(button("АНАЛИЗИРОВАТЬ FEN") { analyzeFen() })
        layout.addView(move)
        layout.addView(button("СКРЫТЬ СТРЕЛКУ") {
            stopService(Intent(this, ArrowOverlayService::class.java))
            status.text = "Стрелка скрыта."
        })
        val note = TextView(this).apply {
            text = "Распознавание с экрана экспериментальное. Сначала проверь Stockfish на стартовом FEN. Для тестов используй партии с ботами и учебные позиции."
            setTextColor(0xff899487.toInt()); textSize = 11f; setPadding(0, 20, 0, 0)
        }
        layout.addView(note)
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(0xff101510.toInt())
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffbef44b.toInt()))
        textSize = 12f
        isAllCaps = false
        setOnClickListener { action() }
        val p = LinearLayout.LayoutParams(-1, 52); p.topMargin = 8
        layoutParams = p
    }

    private fun requestCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else status.text = "Разрешение на стрелку уже выдано."
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode); putExtra("data", data)
            }
            startForegroundService(service)
            status.text = "Захват экрана запущен. Оставь его активным и открой шахматную доску."
        }
    }

    private fun analyzeFen() {
        val value = fen.text.toString().trim()
        status.text = "Запуск Stockfish 19…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Stockfish(this@MainActivity).analyze(value, 14) }
            }
            result.onSuccess {
                move.text = "Ход: ${it.bestMove}"
                status.text = it.info.ifBlank { "Анализ завершён." }
                if (it.bestMove.length >= 4 && Settings.canDrawOverlays(this@MainActivity)) {
                    val from = it.bestMove.substring(0,2)
                    val to = it.bestMove.substring(2,4)
                    startService(Intent(this@MainActivity, ArrowOverlayService::class.java).apply {
                        putExtra("from", from); putExtra("to", to)
                    })
                }
            }.onFailure {
                status.text = "Ошибка Stockfish: ${it.message}. Проверь, что workflow добавил движок в APK."
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        executor.shutdown()
        super.onDestroy()
    }
}
