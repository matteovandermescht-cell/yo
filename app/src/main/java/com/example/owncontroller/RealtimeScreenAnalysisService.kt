package com.example.owncontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.view.WindowManager
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.max

/**
 * User-consented, on-device live screen color metrics for QA.
 * This is a heuristic analyzer, not an AI model. Frames are analyzed in memory and not saved.
 */
class RealtimeScreenAnalysisService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var lastBroadcastAt = 0L
    private var frameCount = 0L
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for screen capture permission"))
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) {
            publish("ERROR", "Screen capture permission data missing")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startCapture(resultCode, resultData)
        } catch (e: Exception) {
            publish("ERROR", "Could not start capture: ${e.message ?: "unknown error"}")
            stopCapture()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        val metrics = resources.displayMetrics
        val width = max(1, metrics.widthPixels)
        val height = max(1, metrics.heightPixels)
        val density = metrics.densityDpi
        worker = HandlerThread("RealtimeScreenAnalyzer").also { it.start() }
        workerHandler = Handler(worker!!.looper)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                frameCount++
                analyzeFrame(image)
                if (frameCount % 4L == 0L) {
                    val frame = image
                    val input = InputImage.fromMediaImage(frame, 0)
                    textRecognizer.process(input)
                        .addOnSuccessListener { result ->
                            val encoded = result.textBlocks.flatMap { block ->
                                block.lines.mapNotNull { line ->
                                    val box = line.boundingBox ?: return@mapNotNull null
                                    val clean = line.text.replace("|", " ").replace("\n", " ").take(90)
                                    "${box.left},${box.top},${box.right},${box.bottom}|$clean"
                                }
                            }.take(40).joinToString("\n")
                            publishDetections(encoded, frame.width, frame.height)
                        }
                        .addOnFailureListener { error ->
                            publish("OCR_WARNING", "Text recognition failed: ${error.message ?: "unknown error"}")
                        }
                        .addOnCompleteListener {
                            frame.close()
                        }
                    image = null
                }
            } catch (e: Exception) {
                publish("ERROR", "Frame analysis failed: ${e.message ?: "unknown error"}")
            } finally {
                image?.close()
            }
        }, workerHandler)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                publish("STOPPED", "Screen capture stopped by Android or user")
                stopCapture()
                stopSelf()
            }
        }, workerHandler)
        virtualDisplay = projection?.createVirtualDisplay(
            "OwnGameController-QA",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, workerHandler
        )
        publish("RUNNING", "Live analysis started; frames are processed locally")
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID, buildNotification("Live screen analysis is running")
        )
    }

    private fun analyzeFrame(image: Image) {
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val rowPaddingPixels = (rowStride - pixelStride * width) / pixelStride
        val hsv = FloatArray(3)
        var green = 0L
        var red = 0L
        var samples = 0L
        val step = max(4, max(width, height) / 180)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val index = y * rowStride + x * pixelStride
                if (index + 2 < buffer.limit()) {
                    val r = buffer.get(index).toInt() and 0xff
                    val g = buffer.get(index + 1).toInt() and 0xff
                    val b = buffer.get(index + 2).toInt() and 0xff
                    Color.RGBToHSV(r, g, b, hsv)
                    if (hsv[1] >= 0.25f && hsv[2] >= 0.20f) {
                        if (hsv[0] in 70f..170f) green++
                        if (hsv[0] <= 20f || hsv[0] >= 340f) red++
                    }
                    samples++
                }
                x += step
            }
            y += step
        }
        val now = System.currentTimeMillis()
        if (now - lastBroadcastAt >= 1000L) {
            lastBroadcastAt = now
            val message = "Live frames: $frameCount · green: ${pct(green, samples)}% · red: ${pct(red, samples)}%"
            publish("RUNNING", message)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
        }
    }

    private fun publishDetections(encoded: String, width: Int, height: Int) {
        sendBroadcast(Intent(ACTION_OCR_RESULTS).setPackage(packageName).apply {
            putExtra(EXTRA_OCR_LINES, encoded)
            putExtra(EXTRA_FRAME_WIDTH, width)
            putExtra(EXTRA_FRAME_HEIGHT, height)
        })
        if (encoded.isBlank()) publish("RUNNING", "OCR active: no readable text detected")
        else publish("RUNNING", "OCR detected ${encoded.lines().size} text line(s); showing screen labels")
    }

    private fun pct(count: Long, total: Long): String =
        if (total == 0L) "0.0" else String.format(java.util.Locale.US, "%.1f", count * 100.0 / total)

    private fun publish(state: String, message: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).apply {
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_MESSAGE, message)
        })
    }

    private fun buildNotification(message: String): Notification {
        val channelId = "realtime_screen_analysis"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Live QA screen analysis", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Own Game Controller")
            .setContentText(message.take(120))
            .setOngoing(true)
            .build()
    }

    private fun stopCapture() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        try { worker?.quitSafely() } catch (_: Exception) {}
        worker = null
        workerHandler = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        try { textRecognizer.close() } catch (_: Exception) {}
        stopCapture()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.owncontroller.STOP_REALTIME_ANALYSIS"
        const val ACTION_STATUS = "com.example.owncontroller.REALTIME_ANALYSIS_STATUS"
        const val ACTION_OCR_RESULTS = "com.example.owncontroller.OCR_RESULTS"
        const val EXTRA_OCR_LINES = "ocr_lines"
        const val EXTRA_FRAME_WIDTH = "frame_width"
        const val EXTRA_FRAME_HEIGHT = "frame_height"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"
        private const val NOTIFICATION_ID = 5107
    }
}
