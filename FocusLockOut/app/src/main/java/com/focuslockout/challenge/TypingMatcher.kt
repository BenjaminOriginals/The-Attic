package com.focuslockout.challenge

/** Exact, character-for-character comparison used by the typing penalty. */
object TypingMatcher {

    /**
     * Largest single edit accepted from the keyboard. Word suggestions stay under this; pasting a
     * passage (or a big chunk of one) does not.
     */
    const val MAX_CHARS_PER_EDIT = 16

    /**
     * Index of the first character of [input] that deviates from [target], or -1 when [input] is a
     * clean prefix of [target]. Typing past the end of [target] reports `target.length`.
     */
    fun firstMismatchIndex(input: String, target: String): Int {
        val limit = minOf(input.length, target.length)
        for (i in 0 until limit) {
            if (input[i] != target[i]) return i
        }
        return if (input.length > target.length) target.length else -1
    }

    /** Number of leading characters of [input] that already match [target]. */
    fun correctPrefixLength(input: String, target: String): Int {
        val mismatch = firstMismatchIndex(input, target)
        return if (mismatch == -1) input.length else mismatch
    }

    fun isComplete(input: String, target: String): Boolean = input == target

    /** True when an edit inserts more text at once than a human can type in one keystroke/suggestion. */
    fun isPasteLike(previous: String, next: String): Boolean = next.length - previous.length > MAX_CHARS_PER_EDIT
}
