package com.focuslockout.challenge

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeGeneratorTest {

    @Test
    fun answersMatchTheirExpressions() {
        val generator = MathChallengeGenerator(Random(42))
        repeat(5_000) {
            val problem = generator.next()
            assertEquals(problem.expression, problem.answer, Evaluator(problem.expression).evaluate())
        }
    }

    @Test
    fun answersArePositiveAndProblemsAreMultiStep() {
        val generator = MathChallengeGenerator(Random(7))
        repeat(5_000) {
            val problem = generator.next()
            assertTrue(problem.expression, problem.answer > 0)
            val operators = problem.expression.count { it in "+-*" }
            assertTrue(problem.expression, operators >= 2)
        }
    }

    @Test
    fun problemSetHasRequestedSize() {
        assertEquals(5, MathChallengeGenerator(Random(1)).problemSet().size)
        assertEquals(3, MathChallengeGenerator(Random(1)).problemSet(3).size)
    }

    @Test
    fun displayFormUsesTypographicOperators() {
        val problem = MathProblem("(14 * 7) - 19", 79)
        assertEquals("(14 × 7) − 19", problem.displayExpression)
    }

    /** Minimal recursive-descent evaluator for + - * and parentheses. */
    private class Evaluator(private val source: String) {
        private var pos = 0

        fun evaluate(): Int {
            val value = expression()
            skipSpaces()
            check(pos == source.length) { "Trailing input in '$source'" }
            return value
        }

        private fun expression(): Int {
            var value = term()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '+' -> { pos++; value += term() }
                    '-' -> { pos++; value -= term() }
                    else -> return value
                }
            }
        }

        private fun term(): Int {
            var value = factor()
            while (true) {
                skipSpaces()
                if (peek() == '*') {
                    pos++
                    value *= factor()
                } else {
                    return value
                }
            }
        }

        private fun factor(): Int {
            skipSpaces()
            if (peek() == '(') {
                pos++
                val value = expression()
                skipSpaces()
                check(peek() == ')') { "Missing ')' in '$source'" }
                pos++
                return value
            }
            val start = pos
            while (peek()?.isDigit() == true) pos++
            check(pos > start) { "Expected a number at $start in '$source'" }
            return source.substring(start, pos).toInt()
        }

        private fun peek(): Char? = source.getOrNull(pos)

        private fun skipSpaces() {
            while (peek() == ' ') pos++
        }
    }
}
