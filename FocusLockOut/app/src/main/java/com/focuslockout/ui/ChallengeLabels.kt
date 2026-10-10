package com.focuslockout.ui

import androidx.annotation.StringRes
import com.focuslockout.R
import com.focuslockout.data.ChallengeType

@get:StringRes
val ChallengeType.titleRes: Int
    get() = when (this) {
        ChallengeType.TYPING -> R.string.challenge_typing_title
        ChallengeType.SHAKE -> R.string.challenge_shake_title
        ChallengeType.MATH -> R.string.challenge_math_title
    }

@get:StringRes
val ChallengeType.descriptionRes: Int
    get() = when (this) {
        ChallengeType.TYPING -> R.string.challenge_typing_description
        ChallengeType.SHAKE -> R.string.challenge_shake_description
        ChallengeType.MATH -> R.string.challenge_math_description
    }
