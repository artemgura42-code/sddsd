package app.chessvision

import android.app.*
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import app.chessvision.vision.BoardVision
import java.nio.ByteBuffer

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastScanAt = 0L
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("data", Intent::class.java)
                            else intent.getParcelableExtra("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(code, data)
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay("ChessVisionCapture", metrics.widthPixels,
            metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, handler)
        reader?.setOnImageAvailableListener({ source ->
            val now = System.currentTimeMillis()
            if (now - lastScanAt < 1400L) {
                source.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastScanAt = now
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val padded = Bitmap.createBitmap(image.width + rowPadding/pixelStride, image.height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                val result = BoardVision.analyze(cropped)
                val broadcast = Intent("app.chessvision.FRAME_ANALYSIS").apply {
                    putExtra("message", result.message)
                    putExtra("fen", result.fen ?: "")
                    putExtra("confidence", result.confidence)
                }
                sendBroadcast(broadcast)
                padded.recycle(); cropped.recycle()
            } catch (_: Exception) {
            } finally { image.close() }
        }, handler)
        return START_STICKY
    }

    private fun createNotification() {
        val channel = "chessvision_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channel, "ChessVision screen capture", NotificationManager.IMPORTANCE_LOW))
        }
        val n = Notification.Builder(this, channel).setContentTitle("ChessVision")
            .setContentText("Захват экрана активен").setSmallIcon(android.R.drawable.ic_menu_camera).build()
        startForeground(22, n)
    }

    override fun onDestroy() {
        reader?.close(); display?.release(); projection?.stop()
        super.onDestroy()
    }
}
