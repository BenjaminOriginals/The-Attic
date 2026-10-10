package com.focuslockout.overlay

import android.content.Intent
import com.focuslockout.data.ChallengeType

enum class OverlayMode {
    /** A blocked app came to the foreground; passing grants a temporary grace period for it. */
    BLOCKED_APP,

    /** Launched from the dashboard; passing ends the whole session. */
    EMERGENCY_UNLOCK,
}

/** What the overlay was opened for, decoded from its launch intent. */
data class OverlayRequest(
    val mode: OverlayMode,
    val packageName: String?,
    val appLabel: String?,
    val challengeType: ChallengeType,
) {
    companion object {
        const val EXTRA_MODE = "com.focuslockout.extra.MODE"
        const val EXTRA_PACKAGE = "com.focuslockout.extra.PACKAGE"
        const val EXTRA_APP_LABEL = "com.focuslockout.extra.APP_LABEL"
        const val EXTRA_CHALLENGE = "com.focuslockout.extra.CHALLENGE"

        fun from(intent: Intent?): OverlayRequest {
            val mode = OverlayMode.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_MODE) }
                ?: OverlayMode.BLOCKED_APP
            return OverlayRequest(
                mode = mode,
                packageName = intent?.getStringExtra(EXTRA_PACKAGE),
                appLabel = intent?.getStringExtra(EXTRA_APP_LABEL),
                challengeType = ChallengeType.fromKey(intent?.getStringExtra(EXTRA_CHALLENGE)),
            )
        }
    }
}
