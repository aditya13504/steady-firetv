/**
 * Steady companion server. Stateless by design, so it runs anywhere (Render, a home server, a laptop):
 *  - pairs a phone with a Fire TV by a 6-digit code and relays pose points and voice commands to it;
 *  - serves the phone camera page and the family page;
 *  - turns plans and finished sessions into the coach's words (the LLM key stays here, never on the TV).
 * Household history lives on the TV; family alerts go from the TV straight to the family's push topic.
 */
import { existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { networkInterfaces } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import { WebSocket, WebSocketServer } from 'ws';
import { intro, llmConfigured, MODEL, summary, validIntro, validSummary } from './coach.js';

const here = dirname(fileURLToPath(import.meta.url));
for (const file of [resolve(here, '..', '.env'), resolve(here, '..', '..', '.env')]) if (existsSync(file)) process.loadEnvFile(file);

const PORT = Number(process.env.PORT ?? 8787);
const started = Date.now();
const app = express();
app.set('trust proxy', true);
app.disable('x-powered-by');
app.use((_req, res, next) => {
  res.set({ 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer', 'Permissions-Policy': 'camera=(self), microphone=(self)' });
  next();
});
app.use(express.json({ limit: '64kb' }));
app.use(express.static(resolve(here, '..', 'public'), { extensions: ['html'], maxAge: '10m' }));

/** The address phones reach: PUBLIC_URL, Render's URL, or this machine on the local network. */
function publicBase(): string {
  const pub = process.env.PUBLIC_URL ?? process.env.RENDER_EXTERNAL_URL;
  if (pub) return pub.replace(/\/$/, '');
  for (const list of Object.values(networkInterfaces())) {
    for (const a of list ?? []) if (a.family === 'IPv4' && !a.internal && !a.address.startsWith('169.254')) return `http://${a.address}:${PORT}`;
  }
  return `http://localhost:${PORT}`;
}

// ------------------------------------------------------------------ rate limiting

/** Fixed-window counter per key: enough to stop code guessing and runaway clients. */
function limiter(max: number, windowMs: number) {
  const hits = new Map<string, { n: number; reset: number }>();
  setInterval(() => {
    const now = Date.now();
    for (const [k, v] of hits) if (v.reset < now) hits.delete(k);
  }, windowMs).unref();
  return (key: string) => {
    const now = Date.now();
    const h = hits.get(key);
    if (!h || h.reset < now) {
      hits.set(key, { n: 1, reset: now + windowMs });
      return true;
    }
    h.n++;
    return h.n <= max;
  };
}
const joinFailures = limiter(10, 60_000);
const coachCalls = limiter(40, 60_000);

// ------------------------------------------------------------------ pairing rooms

interface Room {
  code: string;
  tv?: WebSocket;
  phone?: WebSocket;
  lastSeen: number;
}
const rooms = new Map<string, Room>();

function newCode(): string {
  for (;;) {
    const code = String(Math.floor(100_000 + Math.random() * 900_000));
    if (!rooms.has(code)) return code;
  }
}

function send(ws: WebSocket | undefined, msg: unknown) {
  if (ws && ws.readyState === WebSocket.OPEN) ws.send(typeof msg === 'string' ? msg : JSON.stringify(msg));
}

setInterval(() => {
  const cutoff = Date.now() - 6 * 3_600_000;
  for (const [code, room] of rooms) if (!room.tv && !room.phone && room.lastSeen < cutoff) rooms.delete(code);
}, 600_000).unref();

const server = createServer(app);
const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 64 * 1024 });

wss.on('connection', (ws, req) => {
  const url = new URL(req.url ?? '/', 'http://x');
  const role = url.searchParams.get('role');
  let code = url.searchParams.get('code') ?? '';
  const ip = req.socket.remoteAddress ?? '?';

  if (role === 'tv') {
    // A TV may keep its code across reconnects, unless another live TV holds it.
    if (!/^\d{6}$/.test(code) || (rooms.get(code)?.tv && rooms.get(code)!.tv!.readyState === WebSocket.OPEN)) code = newCode();
    const room = rooms.get(code) ?? { code, lastSeen: Date.now() };
    room.tv = ws;
    room.lastSeen = Date.now();
    rooms.set(code, room);
    const phoneUrl = `${publicBase()}/p/${code}`;
    send(ws, { t: 'paired-code', code, phoneUrl, localUrl: `http://localhost:${PORT}/p/${code}`, phoneConnected: room.phone?.readyState === WebSocket.OPEN });
    ws.on('message', data => {
      room.lastSeen = Date.now();
      send(room.phone, data.toString());
    });
    ws.on('close', () => {
      if (room.tv === ws) room.tv = undefined;
      send(room.phone, { t: 'tv', connected: false });
    });
    return;
  }

  if (role === 'phone') {
    const room = rooms.get(code);
    if (!room?.tv || room.tv.readyState !== WebSocket.OPEN) {
      const allowed = joinFailures(ip);
      send(ws, { t: 'error', message: allowed ? `No TV is showing code ${code}. Check the number on your TV.` : 'Too many tries. Wait a minute, then check the code on your TV.' });
      ws.close();
      return;
    }
    send(room.phone, { t: 'error', message: 'Another phone took over the camera.' });
    room.phone?.close();
    room.phone = ws;
    room.lastSeen = Date.now();
    send(ws, { t: 'hello', code });
    send(room.tv, { t: 'phone', connected: true });
    ws.on('message', data => {
      const msg = data.toString();
      // Camera pictures are only for watching live: drop them rather than queue them if the TV falls behind.
      if (msg.startsWith('{"t":"img"') && (room.tv?.bufferedAmount ?? 0) > 256 * 1024) return;
      send(room.tv, msg);
    });
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

// ------------------------------------------------------------------ coach API (stateless)

app.post('/api/coach/intro', async (req, res) => {
  if (!coachCalls(req.ip ?? '?')) return void res.status(429).json({ error: 'Too many requests' });
  if (!validIntro(req.body)) return void res.status(400).json({ error: 'Invalid request' });
  res.json({ intro: await intro(req.body) });
});

app.post('/api/coach/summary', async (req, res) => {
  if (!coachCalls(req.ip ?? '?')) return void res.status(429).json({ error: 'Too many requests' });
  if (!validSummary(req.body)) return void res.status(400).json({ error: 'Invalid request' });
  res.json((await summary(req.body)) ?? { source: 'none' });
});

// ------------------------------------------------------------------ pages and health

app.get('/p/:code', (_req, res) => res.sendFile(resolve(here, '..', 'public', 'phone.html')));
app.get('/f/:topic', (req, res) => {
  if (!/^steady-[a-z0-9]{16,40}$/.test(req.params.topic)) return void res.status(404).send('Not found');
  res.sendFile(resolve(here, '..', 'public', 'family.html'));
});
app.get('/health', (_req, res) => res.json({ ok: true, rooms: rooms.size, llm: llmConfigured(), uptimeSec: Math.round((Date.now() - started) / 1000) }));

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Steady companion on http://localhost:${PORT}  (phones: ${publicBase()})  coach: ${llmConfigured() ? `LLM ${MODEL()}` : 'no LLM key, the TV uses its own rules'}`);
});
