package com.steady.tv

import com.steady.tv.pose.ChairStandNorms
import com.steady.tv.pose.DemoPerson
import com.steady.tv.pose.ExerciseId
import com.steady.tv.pose.FallCheck
import com.steady.tv.pose.Motions
import com.steady.tv.pose.Smoother
import com.steady.tv.pose.Tracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseEngineTest {

    /** Feed the demo person at 15 fps (what the phone sends) through smoothing into a tracker. */
    private fun run(id: ExerciseId, seconds: Int, target: Int = 999, sloppyEvery: Int = 0): Tracker {
        val person = DemoPerson(id, sloppyEvery = sloppyEvery)
        val tracker = Tracker.create(id, target)
        val smoother = Smoother()
        var t = 0L
        while (t < seconds * 1000L) {
            tracker.feed(smoother.apply(person.frame(t)))
            t += 66
        }
        return tracker
    }

    /** Cycles started within the window (the last one may be partial but still contains a rep). */
    private fun cycles(id: ExerciseId, seconds: Int) = Math.ceil(seconds * 1000.0 / Motions.of(id).cycleMs).toInt()

    @Test fun sitToStandCountsEveryCleanStand() {
        val tracker = run(ExerciseId.SIT_TO_STAND, 32)
        val expected = cycles(ExerciseId.SIT_TO_STAND, 32)
        assertTrue("reps=${tracker.state.reps} expected≈$expected", tracker.state.reps in expected - 1..expected)
        assertEquals(0, tracker.cueCounts["stand_tall"] ?: 0)
    }

    @Test fun halfStandsStillCountButGetCoached() {
        val tracker = run(ExerciseId.SIT_TO_STAND, 40, sloppyEvery = 3)
        assertTrue("expected a stand-tall cue, got ${tracker.cueCounts}", (tracker.cueCounts["stand_tall"] ?: 0) >= 2)
    }

    @Test fun chairStandTestStopsAtThirtySeconds() {
        val tracker = run(ExerciseId.CHAIR_STAND_TEST, 45)
        assertTrue(tracker.state.done)
        assertEquals(0L, tracker.state.remainingMs)
        assertTrue("stands=${tracker.state.reps}", tracker.state.reps in 9..13)
    }

    @Test fun heelRaisesCount() {
        val tracker = run(ExerciseId.HEEL_RAISE, 30)
        val expected = cycles(ExerciseId.HEEL_RAISE, 30)
        assertTrue("reps=${tracker.state.reps} expected≈$expected", tracker.state.reps in expected - 2..expected)
    }

    @Test fun marchCountsBothKnees() {
        val tracker = run(ExerciseId.MARCH, 24)
        val expected = 2 * cycles(ExerciseId.MARCH, 24)
        assertTrue("reps=${tracker.state.reps} expected≈$expected", tracker.state.reps in expected - 2..expected)
    }

    @Test fun lazyMarchIsCoachedNotCounted() {
        val tracker = run(ExerciseId.MARCH, 24, sloppyEvery = 1)
        assertEquals(0, tracker.state.reps)
        assertTrue((tracker.cueCounts["knee_higher"] ?: 0) > 0)
    }

    @Test fun sideLegRaisesCountEachSide() {
        val tracker = run(ExerciseId.SIDE_LEG_RAISE, 30)
        val expected = 2 * cycles(ExerciseId.SIDE_LEG_RAISE, 30)
        assertTrue("reps=${tracker.state.reps} expected≈$expected", tracker.state.reps in expected - 2..expected)
    }

    @Test fun oneLegBalanceTimesHoldsAndScoresSteadiness() {
        val tracker = run(ExerciseId.ONE_LEG_STAND, 30, target = 2)
        assertTrue(tracker.state.done)
        assertTrue("best=${tracker.state.bestHoldMs}", tracker.state.bestHoldMs >= 10_000)
        val q = tracker.quality()
        assertNotNull(q)
        assertTrue("steadiness=$q", q!! > 60)
    }

    @Test fun fallCheckFiresOnlyWhenTheBodyStaysHorizontal() {
        val person = DemoPerson(ExerciseId.SIT_TO_STAND, sloppyEvery = 0)
        val check = FallCheck()
        var t = 0L
        while (t < 20_000) {
            assertFalse("false alarm at $t ms", check.feed(person.frame(t)))
            t += 66
        }
        person.falling = true
        val start = t
        while (t < start + 4_000) {
            check.feed(person.frame(t))
            t += 66
        }
        assertTrue(check.alarm)
    }

    @Test fun cdcNorms() {
        assertEquals(10, ChairStandNorms.threshold(76, female = true))
        assertEquals(true, ChairStandNorms.belowAverage(8, 76, female = true))
        assertEquals(false, ChairStandNorms.belowAverage(11, 81, female = false))
        assertEquals(null, ChairStandNorms.threshold(40, female = true))
    }
}
