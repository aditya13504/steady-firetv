package com.steady.tv.pose

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The wider exercise library. Each move is described by one body signal measured against the body's
 * own ruler (torso length, shoulder or hip width), so it works at any distance from the phone, and by
 * coach keyframes. The same keyframes drive the demo person, so every move is checked by the tests.
 */
object MoreExercises {
    private fun info(id: ExerciseId, title: String, how: String, kind: Kind, target: Int, unit: String, chair: Boolean, benefit: String, category: Category, sit: Boolean = false, holdSeconds: Int = 10) =
        ExerciseInfo(id, title, how, kind, target, unit, chair, benefit, category, sit, holdSeconds)

    val all = listOf(
        info(ExerciseId.MINI_SQUAT, "Mini squats", "Hold the chair. Bend your knees a little, as if to sit, then straighten up tall.", Kind.REPS, 10, "squats", true, "Strong thighs make stairs and kerbs easier.", Category.STRENGTH),
        info(ExerciseId.HANDS_ASSISTED_STAND, "Sit to stand, hands helping", "Hands on your thighs or the armrests. Push and stand up, then sit back down slowly.", Kind.REPS, 8, "stands", true, "A gentler way to build standing strength.", Category.STRENGTH, sit = true),
        info(ExerciseId.ARMS_CROSSED_STAND, "Sit to stand, arms crossed", "Cross your arms over your chest. Stand all the way up without using your hands, then sit slowly.", Kind.REPS, 8, "stands", true, "The next step up once sit to stand feels easy.", Category.STRENGTH, sit = true),
        info(ExerciseId.SEATED_KNEE_EXTENSION, "Seated knee straightening", "Sit tall. Straighten one knee until your leg is level, hold a moment, then lower. Switch legs.", Kind.REPS, 10, "lifts", true, "Strengthens the thigh muscle that protects your knee.", Category.STRENGTH, sit = true),
        info(ExerciseId.STANDING_KNEE_CURL, "Standing knee bends", "Hold the chair. Bend one knee to bring your heel up behind you, then lower. Switch legs.", Kind.REPS, 10, "bends", true, "Strong hamstrings help you walk with longer steps.", Category.STRENGTH),
        info(ExerciseId.SEATED_MARCH, "Seated march", "Sit tall near the front of the chair. Lift one knee, then the other, like marching.", Kind.REPS, 20, "steps", true, "Warms up your hips without standing.", Category.MOVE, sit = true),
        info(ExerciseId.SEATED_KNEES_OUT, "Seated knee opens", "Sit tall with feet flat. Open your knees out to the sides, then bring them back together.", Kind.REPS, 10, "opens", true, "Works the hip muscles that keep you steady.", Category.STRENGTH, sit = true),
        info(ExerciseId.SIDE_STEP, "Side steps", "Hold the chair. Step one foot out to the side, then bring it back. Switch sides.", Kind.REPS, 10, "steps", true, "Practises the sideways step that saves a fall.", Category.BALANCE),
        info(ExerciseId.FRONT_ARM_RAISE, "Front arm raises", "Sit tall. Lift both arms in front of you to shoulder height, then lower slowly.", Kind.REPS, 10, "raises", true, "Keeps reaching into cupboards easy.", Category.ARMS, sit = true),
        info(ExerciseId.SIDE_ARM_RAISE, "Side arm raises", "Stand tall. Lift both arms out to the sides to shoulder height, then lower slowly.", Kind.REPS, 10, "raises", false, "Strong shoulders help you push up and catch yourself.", Category.ARMS),
        info(ExerciseId.ARM_CURL, "Arm curls", "Sit tall, elbows by your sides. Bend your elbows to bring your hands up to your shoulders, then lower.", Kind.REPS, 10, "curls", true, "Makes carrying shopping easier.", Category.ARMS, sit = true),
        info(ExerciseId.OVERHEAD_REACH, "Overhead reach", "Stand tall. Reach both arms up above your head, then lower them to your sides.", Kind.REPS, 10, "reaches", false, "Opens the chest and helps you stand taller.", Category.ARMS),
        info(ExerciseId.SHOULDER_SHRUG, "Shoulder shrugs", "Sit tall. Lift your shoulders up towards your ears, then let them drop.", Kind.REPS, 10, "shrugs", true, "Eases stiff shoulders and neck.", Category.MOBILITY, sit = true),
        info(ExerciseId.FEET_TOGETHER, "Feet together stand", "Hold the chair. Stand with your feet side by side, touching, and hold for 10 seconds.", Kind.HOLD, 2, "holds", true, "The first step in rebuilding balance.", Category.BALANCE),
        info(ExerciseId.TANDEM_STANCE, "Heel-to-toe stand", "Hold the chair. Put one foot right in front of the other, heel to toe, and hold for 10 seconds.", Kind.HOLD, 2, "holds", true, "Trains the balance you need on narrow paths.", Category.BALANCE),
        info(ExerciseId.WEIGHT_SHIFT, "Side to side sway", "Hold the chair, feet apart. Shift your weight over one foot, then over the other.", Kind.REPS, 10, "shifts", true, "Teaches your body where its balance point is.", Category.BALANCE),
        info(ExerciseId.KNEE_LIFT_HOLD, "Knee lift and hold", "Hold the chair. Lift one knee and hold it up for 5 seconds. Lower, then switch legs.", Kind.HOLD, 4, "holds", true, "Balance and hip strength together.", Category.BALANCE, holdSeconds = 5),
        info(ExerciseId.HEAD_TURN, "Head turns", "Sit tall. Slowly turn your head to look over one shoulder, then the other.", Kind.REPS, 10, "turns", true, "Keeps your neck free for checking traffic.", Category.MOBILITY, sit = true),
        info(ExerciseId.SIDE_BEND, "Side bends", "Stand tall and hold the chair. Lean gently to one side, back to the middle, then the other side.", Kind.REPS, 10, "bends", true, "Stretches the sides of your body.", Category.MOBILITY),
        info(ExerciseId.TRUNK_TWIST, "Seated twists", "Sit tall, arms crossed. Turn your shoulders to one side, back to the middle, then the other side.", Kind.REPS, 10, "twists", true, "Keeps your back moving so turning feels easy.", Category.MOBILITY, sit = true),
        info(ExerciseId.HIP_HINGE, "Hip hinges", "Hold the chair. Keep your back straight and tip forward from the hips, then stand tall.", Kind.REPS, 10, "hinges", true, "Teaches safe bending for picking things up.", Category.MOBILITY),
        info(ExerciseId.SEATED_JACKS, "Seated jacks", "Sit tall. Raise your arms up overhead and open your knees, then bring them back. Keep a steady rhythm.", Kind.REPS, 20, "jacks", true, "Gets your heart going safely while seated.", Category.MOVE, sit = true)
    )

