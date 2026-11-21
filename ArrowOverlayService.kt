package app.chessvision.overlay

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class ArrowOverlayService : Service() {
    private var window: WindowManager? = null
    private var view: ArrowView? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        val from = intent?.getStringExtra("from") ?: "e2"
        val to = intent?.getStringExtra("to") ?: "e4"
        if (view == null) {
            window = getSystemService(WINDOW_SERVICE) as WindowManager
            view = ArrowView(this)
            val type = if (android.os.Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                       else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(-1, -1, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT)
            window?.addView(view, params)
        }
        view?.setMove(from, to)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        view?.let { runCatching { window?.removeView(it) } }
        view = null
        super.onDestroy()
    }
}

private class ArrowView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190,244,75); strokeWidth = 13f; strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE; setShadowLayer(8f,0f,0f,Color.BLACK)
    }
    private var from = "e2"; private var to = "e4"
    fun setMove(a: String, b: String) { from=a; to=b; invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Generic central-board placement for the first prototype; calibration is a known next test.
        val size = minOf(width, height) * 0.82f
        val left = (width-size)/2f
        val top = (height-size)/2f
        fun point(s: String): PointF {
            val file = (s[0]-'a').coerceIn(0,7)
            val rank = (s[1]-'1').coerceIn(0,7)
            return PointF(left+(file+0.5f)*size/8f, top+(7-rank+0.5f)*size/8f)
        }
        val a=point(from); val b=point(to)
        canvas.drawLine(a.x,a.y,b.x,b.y,paint)
        val angle=atan2(b.y-a.y,b.x-a.x); val len=34f
        val path=Path().apply {
            moveTo(b.x,b.y)
            lineTo(b.x-len*cos(angle-0.55), b.y-len*sin(angle-0.55))
            moveTo(b.x,b.y)
            lineTo(b.x-len*cos(angle+0.55), b.y-len*sin(angle+0.55))
        }
        paint.style=Paint.Style.STROKE
        canvas.drawPath(path,paint)
    }
}
