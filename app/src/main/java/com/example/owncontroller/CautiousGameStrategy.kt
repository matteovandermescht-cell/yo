package com.example.owncontroller

/**
 * Conservative strategy policy for an explicitly integrated own-game/test build.
 *
 * This class does not read another app's screen and does not inject touch input.
 * Neon-Cortex should call decide() with its own authoritative/test game state,
 * then handle the returned intent inside its own game code.
 */
object CautiousGameStrategy {
    enum class Action {
        WAIT_FOR_MATCH,
        RETREAT_TO_SAFETY,
        MOVE_TO_BUSH,
        CAMP_IN_BUSH,
        COLLECT_POWERUP,
        ATTACK_TARGET,
        REPOSITION
    }

    data class GameState(
        val matchStarted: Boolean,
        val playerAlive: Boolean,
        val healthFraction: Float,
        val enemyVisible: Boolean,
        val nearestEnemyDistance: Float,
        val attackRange: Float,
        val lineOfSight: Boolean,
        val nearbyEnemyCount: Int,
        val inBush: Boolean,
        val bushAvailable: Boolean,
        val powerupAvailable: Boolean,
        val powerupDistance: Float,
        val safePowerupDistance: Float
    )

    data class Decision(
        val action: Action,
        val reason: String,
        /** Suggested delay before evaluating again; the host game owns timing. */
        val reevaluateAfterMs: Long
    )

    // Tuned to favor survival and cover over chasing fights.
    private const val LOW_HEALTH = 0.35f
    private const val COMFORTABLE_HEALTH = 0.70f
    private const val DANGER_DISTANCE = 10f
    private const val MIN_ATTACK_HEALTH = 0.55f
    private const val MAX_NEARBY_ENEMIES_TO_ENGAGE = 1

    fun decide(state: GameState): Decision {
        if (!state.matchStarted) {
            return Decision(Action.WAIT_FOR_MATCH, "Wait until the match starts.", 1_000)
        }
        if (!state.playerAlive) {
            return Decision(Action.WAIT_FOR_MATCH, "Player is not alive; wait for the next round.", 1_500)
        }
        if (state.healthFraction <= LOW_HEALTH ||
            (state.enemyVisible && state.nearestEnemyDistance <= DANGER_DISTANCE &&
                state.healthFraction < COMFORTABLE_HEALTH)
        ) {
            return Decision(Action.RETREAT_TO_SAFETY, "Health or nearby threat is too risky; retreat.", 500)
        }

        // Do not chase an enemy just because it is visible.
        val safeToAttack = state.enemyVisible &&
            state.nearestEnemyDistance <= state.attackRange &&
            state.lineOfSight &&
            state.healthFraction >= MIN_ATTACK_HEALTH &&
            state.nearbyEnemyCount <= MAX_NEARBY_ENEMIES_TO_ENGAGE

        if (safeToAttack) {
            return Decision(Action.ATTACK_TARGET, "Attack only a visible, in-range target with clear line of sight and a manageable threat.", 700)
        }

        // Cover has priority over optional loot when it is available.
        if (state.bushAvailable && !state.inBush) {
            return Decision(Action.MOVE_TO_BUSH, "Prefer nearby cover instead of starting a risky fight.", 900)
        }
        if (state.inBush && state.enemyVisible &&
            state.nearestEnemyDistance > state.attackRange
        ) {
            return Decision(Action.CAMP_IN_BUSH, "Stay concealed; do not chase a distant enemy.", 1_000)
        }
        if (state.inBush && !safeToAttack) {
            return Decision(Action.CAMP_IN_BUSH, "Hold cover and reassess before engaging.", 1_000)
        }

        if (state.powerupAvailable &&
            state.powerupDistance <= state.safePowerupDistance &&
            !state.enemyVisible
        ) {
            return Decision(Action.COLLECT_POWERUP, "Collect nearby loot only while the route appears safe.", 900)
        }

        return Decision(Action.REPOSITION, "No safe attack or nearby loot; reposition cautiously.", 1_000)
    }
}