    private val ARMS = listOf(Lm.L_SHOULDER, Lm.R_SHOULDER, Lm.L_ELBOW, Lm.R_ELBOW, Lm.L_WRIST, Lm.R_WRIST)
    private val HEAD = listOf(Lm.NOSE, Lm.L_SHOULDER, Lm.R_SHOULDER)
    private val BODY = Lm.CORE + Lm.LEGS

    private fun PoseFrame.of(side: Int, left: Int, right: Int) = this[if (side == 0) left else right]
    /** Wrist height above the shoulder, in torso lengths (about -1 with arms down, 0 at shoulder height). */
    private fun PoseFrame.wristLift(side: Int) = (of(side, Lm.L_SHOULDER, Lm.R_SHOULDER).y - of(side, Lm.L_WRIST, Lm.R_WRIST).y) / torso
    private val PoseFrame.hipWidth get() = abs(this[Lm.L_HIP].x - this[Lm.R_HIP].x).coerceAtLeast(1e-3f)
    private val PoseFrame.ankleMid get() = mid(Lm.L_ANKLE, Lm.R_ANKLE)

    fun tracker(id: ExerciseId, target: Int): Tracker {
        val i = Catalog.info(id)
        return when (id) {
            ExerciseId.MINI_SQUAT -> Moves(i, target, Move(BODY, calibrate = true, go = 0.15f, back = 0.06f, phase = "Bend a little, then stand tall", further = "Bend your knees a little more, if it feels OK.", settle = "Stand tall and hold the chair") { f, _ -> -(f.kneeMidY - f.hipMid.y) / f.torso })
            ExerciseId.SEATED_KNEE_EXTENSION -> Moves(i, target, Move(BODY, sides = 2, go = -0.3f, back = -0.55f, phase = "Straighten one knee, then lower", further = "Straighten your knee a little more.", oneAtATime = true) { f, s -> -(f.of(s, Lm.L_ANKLE, Lm.R_ANKLE).y - f.of(s, Lm.L_KNEE, Lm.R_KNEE).y) / f.torso })
            ExerciseId.STANDING_KNEE_CURL -> Moves(i, target, Move(BODY, sides = 2, go = -0.4f, back = -0.65f, phase = "Heel up behind you, then down", further = "Bring your heel a little higher.", oneAtATime = true) { f, s -> -(f.of(s, Lm.L_ANKLE, Lm.R_ANKLE).y - f.of(s, Lm.L_KNEE, Lm.R_KNEE).y) / f.torso })
            ExerciseId.SEATED_MARCH -> Moves(i, target, Move(BODY, sides = 2, go = 0.12f, back = -0.05f, phase = "Left, right, left, right", further = "Lift your knees a little higher.", alternate = false, oneAtATime = true) { f, s -> (f.of(s, Lm.L_HIP, Lm.R_HIP).y - f.of(s, Lm.L_KNEE, Lm.R_KNEE).y) / f.torso })
            ExerciseId.SEATED_KNEES_OUT -> Moves(i, target, Move(BODY, go = 2.3f, back = 1.75f, phase = "Knees open, and together", further = "Open your knees a little wider.") { f, _ -> (f[Lm.L_KNEE].x - f[Lm.R_KNEE].x) / f.hipWidth })
            ExerciseId.SIDE_STEP -> Moves(i, target, Move(BODY, go = 1.35f, back = 0.95f, phase = "Step out, and back", further = "Take a slightly bigger step.") { f, _ -> abs(f[Lm.L_ANKLE].x - f[Lm.R_ANKLE].x) / f.shoulderWidth })
            ExerciseId.FRONT_ARM_RAISE -> Moves(i, target, Move(Lm.CORE + ARMS, go = -0.3f, back = -0.7f, phase = "Arms up to shoulder height", further = "Lift your arms to shoulder height.") { f, _ -> min(f.wristLift(0), f.wristLift(1)) })
            ExerciseId.SIDE_ARM_RAISE -> Moves(i, target, Move(Lm.CORE + ARMS, go = -0.3f, back = -0.7f, phase = "Arms out to the sides", further = "Lift your arms out to shoulder height.") { f, _ ->
                min(f.wristLift(0).takeIf { abs(f[Lm.L_WRIST].x - f[Lm.L_SHOULDER].x) / f.torso > 0.5f } ?: -1f, f.wristLift(1).takeIf { abs(f[Lm.R_WRIST].x - f[Lm.R_SHOULDER].x) / f.torso > 0.5f } ?: -1f)
            })
            ExerciseId.ARM_CURL -> Moves(i, target, Move(Lm.CORE + ARMS, go = 0.1f, back = -0.25f, phase = "Hands up to your shoulders", further = "Bring your hands all the way up.") { f, _ ->
                min((f[Lm.L_ELBOW].y - f[Lm.L_WRIST].y) / f.torso, (f[Lm.R_ELBOW].y - f[Lm.R_WRIST].y) / f.torso)
            })
            ExerciseId.OVERHEAD_REACH -> Moves(i, target, Move(Lm.CORE + ARMS, go = 0.35f, back = -0.3f, phase = "Reach up high", further = "Reach a little higher, if your shoulders are happy.") { f, _ -> min(f.wristLift(0), f.wristLift(1)) })
            ExerciseId.SHOULDER_SHRUG -> Moves(i, target, Move(HEAD, calibrate = true, go = 0.12f, back = 0.05f, phase = "Shoulders up, and drop", further = "Lift your shoulders a little higher.") { f, _ -> -(f.shoulderMid.y - f[Lm.NOSE].y) / f.shoulderWidth })
            ExerciseId.WEIGHT_SHIFT -> Moves(i, target, Move(BODY, sides = 2, calibrate = true, go = 0.22f, back = 0.08f, phase = "Over one foot, then the other", further = "Shift a little further over your foot.", settle = "Stand tall, feet apart") { f, s -> (if (s == 0) 1 else -1) * (f.hipMid.x - f.ankleMid.x) / f.shoulderWidth })
            ExerciseId.HEAD_TURN -> Moves(i, target, Move(HEAD, sides = 2, calibrate = true, go = 0.12f, back = 0.05f, phase = "Look over one shoulder, then the other", further = "Turn a little further, gently.", settle = "Sit tall and look at the TV") { f, s -> (if (s == 0) 1 else -1) * (f[Lm.NOSE].x - f.shoulderMid.x) / f.shoulderWidth })
            ExerciseId.SIDE_BEND -> Moves(i, target, Move(Lm.CORE, sides = 2, calibrate = true, go = 0.18f, back = 0.07f, phase = "Lean to one side, then the other", further = "Lean a little further, gently.") { f, s -> (if (s == 0) 1 else -1) * (f.shoulderMid.x - f.hipMid.x) / f.torso })
            ExerciseId.TRUNK_TWIST -> Moves(i, target, Move(Lm.CORE, calibrate = true, go = 0.2f, back = 0.08f, phase = "Turn your shoulders, and back", further = "Turn a little further, gently.", settle = "Sit tall, arms crossed") { f, _ -> -ln(f.shoulderWidth / f.hipWidth) })
            ExerciseId.HIP_HINGE -> Moves(i, target, Move(BODY, calibrate = true, go = 0.18f, back = 0.07f, phase = "Tip forward from the hips, then tall", further = "Tip a little further, back straight.") { f, _ -> -ln(f.torso / dist(f.hipMid, f.ankleMid).coerceAtLeast(1e-3f)) })
            ExerciseId.SEATED_JACKS -> Moves(i, target, Move(Lm.CORE + ARMS + Lm.NOSE, go = 0.1f, back = -0.4f, phase = "Arms up, and down", further = "Reach your arms up over your head.") { f, _ ->
                min(f[Lm.NOSE].y - f[Lm.L_WRIST].y, f[Lm.NOSE].y - f[Lm.R_WRIST].y) / f.torso
            })
            ExerciseId.FEET_TOGETHER -> PoseHold(i, target, Lm.LEGS + Lm.CORE, "Feet together, and hold") { f ->
                abs(f[Lm.L_ANKLE].x - f[Lm.R_ANKLE].x) / f.shoulderWidth < 0.45f && abs(f[Lm.L_ANKLE].y - f[Lm.R_ANKLE].y) / f.torso < 0.15f
            }
            ExerciseId.TANDEM_STANCE -> PoseHold(i, target, Lm.LEGS + Lm.CORE, "Heel to toe, and hold") { f ->
                abs(f[Lm.L_ANKLE].x - f[Lm.R_ANKLE].x) / f.shoulderWidth < 0.3f && abs(f[Lm.L_ANKLE].y - f[Lm.R_ANKLE].y) / f.torso < 0.15f
            }
            ExerciseId.KNEE_LIFT_HOLD -> PoseHold(i, target, Lm.LEGS + Lm.CORE, "Lift one knee, and hold") { f ->
                min(f[Lm.L_KNEE].y - f[Lm.L_HIP].y, f[Lm.R_KNEE].y - f[Lm.R_HIP].y) / f.torso < 0.5f
            }
            else -> error("no tracker for $id")
        }
    }

