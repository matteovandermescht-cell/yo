// Example receiver to integrate into YOUR own Android game app.
// Change the package and action names to match your app.
package com.example.mygame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GameCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command") ?: return
        val replyPackage = intent.getStringExtra("reply_package") ?: return

        val status = when (command) {
            "PING" -> "Connected"
            "START" -> "Test routine start requested"
            "STOP" -> "Test routine stop requested"
            "PRIMARY_ACTION" -> "Primary test action requested"
            else -> "Unknown command"
        }

        // Connect these cases to your own game's test/debug logic.
        context.sendBroadcast(Intent("com.example.owncontroller.GAME_STATUS").apply {
            setPackage(replyPackage)
            putExtra("status", status)
        })
    }
}
