package com.steady.tv

import com.steady.tv.data.Coach
import com.steady.tv.data.DemoHousehold
import com.steady.tv.data.HouseholdJson
import com.steady.tv.data.Person
import com.steady.tv.data.Topics
import com.steady.tv.pose.ExerciseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {
    private val demo = DemoHousehold.create()
    private val maggie = demo.person("maggie")!!
    private val walter = demo.person("walter")!!

    @Test fun targetsRiseAfterTwoGoodSessionsAndStrengthIsRetested() {
        val plan = Coach.plan(maggie)
        assertEquals(12, plan.items.first { it.id == ExerciseId.SIT_TO_STAND }.target)
        assertTrue(plan.items.any { it.id == ExerciseId.CHAIR_STAND_TEST })
        assertTrue("sit to stand reason leads: ${plan.reasons}", plan.reasons.first().contains("sit to stand"))
    }

    @Test fun missedTargetsHoldSteadyAndRecentTestIsNotRepeated() {
        val plan = Coach.plan(walter)
        assertEquals(8, plan.items.first { it.id == ExerciseId.SIT_TO_STAND }.target)
        assertTrue(plan.items.none { it.id == ExerciseId.CHAIR_STAND_TEST })
    }

    @Test fun newPeopleStartGentlyWithAStrengthCheck() {
        val plan = Coach.plan(Person("x", "Ravi", 84, false))
        assertEquals(8, plan.items.first { it.id == ExerciseId.SIT_TO_STAND }.target)
        assertTrue(plan.items.any { it.id == ExerciseId.CHAIR_STAND_TEST })
        assertTrue(plan.reasons.first().startsWith("Welcome"))
    }

    @Test fun householdHabitsPredictWhoIsExercising() {
        assertEquals("maggie", Coach.likely(demo, 9.5)!!.first.id)
        assertEquals("walter", Coach.likely(demo, 18.75)!!.first.id)
    }

    @Test fun householdSurvivesAJsonRoundTrip() {
        val back = HouseholdJson.read(HouseholdJson.write(demo))
        assertEquals(demo.people.map { it.history.size }, back.people.map { it.history.size })
        assertEquals(demo.familyTopic, back.familyTopic)
        assertEquals(8, back.person("maggie")!!.history.first().chairStand)
    }

    @Test fun familyTopicsAreLongAndRandom() {
        val a = Topics.newTopic()
        val b = Topics.newTopic()
        assertTrue(a.matches(Regex("steady-[a-z0-9]{24}")))
        assertTrue(a != b)
    }

    @Test fun rulesNoteLeadsWithTheStrengthCheck() {
        val today = maggie.history.last().copy(chairStand = 11, at = System.currentTimeMillis())
        val p = maggie.copy(history = maggie.history + today)
        val note = Coach.rulesNote(p, today, "Daniel")
        assertTrue(note.summary, note.summary.startsWith("Chair stand check: 11 stands, up from 8, in the usual range"))
    }
}