    // ------------------------------------------------------------------ coach keyframes

    private fun Pt.mv(dx: Float, dy: Float) = Pt(x + dx, y + dy, v)
    private fun Figure.upper(dx: Float, dy: Float, hips: Boolean = false) = copy(
        nose = nose.mv(dx, dy), lSh = lSh.mv(dx, dy), rSh = rSh.mv(dx, dy), lEl = lEl.mv(dx, dy), rEl = rEl.mv(dx, dy), lWr = lWr.mv(dx, dy), rWr = rWr.mv(dx, dy),
        lHip = if (hips) lHip.mv(dx, dy) else lHip, rHip = if (hips) rHip.mv(dx, dy) else rHip
    )
    private fun handsOnThighs(f: Figure) = f.copy(
        lWr = Pt(f.lKn.x + 0.005f, (f.lHip.y + f.lKn.y) / 2f), rWr = Pt(f.rKn.x - 0.005f, (f.rHip.y + f.rKn.y) / 2f),
        lEl = Pt(f.lSh.x + 0.045f, (f.lSh.y + f.lHip.y) / 2f), rEl = Pt(f.rSh.x - 0.045f, (f.rSh.y + f.rHip.y) / 2f)
    )

    private val S = Figure.STAND
    private val SE = Figure.SEATED
    private val SQUAT = S.upper(0f, 0.06f, hips = true).copy(lKn = Pt(0.56f, 0.69f), rKn = Pt(0.44f, 0.69f))
    private val EXT_L = SE.copy(lAn = Pt(0.565f, 0.72f))
    private val EXT_R = SE.copy(rAn = Pt(0.435f, 0.72f))
    private val CURL_L = S.copy(lAn = Pt(0.555f, 0.74f))
    private val CURL_R = S.copy(rAn = Pt(0.445f, 0.74f))
    private val SKNEE_L = SE.copy(lKn = Pt(0.57f, 0.6f), lAn = Pt(0.565f, 0.78f))
    private val SKNEE_R = SE.copy(rKn = Pt(0.43f, 0.6f), rAn = Pt(0.435f, 0.78f))
    private val KNEES_OUT = SE.copy(lKn = Pt(0.64f, 0.70f), rKn = Pt(0.36f, 0.70f), lAn = Pt(0.6f, 0.88f), rAn = Pt(0.4f, 0.88f))
    private val WIDE = S.copy(lKn = Pt(0.58f, 0.69f), rKn = Pt(0.42f, 0.69f), lAn = Pt(0.62f, 0.88f), rAn = Pt(0.38f, 0.88f))
    private val FRONT = SE.copy(lEl = Pt(0.58f, 0.44f), rEl = Pt(0.42f, 0.44f), lWr = Pt(0.585f, 0.45f), rWr = Pt(0.415f, 0.45f))
    private val SIDES = S.copy(lEl = Pt(0.68f, 0.27f), lWr = Pt(0.79f, 0.27f), rEl = Pt(0.32f, 0.27f), rWr = Pt(0.21f, 0.27f))
    private val CURLED = SE.copy(lEl = Pt(0.6f, 0.55f), rEl = Pt(0.4f, 0.55f), lWr = Pt(0.6f, 0.45f), rWr = Pt(0.4f, 0.45f))
    private val REACH = S.copy(lEl = Pt(0.6f, 0.15f), lWr = Pt(0.6f, 0.04f), rEl = Pt(0.4f, 0.15f), rWr = Pt(0.4f, 0.04f))
    private val SHRUG = SE.copy(lSh = SE.lSh.mv(0f, -0.03f), rSh = SE.rSh.mv(0f, -0.03f), lEl = SE.lEl.mv(0f, -0.03f), rEl = SE.rEl.mv(0f, -0.03f), lWr = SE.lWr.mv(0f, -0.03f), rWr = SE.rWr.mv(0f, -0.03f))
    private val TOGETHER = S.copy(lKn = Pt(0.52f, 0.69f), rKn = Pt(0.48f, 0.69f), lAn = Pt(0.52f, 0.88f), rAn = Pt(0.48f, 0.88f))
    private val TANDEM = S.copy(lKn = Pt(0.515f, 0.69f), rKn = Pt(0.485f, 0.69f), lAn = Pt(0.505f, 0.875f), rAn = Pt(0.495f, 0.885f))
    private val SHIFT_L = S.upper(0.05f, 0f, hips = true).copy(lKn = S.lKn.mv(0.025f, 0f), rKn = S.rKn.mv(0.025f, 0f))
    private val SHIFT_R = S.upper(-0.05f, 0f, hips = true).copy(lKn = S.lKn.mv(-0.025f, 0f), rKn = S.rKn.mv(-0.025f, 0f))
    private val HEAD_L = SE.copy(nose = SE.nose.mv(0.03f, 0f))
    private val HEAD_R = SE.copy(nose = SE.nose.mv(-0.03f, 0f))
    private val BEND_L = S.upper(0.07f, 0.01f)
    private val BEND_R = S.upper(-0.07f, 0.01f)
    private val CROSSED = Figure.armsCrossed(SE)
    private val TWIST = Figure.armsCrossed(SE.copy(lSh = Pt(0.545f, 0.43f), rSh = Pt(0.445f, 0.43f)))
    private val HINGE = S.copy(nose = Pt(0.5f, 0.25f), lSh = Pt(0.57f, 0.33f), rSh = Pt(0.43f, 0.33f), lEl = Pt(0.585f, 0.43f), rEl = Pt(0.415f, 0.43f), lWr = Pt(0.59f, 0.52f), rWr = Pt(0.41f, 0.52f))
    private val JACK = SE.copy(lEl = Pt(0.63f, 0.30f), lWr = Pt(0.66f, 0.17f), rEl = Pt(0.37f, 0.30f), rWr = Pt(0.34f, 0.17f), lKn = Pt(0.62f, 0.70f), rKn = Pt(0.38f, 0.70f), lAn = Pt(0.6f, 0.88f), rAn = Pt(0.4f, 0.88f))

