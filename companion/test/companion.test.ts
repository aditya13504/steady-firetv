import assert from 'node:assert/strict';
import { spawn, type ChildProcess } from 'node:child_process';
import { after, before, test } from 'node:test';
import { WebSocket } from 'ws';
import { clean, validIntro, validSummary } from '../src/coach.js';

const PORT = 19000 + Math.floor(Math.random() * 500);
const BASE = `http://localhost:${PORT}`;
let proc: ChildProcess;

before(async () => {
  proc = spawn(process.execPath, ['--import', 'tsx', 'src/server.ts'], { env: { ...process.env, PORT: String(PORT), LLM_API_KEY: '', GROQ_API_KEY: '', PUBLIC_URL: 'https://steady.example' }, stdio: 'pipe' });
  for (let i = 0; i < 80; i++) {
    try {
      if ((await fetch(`${BASE}/health`)).ok) return;
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

test('pairing: TV gets a 6-digit code on the public URL; pose points and commands reach the TV; state reaches the phone', async () => {
  const tv = await open(`ws://localhost:${PORT}/ws?role=tv`);
  const hello = await tv.next();
  assert.equal(hello.t, 'paired-code');
  assert.match(hello.code, /^\d{6}$/);
  assert.equal(hello.phoneUrl, `https://steady.example/p/${hello.code}`);

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

test('a reconnecting TV keeps its code', async () => {
  const a = await open(`ws://localhost:${PORT}/ws?role=tv`);
  const { code } = await a.next();
  a.ws.close();
  await new Promise(r => setTimeout(r, 100));
  const b = await open(`ws://localhost:${PORT}/ws?role=tv&code=${code}`);
  assert.equal((await b.next()).code, code);
  b.ws.close();
});

test('wrong codes are refused, and guessing is rate limited', async () => {
  let last = '';
  for (let i = 0; i < 12; i++) {
    const phone = await open(`ws://localhost:${PORT}/ws?role=phone&code=000000`);
    last = (await phone.next()).message;
  }
  assert.match(last, /Too many tries/);
});

test('coach endpoints validate input and fall back cleanly without an LLM key', async () => {
  const bad = await fetch(`${BASE}/api/coach/summary`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ profile: { name: 'x' } }) });
  assert.equal(bad.status, 400);
  const ok = await fetch(`${BASE}/api/coach/summary`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ profile: { name: 'Maggie', age: 76, sex: 'female' }, today: { results: [] } })
  });
  assert.deepEqual(await ok.json(), { source: 'none' });
  const intro = await fetch(`${BASE}/api/coach/intro`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: 'Maggie', plan: [{ id: 'SIT_TO_STAND', target: 12 }], reasons: ['You reached 10 twice.'] })
  });
  assert.deepEqual(await intro.json(), { intro: null });
});

test('family page only serves well-formed secret topics', async () => {
  assert.equal((await fetch(`${BASE}/f/steady-abcdefghijkmnpqrstuvwxyz`)).status, 200);
  assert.equal((await fetch(`${BASE}/f/../../etc`)).status, 404);
  assert.equal((await fetch(`${BASE}/f/short`)).status, 404);
});

test('request validation and output cleaning', () => {
  assert.ok(validIntro({ name: 'Maggie', plan: [{ id: 'MARCH', target: 20 }], reasons: [] }));
  assert.ok(!validIntro({ name: 'Maggie', plan: [{ id: 'DROP TABLE', target: 1 }], reasons: [] }));
  assert.ok(!validSummary({ profile: { name: 'M', age: 'old', sex: 'female' }, today: {} }));
  assert.equal(clean('sit‑to‑stand — great'), 'sit-to-stand, great');
  assert.equal(clean('Hello Asha! 1. Welcome! Hold for 10 seconds.'), 'Hello Asha! Welcome! Hold for 10 seconds.');
});
