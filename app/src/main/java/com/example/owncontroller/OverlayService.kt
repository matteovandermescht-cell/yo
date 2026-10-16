package com.example.owncontroller

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    private var autoPlayEnabled = false
    private var autoPlayStatus: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) showBubble()
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showBubble() {
        val view = TextView(this).apply {
            text = "OGC"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(35, 95, 180))
            contentDescription = "Open Own Game Controller menu"
        }
        bubbleParams = WindowManager.LayoutParams(
            dp(56), dp(56), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16); y = dp(180)
        }
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = bubbleParams.x; startY = bubbleParams.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - touchX) > dp(8) || kotlin.math.abs(event.rawY - touchY) > dp(8)
                    if (!moved) togglePanel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = startX + (event.rawX - touchX).toInt()
                    bubbleParams.y = startY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, bubbleParams)
                    true
                }
                else -> true
            }
        }
        bubble = view
        windowManager.addView(view, bubbleParams)
    }

    private fun togglePanel() {
        if (panel != null) {
            removePanel()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(245, 247, 250))
        }
        layout.addView(TextView(this).apply {
            text = "Own Game Controller"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
        })
        layout.addView(TextView(this).apply {
            text = "Live vision · own-game prototype"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(6), 0, dp(8))
        })
        autoPlayStatus = TextView(this).apply {
            text = "Auto Play: not connected to a game"
            textSize = 12f
            setTextColor(Color.rgb(150, 70, 20))
            setPadding(0, 0, 0, dp(8))
        }
        layout.addView(autoPlayStatus)
        layout.addView(Button(this).apply {
            text = if (autoPlayEnabled) "Stop Auto Play" else "Enable Auto Play"
            setOnClickListener {
                autoPlayEnabled = !autoPlayEnabled
                text = if (autoPlayEnabled) "Stop Auto Play" else "Enable Auto Play"
                autoPlayStatus?.text = if (autoPlayEnabled) {
                    "Requested · game bridge and live input are not connected"
                } else {
                    "Stopped · no game actions are being sent"
                }
                autoPlayStatus?.setTextColor(
                    if (autoPlayEnabled) Color.rgb(150, 70, 20) else Color.DKGRAY
                )
            }
        })
        layout.addView(Button(this).apply {
            text = "Close menu"
            setOnClickListener { removePanel() }
        })
        layout.addView(Button(this).apply {
            text = "Hide floating button"
            setOnClickListener { stopSelf() }
        })
        panelParams = WindowManager.LayoutParams(
            dp(260), WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(80); y = dp(180)
        }
        panel = layout
        windowManager.addView(layout, panelParams)
    }

    private fun removePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
        autoPlayStatus = null
    }

    override fun onDestroy() {
        removePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.owncontroller.STOP_OVERLAY"
    }
}
