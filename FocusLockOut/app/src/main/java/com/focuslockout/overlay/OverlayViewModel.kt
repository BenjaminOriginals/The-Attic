package com.focuslockout.overlay

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslockout.R
import com.focuslockout.challenge.MathChallengeGenerator
import com.focuslockout.challenge.MathProblem
import com.focuslockout.challenge.TypingMatcher
import com.focuslockout.data.ChallengeType
import com.focuslockout.data.LockPolicy
import com.focuslockout.data.LockState
import com.focuslockout.focusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OverlayPhase { LOCKED, CHALLENGE, PASSED }

data class TypingState(
    val passage: String = "",
    val input: String = "",
    val pasteBlocked: Boolean = false,
) {
    val mismatchIndex: Int get() = TypingMatcher.firstMismatchIndex(input, passage)
    val correctChars: Int get() = TypingMatcher.correctPrefixLength(input, passage)
    val progress: Float get() = if (passage.isEmpty()) 0f else correctChars.toFloat() / passage.length
}

enum class MathFeedback { NONE, CORRECT, WRONG }

data class MathState(
    val problems: List<MathProblem> = emptyList(),
    val index: Int = 0,
    val input: String = "",
    val feedback: MathFeedback = MathFeedback.NONE,
) {
    val current: MathProblem? get() = problems.getOrNull(index)
}

data class OverlayUiState(
    val request: OverlayRequest? = null,
    val challenge: ChallengeType = ChallengeType.DEFAULT,
    /** The shake penalty was requested but this device has no accelerometer. */
    val shakeFallback: Boolean = false,
    val phase: OverlayPhase = OverlayPhase.LOCKED,
    val typing: TypingState = TypingState(),
    val math: MathState = MathState(),
    val shakeCount: Int = 0,
    /** The unlock (grace grant or session end) has been written; safe to leave the overlay. */
    val unlockCommitted: Boolean = false,
    val shameText: String = "",
)

class OverlayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.focusRepository
    private val passages: List<String> = application.resources.getStringArray(R.array.typing_passages).toList()
    private val mathGenerator = MathChallengeGenerator()
    private val hasAccelerometer = application.getSystemService(SensorManager::class.java)
        ?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    /** Null until DataStore has been read once. */
    val lockState: StateFlow<LockState?> = repository.lockState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Called for every launch intent. Progress survives a relaunch for the same challenge (e.g. the
     * user pressed Home mid-challenge and reopened a blocked app), but never a finished one.
     */
    fun bind(request: OverlayRequest) {
        _uiState.update { current ->
            val wanted = resolveChallenge(request.challengeType)
            val keepProgress = current.request?.mode == request.mode &&
                current.phase != OverlayPhase.PASSED &&
                current.challenge == wanted
            if (keepProgress) current.copy(request = request) else freshState(request, wanted)
        }
    }

    fun startChallenge() {
        _uiState.update { if (it.phase == OverlayPhase.LOCKED) it.copy(phase = OverlayPhase.CHALLENGE) else it }
    }

    fun onTypingInput(raw: String) {
        val state = _uiState.value
        if (state.phase != OverlayPhase.CHALLENGE) return
        val typing = state.typing
        // The passage is one line; a stray Enter is ignored rather than counted as an error.
        val next = raw.replace("\n", "").take(typing.passage.length + MAX_TYPING_OVERFLOW)
        if (TypingMatcher.isPasteLike(typing.input, next)) {
            _uiState.update { it.copy(typing = it.typing.copy(pasteBlocked = true)) }
            return
        }
        _uiState.update { it.copy(typing = it.typing.copy(input = next, pasteBlocked = false)) }
        if (TypingMatcher.isComplete(next, typing.passage)) onChallengePassed()
    }

    fun onMathInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(MAX_ANSWER_DIGITS)
        _uiState.update { it.copy(math = it.math.copy(input = digits, feedback = MathFeedback.NONE)) }
    }

    fun submitMathAnswer() {
        val state = _uiState.value
        if (state.phase != OverlayPhase.CHALLENGE) return
        val math = state.math
        val problem = math.current ?: return
        val answer = math.input.toIntOrNull() ?: return

        if (answer == problem.answer) {
            val nextIndex = math.index + 1
            _uiState.update {
                it.copy(math = math.copy(index = nextIndex, input = "", feedback = MathFeedback.CORRECT))
            }
            if (nextIndex >= math.problems.size) onChallengePassed()
        } else {
            // One miss and the whole streak starts over with fresh problems.
            _uiState.update {
                it.copy(
                    math = MathState(problems = mathGenerator.problemSet(), feedback = MathFeedback.WRONG),
                )
            }
        }
    }

    fun onShake() {
        val state = _uiState.value
        if (state.phase != OverlayPhase.CHALLENGE || state.challenge != ChallengeType.SHAKE) return
        val count = state.shakeCount + 1
        _uiState.update { it.copy(shakeCount = count) }
        if (count >= LockPolicy.SHAKE_TARGET) onChallengePassed()
    }

    private fun onChallengePassed() {
        val state = _uiState.value
        val request = state.request ?: return
        if (state.phase == OverlayPhase.PASSED) return
        val passedAt = System.currentTimeMillis()
        _uiState.update { it.copy(phase = OverlayPhase.PASSED, unlockCommitted = false) }

        viewModelScope.launch {
            val lock = repository.snapshot()
            when (request.mode) {
                OverlayMode.BLOCKED_APP ->
                    request.packageName?.let { repository.grantGrace(it, LockPolicy.GRACE_PERIOD_MILLIS, passedAt) }
                OverlayMode.EMERGENCY_UNLOCK -> repository.endSessionNow()
            }
            val shame = shameText(request, lock.elapsedMinutes(passedAt))
            _uiState.update { it.copy(unlockCommitted = true, shameText = shame) }
        }
    }

    private fun shameText(request: OverlayRequest, minutes: Int): String {
        val resources = getApplication<Application>().resources
        return when (request.mode) {
            OverlayMode.BLOCKED_APP -> {
                val appName = request.appLabel ?: request.packageName ?: resources.getString(R.string.overlay_unknown_app)
                resources.getQuantityString(R.plurals.shame_blocked_app, minutes, minutes, appName)
            }
            OverlayMode.EMERGENCY_UNLOCK ->
                resources.getQuantityString(R.plurals.shame_emergency, minutes, minutes)
        }
    }

    private fun resolveChallenge(requested: ChallengeType): ChallengeType =
        if (requested == ChallengeType.SHAKE && !hasAccelerometer) ChallengeType.MATH else requested

    private fun freshState(request: OverlayRequest, challenge: ChallengeType) = OverlayUiState(
        request = request,
        challenge = challenge,
        shakeFallback = challenge != request.challengeType,
        // An emergency unlock is a deliberate choice: skip the lock screen and go straight in.
        phase = if (request.mode == OverlayMode.EMERGENCY_UNLOCK) OverlayPhase.CHALLENGE else OverlayPhase.LOCKED,
        typing = TypingState(passage = passages.randomOrNull().orEmpty()),
        math = MathState(problems = mathGenerator.problemSet()),
    )

    private companion object {
        const val MAX_TYPING_OVERFLOW = 20
        const val MAX_ANSWER_DIGITS = 6
    }
}
