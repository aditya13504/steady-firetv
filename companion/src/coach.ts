/**
 * The coach. Progression is decided by plain rules modelled on how the Otago programme progresses
 * (add reps when the target is met twice, ease off when it is missed, re-test strength every two
 * weeks). The language model only explains the plan and the session in warm, plain words, and the
 * app works without it.
 */
import type { ExerciseId, ExerciseResult, Profile, Session } from './household.js';

export interface PlanItem {
  id: ExerciseId;
  target: number;
}

export interface Plan {
  profileId: string;
  items: PlanItem[];
  reasons: string[];
  why: string;
  minutes: number;
}

const ORDER: ExerciseId[] = ['MARCH', 'SIT_TO_STAND', 'HEEL_RAISE', 'SIDE_LEG_RAISE', 'ONE_LEG_STAND'];
const DEFAULTS: Record<ExerciseId, number> = { MARCH: 20, SIT_TO_STAND: 10, HEEL_RAISE: 10, SIDE_LEG_RAISE: 10, ONE_LEG_STAND: 2, CHAIR_STAND_TEST: 30 };
const NAMES: Record<ExerciseId, string> = {
  MARCH: 'standing march',
  SIT_TO_STAND: 'sit to stand',
  HEEL_RAISE: 'heel raises',
  SIDE_LEG_RAISE: 'side leg raises',
  ONE_LEG_STAND: 'one-leg balance',
  CHAIR_STAND_TEST: '30-second chair stand'
};

function lastResults(p: Profile, id: ExerciseId, n: number): ExerciseResult[] {
  return p.history
    .slice(-n)
    .map(s => s.results.find(r => r.id === id))
    .filter((r): r is ExerciseResult => Boolean(r));
}

export function planFor(p: Profile, now = new Date()): Plan {
  const reasons: string[] = [];
  const items: PlanItem[] = ORDER.map(id => {
    const recent = lastResults(p, id, 2);
    const last = recent[recent.length - 1];
    let target = last?.target ?? DEFAULTS[id];
    if (id === 'ONE_LEG_STAND') return { id, target: 2 };
    if (recent.length === 2 && recent.every(r => r.reps >= r.target)) {
      const step = id === 'MARCH' ? 4 : 2;
      target += step;
      reasons.push(`You reached ${last.target} ${NAMES[id]} twice in a row, so today it's ${target}.`);
    } else if (last && last.reps < last.target * 0.7) {
      target = Math.max(4, target - 2);
      reasons.push(`Let's take ${NAMES[id]} down to ${target} today and build back up.`);
    }
    return { id, target };
  });

  const holds = lastResults(p, 'ONE_LEG_STAND', 6).map(r => (r.bestHoldMs ?? 0) / 1000);
  if (holds.length >= 2 && holds[holds.length - 1] > holds[0]) {
    reasons.push(`Your one-leg balance went from ${Math.round(holds[0])} to ${Math.round(holds[holds.length - 1])} seconds. Today we aim for 10 on each leg.`);
  }

  const lastTest = [...p.history].reverse().find(s => s.chairStand);
  const daysSince = lastTest ? (now.getTime() - new Date(lastTest.at).getTime()) / 86_400_000 : Infinity;
  if (daysSince >= 13) {
    items.push({ id: 'CHAIR_STAND_TEST', target: 30 });
    reasons.push(
      lastTest
        ? `It's been ${Math.round(daysSince)} days since your chair stand check (${lastTest.chairStand!.stands} stands), so we'll repeat it at the end.`
        : "We'll finish with the 30-second chair stand check, so we can see your progress."
    );
  }
  const minutes = 10 + items.length * 2;
  return { profileId: p.id, items, reasons, why: reasons.join(' '), minutes };
}

// ------------------------------------------------------------------ language model

const BASE_URL = () => (process.env.LLM_BASE_URL ?? 'https://api.groq.com/openai/v1').replace(/\/$/, '');
const MODEL = () => process.env.LLM_MODEL ?? 'llama-3.3-70b-versatile';
const KEY = () => process.env.LLM_API_KEY ?? process.env.GROQ_API_KEY ?? '';

export function llmConfigured() {
  return Boolean(KEY());
}

