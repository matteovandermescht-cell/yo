package com.example.owncontroller

/**
 * QA-only event executor.
 *
 * Records validated events for assertions and test reporting.
 * It does not inject screen touches or execute gameplay actions.
 */
class QaActionExecutor(
    private val recordResult: (QaTriggerEvent) -> Unit
) : QaTriggerCallback {

    private val processed = LinkedHashSet<String>()

    @Synchronized
    override fun onTriggerReceived(event: QaTriggerEvent) {
        val key = "${event.type}:${event.timestampMs}"

        // Each confirmed event is recorded at most once.
        if (!processed.add(key)) return

        // Keep memory bounded during longer QA sessions.
        while (processed.size > 512) {
            val oldest = processed.first()
            processed.remove(oldest)
        }

        recordResult(event)
    }

    @Synchronized
    fun reset() {
        processed.clear()
    }
}