    private fun m(vararg keys: Triple<Figure, Int, Int>) = Motion(keys.toList())
    private infix fun Figure.at(ms: Pair<Int, Int>) = Triple(this, ms.first, ms.second)

    /** Calibrated moves start and end on the rest pose, so the first second of the demo is a clean baseline. */
    fun motion(id: ExerciseId): Motion = when (id) {
        ExerciseId.MINI_SQUAT -> m(S at (600 to 1000), SQUAT at (900 to 400), S at (900 to 600))
        ExerciseId.HANDS_ASSISTED_STAND -> m(handsOnThighs(SE) at (700 to 700), handsOnThighs(Figure.RISING) at (650 to 0), S at (650 to 700), handsOnThighs(Figure.RISING) at (700 to 0))
        ExerciseId.ARMS_CROSSED_STAND -> m(Figure.armsCrossed(SE) at (700 to 600), Figure.armsCrossed(Figure.RISING) at (650 to 0), Figure.armsCrossed(S) at (650 to 600), Figure.armsCrossed(Figure.RISING) at (700 to 0))
        ExerciseId.SEATED_KNEE_EXTENSION -> m(SE at (600 to 800), EXT_L at (800 to 600), SE at (800 to 600), EXT_R at (800 to 600))
        ExerciseId.STANDING_KNEE_CURL -> m(S at (600 to 600), CURL_L at (700 to 500), S at (700 to 500), CURL_R at (700 to 500))
        ExerciseId.SEATED_MARCH -> m(SE at (400 to 150), SKNEE_L at (450 to 200), SE at (400 to 150), SKNEE_R at (450 to 200))
        ExerciseId.SEATED_KNEES_OUT -> m(SE at (600 to 700), KNEES_OUT at (800 to 600))
        ExerciseId.SIDE_STEP -> m(S at (500 to 600), WIDE at (600 to 500))
        ExerciseId.FRONT_ARM_RAISE -> m(SE at (700 to 500), FRONT at (900 to 600))
        ExerciseId.SIDE_ARM_RAISE -> m(S at (700 to 500), SIDES at (900 to 600))
        ExerciseId.ARM_CURL -> m(SE at (700 to 300), CURLED at (800 to 400))
        ExerciseId.OVERHEAD_REACH -> m(S at (800 to 400), REACH at (1000 to 700))
        ExerciseId.SHOULDER_SHRUG -> m(SE at (600 to 1200), SHRUG at (500 to 600), SE at (500 to 600))
        ExerciseId.FEET_TOGETHER -> m(S at (900 to 1500), TOGETHER at (700 to 11_000), S at (700 to 1500), TOGETHER at (700 to 11_000))
        ExerciseId.TANDEM_STANCE -> m(S at (900 to 1500), TANDEM at (700 to 11_000), S at (700 to 1500), TANDEM at (700 to 11_000))
        ExerciseId.WEIGHT_SHIFT -> m(S at (600 to 1200), SHIFT_L at (900 to 500), S at (900 to 400), SHIFT_R at (900 to 500), S at (900 to 600))
        ExerciseId.KNEE_LIFT_HOLD -> m(S at (900 to 1200), Figure.KNEE_UP_L at (700 to 5800), S at (700 to 1200), Figure.KNEE_UP_R at (700 to 5800))
        ExerciseId.HEAD_TURN -> m(SE at (600 to 1200), HEAD_L at (700 to 600), SE at (700 to 400), HEAD_R at (700 to 600), SE at (700 to 600))
        ExerciseId.SIDE_BEND -> m(S at (600 to 1200), BEND_L at (900 to 500), S at (900 to 400), BEND_R at (900 to 500), S at (900 to 600))
        ExerciseId.TRUNK_TWIST -> m(CROSSED at (600 to 1200), TWIST at (900 to 500), CROSSED at (900 to 600))
        ExerciseId.HIP_HINGE -> m(S at (600 to 1200), HINGE at (1000 to 500), S at (1000 to 600))
        ExerciseId.SEATED_JACKS -> m(SE at (300 to 100), JACK at (400 to 150))
        else -> error("no motion for $id")
    }
}

