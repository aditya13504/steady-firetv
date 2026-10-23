package com.steady.tv

import com.steady.tv.pose.Catalog
import com.steady.tv.pose.DemoPerson
import com.steady.tv.pose.ExerciseId
import com.steady.tv.pose.FallCheck
import com.steady.tv.pose.Figure
import com.steady.tv.pose.Kind
import com.steady.tv.pose.PoseFrame
import com.steady.tv.pose.Tracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Every exercise in the library, checked against its own demo person (built from the coach's keyframes). */
class LibraryTest {
    private val library = Catalog.all.filter { it.kind != Kind.TIMED_TEST }

    @Test fun theLibraryHasTheNewExercises() {
        assertEquals(ExerciseId.entries.size, Catalog.all.size)
        assertTrue(Catalog.all.size >= 28)
    }

    @Test fun everyExerciseReachesItsTargetWhenDoneProperly() {
        for (info in library) {
            val person = DemoPerson(info.id, sloppyEvery = 0)
            val tracker = Tracker.create(info.id)
            var t = 0L
            while (t < 120_000 && !tracker.state.done) {
                tracker.feed(person.frame(t))
                t += 66
            }
            assertTrue("${info.id} reached ${tracker.state.reps} of ${tracker.state.target}", tracker.state.done)
        }
    }

    @Test fun keepingStillCountsNothing() {
        for (info in library) {
            val rest = if (info.sit) Figure.SEATED else Figure.STAND
            val rnd = Random(3)
            val tracker = Tracker.create(info.id)
            var t = 0L
            while (t < 20_000) {
                tracker.feed(PoseFrame(rest.toLandmarks(noise = 0.006f, rnd = rnd), t))
                t += 66
            }
            assertEquals("${info.id} counted while keeping still", 0, tracker.state.reps)
        }
    }

    @Test fun squatsAndStandingUpAreNotMarchSteps() {
        // Found with a real video: both knees rising together in a squat were counted as march steps.
        for (source in listOf(ExerciseId.MINI_SQUAT, ExerciseId.SIT_TO_STAND)) {
            val person = DemoPerson(source, sloppyEvery = 0)
            val tracker = Tracker.create(ExerciseId.MARCH)
            var t = 0L
            while (t < 30_000) { tracker.feed(person.frame(t)); t += 66 }
            assertEquals("$source counted as march steps", 0, tracker.state.reps)
        }
    }

    @Test fun noExerciseLooksLikeAFall() {
        for (info in Catalog.all) {
            val person = DemoPerson(info.id, sloppyEvery = 0)
            val check = FallCheck()
            var t = 0L
            while (t < 30_000) {
                assertFalse("${info.id} raised a fall alarm at $t ms", check.feed(person.frame(t)))
                t += 66
            }
        }
    }
}