async function chatJson(system: string, user: string): Promise<Record<string, string> | null> {
  if (!KEY()) return null;
  try {
    const res = await fetch(`${BASE_URL()}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${KEY()}` },
      body: JSON.stringify({
        model: MODEL(),
        temperature: 0.5,
        max_tokens: 500,
        response_format: { type: 'json_object' },
        messages: [
          { role: 'system', content: system },
          { role: 'user', content: user }
        ]
      }),
      signal: AbortSignal.timeout(12_000)
    });
    if (!res.ok) {
      console.warn(`[coach] LLM ${res.status}: ${(await res.text()).slice(0, 200)}`);
      return null;
    }
    const data = (await res.json()) as { choices?: { message?: { content?: string } }[] };
    return JSON.parse(data.choices?.[0]?.message?.content ?? 'null');
  } catch (err) {
    console.warn('[coach] LLM failed:', err instanceof Error ? err.message : err);
    return null;
  }
}

const VOICE = `You are Steady, a warm, encouraging exercise coach on a TV for older adults doing a fall-prevention strength and balance programme.
Speak like a kind physiotherapist: short sentences, plain words, specific numbers, never patronising, never medical advice or diagnosis.
Celebrate real progress with the actual numbers. If something went badly, be gentle and practical.`;

export async function explainPlan(p: Profile, plan: Plan): Promise<string> {
  const out = await chatJson(
    `${VOICE}\nReturn JSON: {"intro": "two sentences the TV says when the session starts"}.`,
    JSON.stringify({ name: p.name, goal: p.goal, plan: plan.items.map(i => `${NAMES[i.id]}: ${i.target}`), reasons: plan.reasons })
  );
  return out?.intro ?? `Good to see you, ${p.name}. ${plan.reasons[0] ?? "Let's get moving, one exercise at a time."}`;
}

export interface CoachNote {
  spoken: string;
  summary: string;
  next: string;
  family: string;
  source: 'llm' | 'rules';
}

export async function coachSession(p: Profile, s: Session): Promise<CoachNote> {
  const previous = p.history.slice(-4, -1);
  const fallback = rulesNote(p, s);
  const out = await chatJson(
    `${VOICE}
Return JSON with exactly these keys:
"spoken": 1-2 sentences the TV says out loud right now,
"summary": 2-3 sentences shown on screen: what improved (with numbers), what to work on,
"next": one sentence about next session,
"family": one friendly sentence a family member receives as a text, written in third person (use the person's name).`,
    JSON.stringify({
      name: p.name,
      age: p.age,
      goal: p.goal,
      today: s,
      previousSessions: previous,
      exerciseNames: NAMES,
      notes: 'quality = % of clean reps (or steadiness 0-100 for balance). cues = how often each form cue fired. chairStand.belowAverage compares to CDC STEADI norms for age and sex.'
    })
  );
  if (!out?.spoken || !out?.summary) return fallback;
  return { spoken: out.spoken, summary: out.summary, next: out.next ?? fallback.next, family: out.family ?? fallback.family, source: 'llm' };
}

function rulesNote(p: Profile, s: Session): CoachNote {
  const sts = s.results.find(r => r.id === 'SIT_TO_STAND');
  const bal = s.results.find(r => r.id === 'ONE_LEG_STAND');
  const prevSts = lastResults(p, 'SIT_TO_STAND', 2)[0];
  const parts: string[] = [];
  if (sts) parts.push(`${sts.reps} sit to stands${prevSts && sts.reps > prevSts.reps ? `, up from ${prevSts.reps}` : ''}.`);
  if (bal?.bestHoldMs) parts.push(`Best balance hold: ${Math.round(bal.bestHoldMs / 1000)} seconds.`);
  if (s.chairStand) parts.push(`Chair stand check: ${s.chairStand.stands} stands${s.chairStand.belowAverage ? ', still below the usual range for your age, so keep going.' : ', in the healthy range for your age.'}`);
  return {
    spoken: `Well done, ${p.name}. That's another session in the bank.`,
    summary: parts.join(' ') || 'Session complete.',
    next: 'Next time we add a little more where you found it easy.',
    family: `${p.name} finished a ${Math.round(s.durationSec / 60)}-minute strength and balance session on Steady today.`,
    source: 'rules'
  };
}
