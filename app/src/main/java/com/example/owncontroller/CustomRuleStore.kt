package com.example.owncontroller

import android.content.Context

/** User-created local rule: run a safe notification action when OCR sees matching visible text. */
object CustomRuleStore {
    private const val PREFS = "custom_visual_rules"
    private const val KEY_TRIGGER = "trigger_text"
    private const val KEY_ACTION = "action"

    fun save(context: Context, trigger: String, action: String = ACTION_NOTIFY) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TRIGGER, trigger.trim())
            .putString(KEY_ACTION, action)
            .apply()
    }

    fun trigger(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TRIGGER, "").orEmpty()

    fun action(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTION, ACTION_NOTIFY).orEmpty()

    const val ACTION_NOTIFY = "NOTIFY"
}
