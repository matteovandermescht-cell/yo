package com.example.owncontroller

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val TARGET_GAME_PACKAGE = "com.example.mygame"
        private const val COMMAND_ACTION = "com.example.mygame.CONTROLLER_COMMAND"
        private const val STATUS_ACTION = "com.example.owncontroller.GAME_STATUS"
    }

    private lateinit var status: TextView
    private lateinit var log: TextView
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("status") ?: "Game replied without a status."
            status.text = "Game status: $message"
            appendLog("Reply: $message")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scroller = ScrollView(this)
        scroller.addView(content)
        content.addView(TextView(this).apply {
            text = "Own Game Controller"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Setup and test controls for your own compatible game."
            textSize = 15f
            setPadding(0, 8, 0, 18)
        })
        content.addView(TextView(this).apply {
            text = "Overlay setup"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = "Allow this app to appear over other apps, then use Show floating menu. The menu is a movable display-only overlay."
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })
        addButton(content, "Set up 'Display over other apps'") { openOverlaySettings() }
        addButton(content, "Show floating menu") { startOverlay() }
        addButton(content, "Hide floating menu") {
            stopService(Intent(this, OverlayService::class.java))
            appendLog("Floating menu stopped.")
        }

        status = TextView(this).apply {
            text = "Game status: waiting for a reply"
            textSize = 16f
            setPadding(0, 16, 0, 16)
        }
        content.addView(status)
        addButton(content, "Connect / ping") { sendCommand("PING") }
        addButton(content, "Start test routine") { sendCommand("START") }
        addButton(content, "Stop test routine") { sendCommand("STOP") }
        addButton(content, "Run primary test action") { sendCommand("PRIMARY_ACTION") }
        content.addView(TextView(this).apply {
            text = "Activity log"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 18, 0, 8)
        })
        log = TextView(this).apply {
            textSize = 13f
            text = "Ready. The floating menu is display-only."
        }
        content.addView(log)
        setContentView(scroller)
    }

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please enable Display over other apps first.", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }
        try {
            startService(Intent(this, OverlayService::class.java))
            appendLog("Floating menu started. Tap the OGC bubble to open or close the menu; drag it to move it.")
        } catch (e: Exception) {
            appendLog("Could not start overlay: ${e.message ?: "unknown error"}")
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            try { startActivity(intent) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
            appendLog("Opened Android overlay permission settings. Enable permission there, then return to this app.")
        } else appendLog("This Android version does not require the overlay permission screen.")
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(STATUS_ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun addButton(parent: LinearLayout, title: String, action: () -> Unit) {
        val button = Button(this).apply { text = title; isAllCaps = false; setOnClickListener { action() } }
        parent.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6 })
    }

    private fun sendCommand(command: String) {
        if (TARGET_GAME_PACKAGE == "com.example.mygame") {
            status.text = "Game status: configure your own game's package first"
            appendLog("Not sent: target package is still the placeholder.")
            return
        }
        val intent = Intent(COMMAND_ACTION).apply {
            setPackage(TARGET_GAME_PACKAGE)
            putExtra("command", command)
            putExtra("reply_package", packageName)
        }
        sendBroadcast(intent)
        appendLog("Sent command: $command")
    }

    private fun appendLog(message: String) { log.append("\n$message") }
}
