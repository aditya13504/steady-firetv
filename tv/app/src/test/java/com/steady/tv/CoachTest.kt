package com.steady.tv

import com.steady.tv.data.Coach
import com.steady.tv.data.Condition
import com.steady.tv.data.Health
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

    @Test fun kneePainSwapsInSaferMovesAndExplainsWhy() {
        val plan = Coach.plan(Person("k", "Asha", 72, true, conditions = setOf(Condition.KNEE_PAIN)))
        val ids = plan.items.map { it.id }
        assertTrue(ids.toString(), ExerciseId.HANDS_ASSISTED_STAND in ids)
        assertTrue(ids.toString(), ExerciseId.SIT_TO_STAND !in ids && ExerciseId.MINI_SQUAT !in ids)
        assertTrue(ids.toString(), ExerciseId.SEATED_KNEE_EXTENSION in ids)
        assertTrue(plan.reasons.toString(), plan.reasons.any { it.contains("knee pain") })
    }

    @Test fun aWalkingAidMeansSeatedWarmUpFeetTogetherAndNoTimedTest() {
        val ids = Coach.plan(Person("w", "Ravi", 84, false, conditions = setOf(Condition.WALKING_AID))).items.map { it.id }
        assertTrue(ids.toString(), ExerciseId.SEATED_MARCH in ids && ExerciseId.FEET_TOGETHER in ids)
        assertTrue(ids.toString(), ExerciseId.CHAIR_STAND_TEST !in ids && ExerciseId.ONE_LEG_STAND !in ids)
    }

    @Test fun plansNeverIncludeAMoveTheirConditionsRuleOut() {
        val all = Condition.entries
        for (mask in 0 until (1 shl all.size) step 7) {
            val c = all.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
            for (days in 0..6) {
                val p = maggie.copy(conditions = c, history = maggie.history.take(days))
                for (item in Coach.plan(p).items) assertTrue("$c day $days: ${item.id}", Health.whyNot(c, item.id) == null)
            }
        }
    }

    @Test fun extrasChangeFromDayToDay() {
        val plans = (0..3).map { n -> Coach.plan(walter.copy(history = walter.history.take(n))).items.map { it.id }.toSet() }.toSet()
        assertTrue("only ${plans.size} different plans", plans.size >= 3)
    }
}
