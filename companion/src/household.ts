/**
 * Household profiles and exercise history, persisted as one JSON file. Every value in the seed is
 * fictional demo data.
 */
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export type ExerciseId = 'MARCH' | 'SIT_TO_STAND' | 'HEEL_RAISE' | 'SIDE_LEG_RAISE' | 'ONE_LEG_STAND' | 'CHAIR_STAND_TEST';

export interface ExerciseResult {
  id: ExerciseId;
  reps: number;
  target: number;
  quality?: number | null;
  bestHoldMs?: number;
  cues?: Record<string, number>;
}

export interface Session {
  at: string;
  durationSec: number;
  results: ExerciseResult[];
  chairStand?: { stands: number; belowAverage: boolean | null };
  coach?: string;
  stoppedEarly?: boolean;
}

export interface Profile {
  id: string;
  name: string;
  age: number;
  sex: 'female' | 'male';
  goal: string;
  notes: string;
  history: Session[];
}

export interface FamilyMessage {
  id: string;
  at: string;
  kind: 'alert' | 'summary' | 'resolved';
  profileId: string;
  text: string;
}

export interface Household {
  name: string;
  family: { name: string; relation: string };
  profiles: Profile[];
  feed: FamilyMessage[];
}

const FILE = process.env.STEADY_DATA ?? resolve(dirname(fileURLToPath(import.meta.url)), '..', 'data', 'household.json');

function daysAgo(n: number, hour: number, minute = 0): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

function session(at: string, sts: number, stsTarget: number, holdS: number, steady: number, extra: Partial<Session> = {}): Session {
  return {
    at,
    durationSec: 14 * 60,
    results: [
      { id: 'MARCH', reps: 20, target: 20 },
      { id: 'SIT_TO_STAND', reps: sts, target: stsTarget, quality: 80 },
      { id: 'HEEL_RAISE', reps: 10, target: 10 },
      { id: 'SIDE_LEG_RAISE', reps: 10, target: 10 },
      { id: 'ONE_LEG_STAND', reps: holdS >= 10 ? 2 : 0, target: 2, bestHoldMs: holdS * 1000, quality: steady }
    ],
    ...extra
  };
}

export function seed(): Household {
  return {
    name: 'The Lee household',
    family: { name: 'Daniel', relation: 'son' },
    profiles: [
      {
        id: 'maggie',
        name: 'Maggie',
        age: 76,
        sex: 'female',
        goal: 'Walk to the farmers market on Saturdays without worrying about falling.',
        notes: 'Left knee arthritis. Prefers mornings.',
        history: [
          session(daysAgo(14, 9, 10), 7, 10, 4, 58, { chairStand: { stands: 8, belowAverage: true } }),
          session(daysAgo(11, 9, 5), 8, 10, 5, 61),
          session(daysAgo(9, 8, 55), 9, 10, 6, 66),
          session(daysAgo(6, 9, 20), 10, 10, 7, 70),
          session(daysAgo(4, 9, 0), 10, 10, 8, 72),
          session(daysAgo(2, 9, 15), 10, 10, 8, 75)
        ]
      },
      {
        id: 'walter',
        name: 'Walter',
        age: 81,
        sex: 'male',
        goal: 'Get up from the sofa without pushing off with my hands.',
        notes: 'Uses a cane outdoors. Exercises after dinner.',
        history: [
          session(daysAgo(8, 18, 30), 5, 8, 3, 50, { chairStand: { stands: 8, belowAverage: true } }),
          session(daysAgo(5, 18, 40), 6, 8, 3, 54),
          session(daysAgo(3, 18, 20), 7, 8, 4, 57)
        ]
      }
    ],
    feed: []
  };
}

export class Store {
  data: Household;

  constructor(private file = FILE) {
    this.data = existsSync(file) ? (JSON.parse(readFileSync(file, 'utf8')) as Household) : seed();
    if (!existsSync(file)) this.save();
  }

  profile(id: string): Profile {
    const p = this.data.profiles.find(x => x.id === id);
    if (!p) throw new Error(`No profile ${id}`);
    return p;
  }

  save() {
    mkdirSync(dirname(this.file), { recursive: true });
    writeFileSync(`${this.file}.tmp`, JSON.stringify(this.data, null, 2));
    renameSync(`${this.file}.tmp`, this.file);
  }

  reset() {
    this.data = seed();
    this.save();
  }
}

/** Mean local hour of someone's past sessions: the household's own pattern, learned from use. */
export function usualHour(p: Profile): number | null {
  if (!p.history.length) return null;
  const hours = p.history.map(s => {
    const d = new Date(s.at);
    return d.getHours() + d.getMinutes() / 60;
  });
  return hours.reduce((a, b) => a + b, 0) / hours.length;
}

/** Who is most likely in front of the TV right now. */
export function likelyProfile(h: Household, now = new Date()): { id: string; reason: string } {
  const hour = now.getHours() + now.getMinutes() / 60;
  let best = h.profiles[0];
  let bestGap = Infinity;
  for (const p of h.profiles) {
    const u = usualHour(p);
    if (u == null) continue;
    const gap = Math.min(Math.abs(u - hour), 24 - Math.abs(u - hour));
    if (gap < bestGap) {
      bestGap = gap;
      best = p;
    }
  }
  const u = usualHour(best);
  const clock = u == null ? '' : new Date(2000, 0, 1, Math.floor(u), Math.round((u % 1) * 60)).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  return { id: best.id, reason: u == null ? '' : `${best.name} usually exercises around ${clock}` };
}
