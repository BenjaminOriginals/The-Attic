package com.focuslockout.overlay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.focuslockout.R
import com.focuslockout.data.ChallengeType
import com.focuslockout.ui.theme.FocusLockTheme

/**
 * Full-screen lock wall shown over a blocked app (or opened from the dashboard for an emergency
 * unlock). It cannot be dismissed with Back: the only ways out are "back to focus" (home screen)
 * or passing the challenge.
 */
class OverlayActivity : ComponentActivity() {

    private val viewModel: OverlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        viewModel.bind(OverlayRequest.from(intent))
        setContent {
            FocusLockTheme {
                OverlayRoute(
                    viewModel = viewModel,
                    onReturnToFocus = { returnToFocus() },
                    onClose = { finish() },
                    onShare = { text -> shareShame(text) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.bind(OverlayRequest.from(intent))
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        isShowing = false
        super.onPause()
    }

    /** Leaves via the home screen so the blocked app underneath is never revealed. */
    private fun returnToFocus() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(home)
        } catch (e: ActivityNotFoundException) {
            // No launcher to go to; finishing still ends the attempt.
        }
        finish()
    }

    private fun shareShame(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            // Nothing can receive text; the button is a nicety, not a requirement.
        }
    }

    companion object {
        /** True while the overlay is in the resumed state; the accessibility service skips relaunches. */
        @Volatile
        var isShowing: Boolean = false
            private set

        private const val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_NO_ANIMATION

        fun blockedAppIntent(
            context: Context,
            packageName: String,
            appLabel: String,
            challengeType: ChallengeType,
        ): Intent = Intent(context, OverlayActivity::class.java)
            .addFlags(LAUNCH_FLAGS)
            .putExtra(OverlayRequest.EXTRA_MODE, OverlayMode.BLOCKED_APP.name)
            .putExtra(OverlayRequest.EXTRA_PACKAGE, packageName)
            .putExtra(OverlayRequest.EXTRA_APP_LABEL, appLabel)
            .putExtra(OverlayRequest.EXTRA_CHALLENGE, challengeType.key)

        fun emergencyIntent(context: Context, challengeType: ChallengeType): Intent =
            Intent(context, OverlayActivity::class.java)
                .addFlags(LAUNCH_FLAGS)
                .putExtra(OverlayRequest.EXTRA_MODE, OverlayMode.EMERGENCY_UNLOCK.name)
                .putExtra(OverlayRequest.EXTRA_CHALLENGE, challengeType.key)
    }
}
