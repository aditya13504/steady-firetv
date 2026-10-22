/**
 * Steady companion server.
 *  - Pairs a phone with a Fire TV by a 4-digit code and relays pose points + voice commands to it.
 *  - Serves the phone camera page and the family page.
 *  - Keeps household history and runs the coach (the LLM key stays here, never on the TV).
 */
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { networkInterfaces } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import QRCode from 'qrcode';
import { WebSocket, WebSocketServer } from 'ws';
import { coachSession, explainPlan, llmConfigured, planFor } from './coach.js';
import { likelyProfile, Store, type FamilyMessage, type Session } from './household.js';

const here = dirname(fileURLToPath(import.meta.url));
for (const file of [resolve(here, '..', '.env'), resolve(here, '..', '..', '.env')]) if (existsSync(file)) process.loadEnvFile(file);

const PORT = Number(process.env.PORT ?? 8787);
const store = new Store();
const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(express.static(resolve(here, '..', 'public'), { extensions: ['html'] }));

/** The address a phone on the same Wi-Fi can reach. PUBLIC_URL overrides it (e.g. an HTTPS tunnel). */
function publicBase(): string {
  const pub = process.env.PUBLIC_URL ?? process.env.RENDER_EXTERNAL_URL;
  if (pub) return pub.replace(/\/$/, '');
  for (const list of Object.values(networkInterfaces())) {
    for (const a of list ?? []) if (a.family === 'IPv4' && !a.internal && !a.address.startsWith('169.254')) return `http://${a.address}:${PORT}`;
  }
  return `http://localhost:${PORT}`;
}

// ------------------------------------------------------------------ pairing rooms

interface Room {
  code: string;
  tv?: WebSocket;
  phone?: WebSocket;
}
const rooms = new Map<string, Room>();

function newCode(): string {
  for (;;) {
    const code = String(Math.floor(1000 + Math.random() * 9000));
    if (!rooms.has(code)) return code;
  }
}

function send(ws: WebSocket | undefined, msg: unknown) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(typeof msg === 'string' ? msg : JSON.stringify(msg));
}

const server = createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', async (ws, req) => {
  const url = new URL(req.url ?? '/', 'http://x');
  const role = url.searchParams.get('role');
  let code = url.searchParams.get('code') ?? '';

  if (role === 'tv') {
    if (!code || (rooms.get(code)?.tv && rooms.get(code)!.tv!.readyState === WebSocket.OPEN)) code = newCode();
    const room = rooms.get(code) ?? { code };
    room.tv = ws;
    rooms.set(code, room);
    const phoneUrl = `${publicBase()}/p/${code}`;
    send(ws, {
      t: 'paired-code',
      code,
      phoneUrl,
      localUrl: `http://localhost:${PORT}/p/${code}`,
      qr: await QRCode.toString(phoneUrl, { type: 'svg', margin: 0 }).catch(() => null),
      phoneConnected: room.phone?.readyState === WebSocket.OPEN
    });
    console.log(`[pair] TV waiting with code ${code} (${phoneUrl})`);
    ws.on('message', data => send(room.phone, data.toString()));
    ws.on('close', () => {
      if (room.tv === ws) room.tv = undefined;
      send(room.phone, { t: 'tv', connected: false });
      if (!room.tv && !room.phone) rooms.delete(code);
    });
    return;
  }

  if (role === 'phone') {
    const room = rooms.get(code);
    if (!room?.tv) {
      send(ws, { t: 'error', message: `No TV is showing code ${code}. Check the number on your TV.` });
      ws.close();
      return;
    }
    send(room.phone, { t: 'error', message: 'Another phone took over the camera.' });
    room.phone?.close();
    room.phone = ws;
    send(ws, { t: 'hello', code });
    send(room.tv, { t: 'phone', connected: true, ua: req.headers['user-agent'] ?? '' });
    console.log(`[pair] phone joined ${code}`);
    ws.on('message', data => send(room.tv, data.toString()));
    ws.on('close', () => {
      if (room.phone === ws) {
        room.phone = undefined;
        send(room.tv, { t: 'phone', connected: false });
      }
    });
    return;
  }
  ws.close();
});

// ------------------------------------------------------------------ household & coach API

