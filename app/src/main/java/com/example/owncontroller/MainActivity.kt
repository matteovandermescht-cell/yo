package com.example.owncontroller

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val TARGET_GAME_PACKAGE = "com.example.mygame"
        private const val COMMAND_ACTION = "com.example.mygame.CONTROLLER_COMMAND"
        private const val STATUS_ACTION = "com.example.owncontroller.GAME_STATUS"
        private const val PICK_IMAGE_REQUEST = 4102
        private const val SCREEN_CAPTURE_REQUEST = 4103
    }

    private lateinit var status: TextView
    private lateinit var log: TextView
    private lateinit var visualReport: TextView
    private lateinit var overlaySwitch: Switch
    private lateinit var realtimeReport: TextView
    private lateinit var customRuleInput: EditText
    private lateinit var customRuleStatus: TextView
    private lateinit var chatLog: TextView
    private lateinit var chatInput: EditText
    private var latestRecognizedText = ""
    private var receiverRegistered = false
    private var pendingOverlayEnable = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RealtimeScreenAnalysisService.ACTION_OCR_RESULTS) {
                val lines = intent.getStringExtra(RealtimeScreenAnalysisService.EXTRA_OCR_LINES).orEmpty()
                latestRecognizedText = lines
                realtimeReport.text = if (lines.isBlank()) {
                    "On-device text recognition: no readable text detected"
                } else {
                    "On-device text recognition: ${lines.lines().size} visible text line(s) detected"
                }
            } else if (intent?.action == RealtimeScreenAnalysisService.ACTION_STATUS) {
                val state = intent.getStringExtra(RealtimeScreenAnalysisService.EXTRA_STATE) ?: "UNKNOWN"
                val message = intent.getStringExtra(RealtimeScreenAnalysisService.EXTRA_MESSAGE) ?: "No analysis status"
                realtimeReport.text = "Live analysis [$state]: $message"
                appendLog("Live analysis [$state]: $message")
            } else {
                val message = intent?.getStringExtra("status") ?: "Game replied without a status."
                status.text = "Game status: $message"
                appendLog("Reply: $message")
            }
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
            text = "Offline visual analysis for screenshots. Select an image to measure green and red pixel regions."
            textSize = 15f
            setPadding(0, 8, 0, 18)
        })
        content.addView(TextView(this).apply {
            text = "Visual recognition"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 8)
        })
        addButton(content, "Choose screenshot to analyze") { chooseImage() }
        visualReport = TextView(this).apply {
            text = "No image analyzed yet."
            textSize = 14f
            setPadding(0, 8, 0, 18)
        }
        content.addView(visualReport)
        addButton(content, "Start live screen analysis") { requestRealtimeAnalysis() }
        addButton(content, "Open required Android settings") { openRequiredSettings() }
        addButton(content, "Stop live screen analysis") { stopRealtimeAnalysis() }
        realtimeReport = TextView(this).apply {
            text = "Live analysis: stopped"
            textSize = 14f
            setPadding(0, 8, 0, 18)
        }
        content.addView(realtimeReport)

        content.addView(TextView(this).apply {
            text = "Custom visual rule"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = "Enter text that should trigger an action when it appears visibly on screen. Example: LEVEL COMPLETE"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })
        customRuleInput = EditText(this).apply {
            hint = "Text to recognize"
            setSingleLine(true)
            setText(CustomRuleStore.trigger(this@MainActivity))
        }
        content.addView(customRuleInput)
        addButton(content, "Save rule: notify when text appears") {
            val trigger = customRuleInput.text.toString().trim()
            if (trigger.length < 2) {
                customRuleStatus.text = "Enter at least 2 characters."
            } else {
                CustomRuleStore.save(this, trigger)
                customRuleStatus.text = "Saved: notify when \"$trigger\" appears"
                appendLog("Custom visual rule saved.")
            }
        }
        customRuleStatus = TextView(this).apply {
            text = "Saved rule: " + (CustomRuleStore.trigger(this@MainActivity).ifBlank { "none" })
            textSize = 14f
            setPadding(0, 8, 0, 18)
        }
        content.addView(customRuleStatus)

        content.addView(TextView(this).apply {
            text = "Overlay GUI"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = "Enable the movable, display-only floating menu. Android may ask you to grant Display over other apps permission."
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })
        overlaySwitch = Switch(this).apply {
            text = "Overlay GUI aktivieren"
            isChecked = false
            setOnCheckedChangeListener { _, enabled ->
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                        pendingOverlayEnable = true
                        isChecked = false
                        openOverlaySettings()
                        Toast.makeText(this@MainActivity, "Grant overlay permission, then return here.", Toast.LENGTH_LONG).show()
                    } else startOverlay()
                } else {
                    pendingOverlayEnable = false
                    stopOverlay()
                }
            }
        }
        content.addView(overlaySwitch, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 10 })

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
            text = "Ready. Image analysis is offline and does not send controls to a game."
        }
        content.addView(log)

        content.addView(TextView(this).apply {
            text = "Local assistant chat"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 18, 0, 8)
        })
        content.addView(TextView(this).apply {
            text = "Chat runs on this phone and controls only this app. Try: status, start analysis, stop analysis, open settings, enable overlay, disable overlay, what do you see?, help."
            textSize = 13f
            setPadding(0, 0, 0, 8)
        })
        chatLog = TextView(this).apply {
            textSize = 14f
            text = "Controller: Ready. Type help to see local commands."
            setPadding(0, 8, 0, 12)
        }
        content.addView(chatLog)
        chatInput = EditText(this).apply { hint = "Type a command or question"; setSingleLine(true) }
        content.addView(chatInput)
        addButton(content, "Send to controller") {
            val message = chatInput.text.toString().trim()
            if (message.isNotEmpty()) {
                chatLog.append("\nYou: $message")
                chatInput.text.clear()
                handleLocalChatCommand(message)
            }
        }
        setContentView(scroller)
        if (intent?.action == OverlayService.ACTION_REQUEST_ANALYSIS) {
            requestRealtimeAnalysis()
        }
    }

    private fun handleLocalChatCommand(raw: String) {
        val command = raw.trim().lowercase(java.util.Locale.ROOT)
        val reply = when {
            command in listOf("help", "hilfe", "?") -> "Commands: status; start analysis; stop analysis; open settings; enable overlay; disable overlay; what do you see?"
            command.contains("status") || command.contains("zustand") -> "Live analysis: " + realtimeReport.text + ". Overlay permission: " + (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))
            command.contains("what do you see") || command.contains("was siehst") || command.contains("erkannt") ->
                if (latestRecognizedText.isBlank()) "No readable screen text received yet. Start live analysis and approve screen capture first."
                else "Latest recognized screen text:\n" + latestRecognizedText.lines().take(12).joinToString("\n") { it.substringAfter("|", it).take(100) }
            command.contains("start") && (command.contains("analysis") || command.contains("analyse")) -> { requestRealtimeAnalysis(); "Android's screen-capture confirmation opened. Accept it to start local analysis." }
            (command.contains("stop") || command.contains("beend")) && (command.contains("analysis") || command.contains("analyse") || command.contains("live")) -> { stopRealtimeAnalysis(); "Stop requested for live screen analysis." }
            command.contains("settings") || command.contains("einstellung") || command.contains("berechtigung") -> { openRequiredSettings(); "Opened relevant Android settings. Grant only permissions you want to allow, then return to the app." }
            (command.contains("enable") || command.contains("aktivier") || command.contains("start")) && command.contains("overlay") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) { openOverlaySettings(); "Overlay permission settings opened. Enable 'Display over other apps', then return." }
                else { startOverlay(); "Floating menu start requested." }
            }
            (command.contains("disable") || command.contains("deaktivier") || command.contains("stop")) && command.contains("overlay") -> { stopOverlay(); "Floating menu stopped." }
            else -> "I don't know that command yet. Type help for supported commands."
        }
        chatLog.append("\nController: " + reply)
        appendLog("Chat command: " + raw)
    }
    private fun requestRealtimeAnalysis() {
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
        } catch (e: Exception) {
            realtimeReport.text = "Cannot request screen capture: ${e.message ?: "unknown error"}"
            appendLog("Could not request screen capture permission.")
        }
    }

    private fun stopRealtimeAnalysis() {
        stopService(Intent(this, RealtimeScreenAnalysisService::class.java).setAction(RealtimeScreenAnalysisService.ACTION_STOP))
        realtimeReport.text = "Live analysis: stop requested"
        appendLog("Live screen analysis stop requested.")
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    @Deprecated("Deprecated by Android in favor of Activity Result APIs; retained for compatibility with this project.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, RealtimeScreenAnalysisService::class.java).apply {
                    putExtra(RealtimeScreenAnalysisService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(RealtimeScreenAnalysisService.EXTRA_RESULT_DATA, data)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                    else startService(serviceIntent)
                    realtimeReport.text = "Live analysis: starting…"
                    appendLog("User granted screen capture; starting on-device analysis.")
                } catch (e: Exception) {
                    realtimeReport.text = "Live analysis failed to start: ${e.message ?: "unknown error"}"
                    appendLog("Live analysis start failed.")
                }
            } else {
                realtimeReport.text = "Live analysis: permission denied"
                appendLog("Screen capture permission was not granted.")
            }
            return
        }
        if (requestCode != PICK_IMAGE_REQUEST || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: throw IllegalArgumentException("Could not decode selected image")
            val report = ImageColorAnalyzer.analyze(bitmap, sampleStep = maxOf(1, maxOf(bitmap.width, bitmap.height) / 900))
            visualReport.text = buildString {
                append("Image: ${report.width} × ${report.height}\n")
                append("Analyzed samples: ${report.totalPixels}\n")
                append("Green-like pixels: ${report.greenPixels} (${percent(report.greenFraction)})\n")
                append("Red-like pixels: ${report.redPixels} (${percent(report.redFraction)})\n\n")
                append("Color ranges are approximate. Compare with your screenshots and tune ImageColorAnalyzer.kt if needed.")
            }
            appendLog("Image analyzed: ${report.width}×${report.height}; green ${percent(report.greenFraction)}, red ${percent(report.redFraction)}.")
            bitmap.recycle()
        } catch (e: Exception) {
            visualReport.text = "Image analysis failed: ${e.message ?: "unknown error"}"
            appendLog("Image analysis failed.")
        }
    }

    private fun percent(value: Float): String = String.format(java.util.Locale.US, "%.2f%%", value * 100f)

    private fun startOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            pendingOverlayEnable = true
            overlaySwitch.isChecked = false
            openOverlaySettings()
            return
        }
        try {
            startService(Intent(this, OverlayService::class.java))
            pendingOverlayEnable = false
            appendLog("Display-only floating menu enabled.")
        } catch (e: Exception) {
            overlaySwitch.isChecked = false
            appendLog("Could not start overlay: ${e.message ?: "unknown error"}")
        }
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        appendLog("Overlay GUI disabled; floating menu service stopped.")
    }

    private fun openRequiredSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                openOverlaySettings()
                Toast.makeText(this, "Allow display over other apps, then return to Own Game Controller.", Toast.LENGTH_LONG).show()
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "Check app permissions and notifications. Screen capture is approved separately when you tap Start live screen analysis.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            appendLog("Could not open Android settings: ${e.message ?: "unknown error"}")
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
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

    override fun onResume() {
        super.onResume()
        if (pendingOverlayEnable && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            overlaySwitch.isChecked = true
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(STATUS_ACTION).apply {
            addAction(RealtimeScreenAnalysisService.ACTION_STATUS)
            addAction(RealtimeScreenAnalysisService.ACTION_OCR_RESULTS)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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

    private fun appendLog(message: String) {
        log.append("\n$message")
    }
}
