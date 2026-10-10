package com.focuslockout.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingMatcherTest {

    private val target = "Betty Botter bought butter."

    @Test
    fun cleanPrefixHasNoMismatch() {
        assertEquals(-1, TypingMatcher.firstMismatchIndex("", target))
        assertEquals(-1, TypingMatcher.firstMismatchIndex("Betty Bot", target))
        assertEquals(9, TypingMatcher.correctPrefixLength("Betty Bot", target))
    }

    @Test
    fun reportsFirstWrongCharacter() {
        assertEquals(6, TypingMatcher.firstMismatchIndex("Betty botter", target))
        assertEquals(6, TypingMatcher.correctPrefixLength("Betty botter", target))
    }

    @Test
    fun matchingIsCaseAndPunctuationSensitive() {
        assertFalse(TypingMatcher.isComplete("betty Botter bought butter.", target))
        assertFalse(TypingMatcher.isComplete("Betty Botter bought butter", target))
        assertTrue(TypingMatcher.isComplete(target, target))
    }

    @Test
    fun typingPastTheEndIsAMismatch() {
        assertEquals(target.length, TypingMatcher.firstMismatchIndex("$target!", target))
    }

    @Test
    fun largeInsertsLookLikePaste() {
        assertFalse(TypingMatcher.isPasteLike("Betty", "Betty "))
        assertFalse(TypingMatcher.isPasteLike("Betty B", "Betty Botter "))
        assertTrue(TypingMatcher.isPasteLike("", target))
        // Deleting text is never paste.
        assertFalse(TypingMatcher.isPasteLike(target, ""))
    }
}
