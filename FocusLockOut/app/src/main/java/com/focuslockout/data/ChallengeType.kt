package com.focuslockout.data

/** The friction challenge a user must complete to get past the lock. [key] is the persisted value. */
enum class ChallengeType(val key: String) {
    TYPING("TYPING"),
    SHAKE("SHAKE"),
    MATH("MATH");

    companion object {
        val DEFAULT = TYPING

        fun fromKey(key: String?): ChallengeType = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
