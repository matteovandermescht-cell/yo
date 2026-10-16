package com.example.owncontroller

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        // Replace with the applicationId of YOUR game.
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
            text = "Sends named test commands only to your configured game package."
            textSize = 15f
            setPadding(0, 8, 0, 18)
        })
        status = TextView(this).apply {
            text = "Game status: waiting for a reply"
            textSize = 16f
            setPadding(0, 8, 0, 16)
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
            text = "Ready. Configure TARGET_GAME_PACKAGE in MainActivity.kt."
        }
        content.addView(log)
        setContentView(scroller)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(STATUS_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
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
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 6 })
    }

    private fun sendCommand(command: String) {
        if (TARGET_GAME_PACKAGE == "com.example.mygame") {
            status.text = "Game status: configure your game's package first"
            appendLog("Not sent: TARGET_GAME_PACKAGE is still the placeholder.")
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

    private fun appendLog(message: String) {
        log.append("\n$message")
    }
}
