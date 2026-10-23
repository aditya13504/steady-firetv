/**
 * The coach's voice. The TV decides the plan with simple progression rules and keeps all history;
 * this module only turns a plan or a finished session into warm, plain words with a language model.
 * It is stateless: nothing a request contains is stored.
 */

export type ExerciseId = 'MARCH' | 'SIT_TO_STAND' | 'HEEL_RAISE' | 'SIDE_LEG_RAISE' | 'ONE_LEG_STAND' | 'CHAIR_STAND_TEST';

export const NAMES: Record<ExerciseId, string> = {
  MARCH: 'standing march',
  SIT_TO_STAND: 'sit to stand',
  HEEL_RAISE: 'heel raises',
  SIDE_LEG_RAISE: 'side leg raises',
  ONE_LEG_STAND: 'one-leg balance',
  CHAIR_STAND_TEST: '30-second chair stand'
};

export interface IntroRequest {
  name: string;
  goal?: string;
  plan: { id: ExerciseId; target: number }[];
  reasons: string[];
}

export interface SummaryRequest {
  profile: { name: string; age: number; sex: 'female' | 'male'; goal?: string };
  today: Record<string, unknown> & { chairStand?: number };
  previousSessions?: unknown[];
  previousChairStand?: number;
  chairStandNorm?: number;
  family?: { name: string; relation: string };
}

export interface CoachNote {
  spoken: string;
  summary: string;
  next: string;
  family: string;
  source: 'llm' | 'none';
}

const BASE_URL = () => (process.env.LLM_BASE_URL ?? 'https://api.groq.com/openai/v1').replace(/\/$/, '');
export const MODEL = () => process.env.LLM_MODEL ?? 'openai/gpt-oss-120b';
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
        max_tokens: 1500,
        ...(MODEL().startsWith('openai/gpt-oss') ? { reasoning_effort: 'low' } : {}),
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

/** Plain punctuation only: TV fonts and speech engines stumble on non-breaking hyphens and the like. */
export function clean(s: string | undefined): string | undefined {
  if (!s) return undefined;
  return s
    .replace(/[‐‑‒]/g, '-')
    .replace(/\s*[–—]\s*/g, ', ')
    .replace(/ /g, ' ')
    // "Hello Asha! 1. Welcome!": models sometimes copy list numbering from the reasons they were given
    .replace(/(^|[.!?]\s+)\d{1,2}\.\s+(?=[A-Z])/g, '$1')
    .trim();
}

const VOICE = `You are Steady, a warm, encouraging exercise coach on a TV for older adults doing a fall-prevention strength and balance programme.
Speak like a kind physiotherapist: short sentences, plain words, specific numbers, never patronising, never medical advice or diagnosis.
Celebrate real progress with the actual numbers. If something went badly, be gentle and practical.
Numbers mean reps, steps, seconds or chair stands. Describe form in words ("good form", "nice and steady"); never quote percentages, scores or the word "quality", and never say "perfect" or "flawless".`;

export async function intro(req: IntroRequest): Promise<string | null> {
  const out = await chatJson(
    `${VOICE}
Return JSON: {"intro": "exactly two short sentences the TV says as the session starts. First: a warm hello by name. Second: the first item in reasons (they are ordered most important first) in your own words, keeping its reps or seconds. Do not number anything."}`,
    JSON.stringify({ name: req.name, goal: req.goal, plan: req.plan.map(i => `${NAMES[i.id] ?? i.id}: ${i.target}`), reasons: req.reasons })
  );
  return clean(out?.intro) ?? null;
}

export async function summary(req: SummaryRequest): Promise<CoachNote | null> {
  const p = req.profile;
  const recipient = req.family ? `${req.family.name} (${p.name}'s ${req.family.relation})` : 'a family member';
  const hasTest = typeof req.today.chairStand === 'number';
  const out = await chatJson(
    `${VOICE}
Return JSON with exactly these keys:
"spoken": 1-2 short sentences the TV says out loud right now,
"summary": 2 sentences, 35 words at most, shown on screen: the biggest improvement with its numbers, then one thing to work on,
"next": one sentence about the next session (do not start with "Next time"),
"family": one friendly text message sent TO ${recipient}, about ${p.name} in the third person. Do not address ${p.name}.
Only talk about chair stands if today.chairStand exists. If it does, it is the headline: give the number of stands, the previous result (previousChairStand), and whether it is now in the usual range for their age (usual means at least chairStandNorm stands, per the CDC STEADI guide). Mention it in spoken, summary and family.`,
    JSON.stringify({
      name: p.name,
      age: p.age,
      sex: p.sex,
      goal: p.goal,
      today: req.today,
      previousSessions: req.previousSessions ?? [],
      ...(hasTest ? { previousChairStand: req.previousChairStand ?? null, chairStandNorm: req.chairStandNorm ?? null } : {}),
      exerciseNames: NAMES,
      notes: 'quality = % of clean reps (or steadiness 0-100 for balance); use it only to judge form, never quote it. cues = how often each form cue fired; if any fired, mention that cue gently as the thing to work on. skipped = exercise not finished.'
    })
  );
  if (!out?.spoken || !out?.summary) return null;
  return {
    spoken: clean(out.spoken)!,
    summary: clean(out.summary)!,
    next: clean(out.next) ?? '',
    family: clean(out.family) ?? '',
    source: 'llm'
  };
}

// ------------------------------------------------------------------ request validation

const IDS = new Set(Object.keys(NAMES));
const str = (v: unknown, max: number) => typeof v === 'string' && v.length <= max;

export function validIntro(b: unknown): b is IntroRequest {
  const r = b as IntroRequest;
  return (
    !!r && str(r.name, 60) && (r.goal === undefined || str(r.goal, 300)) && Array.isArray(r.plan) && r.plan.length <= 10 &&
    r.plan.every(i => IDS.has(i?.id) && Number.isFinite(i?.target)) && Array.isArray(r.reasons) && r.reasons.length <= 10 && r.reasons.every(x => str(x, 400))
  );
}

export function validSummary(b: unknown): b is SummaryRequest {
  const r = b as SummaryRequest;
  return (
    !!r && !!r.profile && str(r.profile.name, 60) && Number.isFinite(r.profile.age) && (r.profile.sex === 'female' || r.profile.sex === 'male') &&
    typeof r.today === 'object' && r.today !== null && (r.previousSessions === undefined || (Array.isArray(r.previousSessions) && r.previousSessions.length <= 5))
  );
}