/** How one move is measured: a signal that grows as the move happens, and the thresholds for a rep. */
class Move(
    val needs: List<Int>,
    val sides: Int = 1,
    val calibrate: Boolean = false,
    val go: Float,
    val back: Float,
    val phase: String,
    val further: String? = null,
    val alternate: Boolean = sides == 2,
    /** Two sides that move one at a time (knees, legs): a rep needs the other side resting. */
    val oneAtATime: Boolean = false,
    val settle: String = "Sit tall and keep still for a moment",
    val measure: (PoseFrame, Int) -> Float
)

/**
 * Counts reps of any move described by a [Move]: a rep is the signal passing `go`, and the next one
 * needs it back below `back`. With `calibrate`, the signal is measured from the person's own resting
 * value. A move that turns back half way gets a gentle "a little further" cue.
 */
class Moves(info: ExerciseInfo, target: Int, private val spec: Move) : Tracker(info, target) {
    private val base = FloatArray(spec.sides)
    private val calib = List(spec.sides) { mutableListOf<Float>() }
    private var calibrated = !spec.calibrate
    private val inMove = BooleanArray(spec.sides)
    private val peak = FloatArray(spec.sides) { spec.back }
    private var reps = 0
    private var lastSide = -1
    private var sameSide = 0

    override fun update(f: PoseFrame) {
        if (state.done) return
        if (!f.visible(spec.needs)) {
            state = state.copy(phase = "Move so the phone can see you")
            return
        }
        if (!calibrated) {
            for (s in 0 until spec.sides) calib[s].add(spec.measure(f, s))
            if (calib[0].size >= 12) {
                for (s in 0 until spec.sides) base[s] = calib[s].sorted()[calib[s].size / 2]
                calibrated = true
            }
            state = state.copy(phase = spec.settle)
            return
        }
        var progress = 0f
        for (s in 0 until spec.sides) {
            val v = spec.measure(f, s) - base[s]
            progress = max(progress, ((v - spec.back) / (spec.go - spec.back)).coerceIn(0f, 1f))
            val otherResting = !spec.oneAtATime || spec.measure(f, 1 - s) - base[1 - s] < spec.back
            if (!inMove[s] && v > spec.go && otherResting) {
                inMove[s] = true
                reps++
                rep()
                if (spec.alternate) {
                    sameSide = if (s == lastSide) sameSide + 1 else 0
                    if (sameSide >= 2) cue(f, "switch", "Nice. Now the other side.")
                    lastSide = s
                }
            } else if (inMove[s] && v < spec.back) {
                inMove[s] = false
                peak[s] = spec.back
            } else if (!inMove[s]) {
                peak[s] = max(peak[s], v)
                if (v < spec.back) {
                    if (spec.further != null && peak[s] > spec.back + (spec.go - spec.back) * 0.55f) cue(f, "further", spec.further)
                    peak[s] = spec.back
                }
            }
        }
        state = state.copy(reps = reps, target = target, progress = progress, phase = spec.phase, done = reps >= target)
    }
}

