import assert from 'node:assert/strict';
import { spawn, type ChildProcess } from 'node:child_process';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, test } from 'node:test';
import { WebSocket } from 'ws';
import { planFor } from '../src/coach.js';
import { likelyProfile, seed } from '../src/household.js';

test('progression: targets rise after two good sessions, chair stand re-test every two weeks', () => {
  const h = seed();
  const maggie = planFor(h.profiles[0]);
  assert.equal(maggie.items.find(i => i.id === 'SIT_TO_STAND')!.target, 12);
  assert.ok(maggie.items.some(i => i.id === 'CHAIR_STAND_TEST'));
  assert.match(maggie.why, /4 to 8 seconds/);
  const walter = planFor(h.profiles[1]);
  assert.equal(walter.items.find(i => i.id === 'SIT_TO_STAND')!.target, 8);
  assert.ok(!walter.items.some(i => i.id === 'CHAIR_STAND_TEST'));
});

test('household pattern: morning suggests Maggie, evening suggests Walter', () => {
  const h = seed();
  const morning = new Date();
  morning.setHours(9, 30);
  const evening = new Date();
  evening.setHours(18, 45);
  assert.equal(likelyProfile(h, morning).id, 'maggie');
  assert.equal(likelyProfile(h, evening).id, 'walter');
});

const PORT = 19000 + Math.floor(Math.random() * 500);
let proc: ChildProcess;
before(async () => {
  const dir = mkdtempSync(join(tmpdir(), 'steady-'));
  proc = spawn(process.execPath, ['--import', 'tsx', 'src/server.ts'], { env: { ...process.env, PORT: String(PORT), STEADY_DATA: join(dir, 'h.json'), LLM_API_KEY: '' }, stdio: 'pipe' });
  for (let i = 0; i < 80; i++) {
    try {
      if ((await fetch(`http://localhost:${PORT}/health`)).ok) return;
    } catch {}
    await new Promise(r => setTimeout(r, 150));
  }
  throw new Error('server did not start');
});
after(() => proc.kill());

function open(url: string): Promise<{ ws: WebSocket; next: () => Promise<any> }> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    const queue: any[] = [];
    const waiters: ((m: any) => void)[] = [];
    ws.on('message', d => {
      const m = JSON.parse(d.toString());
      const w = waiters.shift();
      if (w) w(m);
      else queue.push(m);
    });
    ws.on('open', () => resolve({ ws, next: () => (queue.length ? Promise.resolve(queue.shift()) : new Promise(r => waiters.push(r))) }));
    ws.on('error', reject);
  });
}

test('pairing: TV gets a code, phone joins with it, pose points and commands reach the TV', async () => {
  const tv = await open(`ws://localhost:${PORT}/ws?role=tv`);
  const hello = await tv.next();
  assert.equal(hello.t, 'paired-code');
  assert.match(hello.code, /^\d{4}$/);
  assert.match(hello.phoneUrl, new RegExp(`/p/${hello.code}$`));
  assert.match(hello.qr, /<svg/);

  const phone = await open(`ws://localhost:${PORT}/ws?role=phone&code=${hello.code}`);
  assert.equal((await phone.next()).t, 'hello');
  assert.equal((await tv.next()).t, 'phone');

  phone.ws.send(JSON.stringify({ t: 'pose', lm: [[0.5, 0.5, 1]] }));
  assert.deepEqual((await tv.next()).lm, [[0.5, 0.5, 1]]);
  phone.ws.send(JSON.stringify({ t: 'cmd', cmd: 'pause' }));
  assert.equal((await tv.next()).cmd, 'pause');
  tv.ws.send(JSON.stringify({ t: 'state', exercise: 'Sit to stand', reps: 3 }));
  assert.equal((await phone.next()).reps, 3);
  tv.ws.close();
  phone.ws.close();
});

test('a wrong code is refused with a plain message', async () => {
  const phone = await open(`ws://localhost:${PORT}/ws?role=phone&code=0000`);
  const m = await phone.next();
  assert.equal(m.t, 'error');
  assert.match(m.message, /No TV is showing code 0000/);
});

test('sessions: saved to history, coach note returned without an LLM key, family feed updated', async () => {
  const res = await fetch(`http://localhost:${PORT}/api/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      profileId: 'maggie',
      session: { durationSec: 900, results: [{ id: 'SIT_TO_STAND', reps: 12, target: 12, quality: 90 }, { id: 'ONE_LEG_STAND', reps: 2, target: 2, bestHoldMs: 11000 }], chairStand: { stands: 11, belowAverage: false } }
    })
  });
  const body = await res.json();
  assert.equal(body.coach.source, 'rules');
  assert.match(body.coach.summary, /12 sit to stands/);
  const fam = await (await fetch(`http://localhost:${PORT}/api/family`)).json();
  assert.match(fam.feed.at(-1).text, /Maggie finished/);
  const alert = await (await fetch(`http://localhost:${PORT}/api/alerts`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ profileId: 'maggie', kind: 'possible_fall' }) })).json();
  assert.equal(alert.notified, 'Daniel');
});