app.get('/api/household', (_req, res) => {
  const h = store.data;
  res.json({
    name: h.name,
    family: h.family,
    likely: likelyProfile(h, demoNow()),
    greeting: greetingFor(demoNow()),
    profiles: h.profiles.map(p => ({
      id: p.id,
      name: p.name,
      age: p.age,
      sex: p.sex,
      goal: p.goal,
      sessions: p.history.length,
      streakDays: streak(p.history),
      lastSession: p.history.at(-1)?.at ?? null,
      history: p.history.slice(-8).map(s => ({
        at: s.at,
        sitToStand: s.results.find(r => r.id === 'SIT_TO_STAND')?.reps ?? null,
        balanceSec: Math.round((s.results.find(r => r.id === 'ONE_LEG_STAND')?.bestHoldMs ?? 0) / 1000),
        chairStand: s.chairStand?.stands ?? null
      }))
    })),
    llm: llmConfigured()
  });
});

/** DEMO_HOUR pins the household clock (for demos and recordings); otherwise the real local time. */
function demoNow(): Date {
  const d = new Date();
  if (process.env.DEMO_HOUR) d.setHours(Number(process.env.DEMO_HOUR), 0, 0, 0);
  return d;
}

function greetingFor(d: Date): string {
  const h = d.getHours();
  return h >= 4 && h < 12 ? 'Good morning' : h < 17 && h >= 12 ? 'Good afternoon' : 'Good evening';
}

function streak(history: Session[]): number {
  const days = new Set(history.map(s => new Date(s.at).toDateString()));
  let n = 0;
  const d = new Date();
  // allow a rest day: count sessions in the last 7 days
  for (let i = 0; i < 7; i++) {
    if (days.has(d.toDateString())) n++;
    d.setDate(d.getDate() - 1);
  }
  return n;
}

app.get('/api/plan/:id', async (req, res) => {
  try {
    const p = store.profile(req.params.id);
    const plan = planFor(p);
    res.json({ ...plan, intro: await explainPlan(p, plan) });
  } catch (err) {
    res.status(404).json({ error: String(err) });
  }
});

app.post('/api/sessions', async (req, res) => {
  try {
    const { profileId, session } = req.body as { profileId: string; session: Session };
    const p = store.profile(profileId);
    const s: Session = { ...session, at: session.at ?? new Date().toISOString() };
    const note = await coachSession({ ...p, history: [...p.history, s] }, s, store.data.family);
    s.coach = note.summary;
    p.history.push(s);
    pushFeed({ kind: 'summary', profileId, text: note.family });
    store.save();
    res.json({ coach: note, plan: planFor(p) });
  } catch (err) {
    res.status(400).json({ error: String(err) });
  }
});

app.post('/api/alerts', (req, res) => {
  const { profileId, kind } = req.body as { profileId: string; kind: 'possible_fall' | 'unwell' | 'resolved' };
  const name = store.data.profiles.find(p => p.id === profileId)?.name ?? 'Someone';
  const time = new Date().toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
  const text =
    kind === 'resolved'
      ? `${name} answered "I'm OK" on the TV at ${time}.`
      : kind === 'unwell'
        ? `${name} stopped exercising at ${time} and said they don't feel well. Please check in.`
        : `Possible fall: ${name} went down during exercise at ${time} and hasn't answered on the TV. Please call now.`;
  pushFeed({ kind: kind === 'resolved' ? 'resolved' : 'alert', profileId, text });
  store.save();
  res.json({ ok: true, notified: store.data.family.name });
});

// ------------------------------------------------------------------ family page feed (server-sent events)

const familyClients = new Set<express.Response>();
function pushFeed(m: Omit<FamilyMessage, 'id' | 'at'>) {
  const msg: FamilyMessage = { ...m, id: crypto.randomUUID(), at: new Date().toISOString() };
  store.data.feed.push(msg);
  store.data.feed = store.data.feed.slice(-50);
  for (const c of familyClients) c.write(`data: ${JSON.stringify(msg)}\n\n`);
}

app.get('/api/family', (_req, res) => res.json({ family: store.data.family, household: store.data.name, feed: store.data.feed }));
app.get('/api/family/events', (req, res) => {
  res.set({ 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
  res.flushHeaders();
  familyClients.add(res);
  const ping = setInterval(() => res.write(': ping\n\n'), 25_000);
  req.on('close', () => {
    familyClients.delete(res);
    clearInterval(ping);
  });
});

app.post('/api/reset', (_req, res) => {
  store.reset();
  res.json({ ok: true });
});

app.get('/p/:code', (_req, res) => res.sendFile(resolve(here, '..', 'public', 'phone.html')));
app.get('/health', (_req, res) => res.json({ ok: true, rooms: rooms.size, llm: llmConfigured() }));

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Steady companion on http://localhost:${PORT}  (phones: ${publicBase()})  coach: ${llmConfigured() ? `LLM ${process.env.LLM_MODEL ?? 'openai/gpt-oss-120b'}` : 'rules only, set LLM_API_KEY for AI summaries'}`);
});