/** Holds a position for a set time, as many times as the target asks; scores how still the hips stay. */
class PoseHold(info: ExerciseInfo, target: Int, private val needs: List<Int>, private val idle: String, private val inPose: (PoseFrame) -> Boolean) : Tracker(info, target) {
    private val holdTargetMs = info.holdSeconds * 1000L
    private var holdStart: Long? = null
    private var lastIn = 0L
    private var best = 0L
    private var holds = 0
    private var credited = false
    private val sway = RollingStd(3_000)
    private val steadyScores = mutableListOf<Int>()

    override fun update(f: PoseFrame) {
        if (state.done) return
        if (!f.visible(needs)) return
        val now = inPose(f)
        if (now) lastIn = f.tMs
        val start = holdStart
        if (now && start == null) {
            holdStart = f.tMs
            credited = false
            sway.clear()
        } else if (start != null && !now && f.tMs - lastIn > 500) {
            holdStart = null
        }
        val holdMs = holdStart?.let { f.tMs - it } ?: 0L
        var steadiness = state.steadiness
        if (holdStart != null) {
            sway.add(f.tMs, f.hipMid.x / f.shoulderWidth)
            if (holdMs > 1_500) {
                steadiness = (100 - sway.std() * 400).roundToInt().coerceIn(0, 100)
                steadyScores.add(steadiness)
            }
            if (holdMs >= holdTargetMs && !credited) {
                credited = true
                holds++
                rep()
                cue(f, "rest", if (holds < target) "Well done. Relax, then go again." else "Well done.")
            }
        }
        best = max(best, holdMs)
        state = state.copy(
            reps = holds, target = target, progress = (holdMs / holdTargetMs.toFloat()).coerceIn(0f, 1f),
            phase = if (holdStart != null) "Hold… ${holdMs / 1000} s" else idle,
            holdMs = holdMs, bestHoldMs = best, steadiness = steadiness, done = holds >= target
        )
    }

    override fun quality(): Int? = if (steadyScores.isEmpty()) null else steadyScores.average().roundToInt()
}
