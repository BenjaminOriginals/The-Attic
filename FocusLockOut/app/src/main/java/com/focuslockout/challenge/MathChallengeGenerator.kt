package com.focuslockout.challenge

import com.focuslockout.data.LockPolicy
import kotlin.random.Random

/** A multi-step arithmetic problem. [expression] is plain ASCII; every answer is a positive integer. */
data class MathProblem(val expression: String, val answer: Int) {
    /** Typographic form for display: real multiplication and minus signs. */
    val displayExpression: String
        get() = expression.replace("*", "×").replace(" - ", " − ")
}

/**
 * Generates mental-arithmetic problems in the spirit of `(14 * 7) - 19`: two operations, two-digit
 * operands, no division and never a negative result (number keypads often lack a minus key).
 */
class MathChallengeGenerator(private val random: Random = Random.Default) {

    fun problemSet(count: Int = LockPolicy.MATH_STREAK_TARGET): List<MathProblem> = List(count) { next() }

    fun next(): MathProblem = when (random.nextInt(TEMPLATE_COUNT)) {
        0 -> {
            val a = pick(6..19)
            val b = pick(3..12)
            val c = pick(2 until a * b)
            MathProblem("($a * $b) - $c", a * b - c)
        }
        1 -> {
            val a = pick(5..40)
            val b = pick(5..40)
            val c = pick(3..9)
            MathProblem("($a + $b) * $c", (a + b) * c)
        }
        2 -> {
            val a = pick(3..15)
            val b = pick(3..15)
            val c = pick(2..12)
            val d = pick(2..12)
            MathProblem("$a * $b + $c * $d", a * b + c * d)
        }
        3 -> {
            val a = pick(20..60)
            val b = pick(2 until a)
            val c = pick(2..9)
            val d = pick(1..50)
            MathProblem("($a - $b) * $c + $d", (a - b) * c + d)
        }
        else -> {
            val a = pick(3..9)
            val b = pick(4..25)
            val c = pick(4..25)
            val d = pick(1 until a * (b + c))
            MathProblem("$a * ($b + $c) - $d", a * (b + c) - d)
        }
    }

    private fun pick(range: IntRange): Int = range.random(random)

    private companion object {
        const val TEMPLATE_COUNT = 5
    }
}
