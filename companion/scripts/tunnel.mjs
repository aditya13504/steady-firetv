// Starts a free Cloudflare quick tunnel (HTTPS, WebSockets) to the companion, then runs the
// companion with PUBLIC_URL set, so the QR code on the TV opens a page a phone can use its camera on.
// Needs `cloudflared` on PATH, or CLOUDFLARED=/path/to/cloudflared.
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const port = process.env.PORT ?? '8787';
const local = resolve(here, '..', '..', '..', 'tools', process.platform === 'win32' ? 'cloudflared.exe' : 'cloudflared');
const bin = process.env.CLOUDFLARED ?? (existsSync(local) ? local : 'cloudflared');

const tunnel = spawn(bin, ['tunnel', '--no-autoupdate', '--url', `http://localhost:${port}`], { stdio: ['ignore', 'pipe', 'pipe'] });
tunnel.on('error', err => {
  console.error(`Could not start cloudflared (${err.message}). Install it from https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/ or set CLOUDFLARED.`);
  process.exit(1);
});

let started = false;
const onData = buf => {
  const m = String(buf).match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/);
  if (!m || started) return;
  started = true;
  console.log(`HTTPS tunnel: ${m[0]}`);
  const server = spawn(process.execPath, ['--import', 'tsx', resolve(here, '..', 'src', 'server.ts')], {
    stdio: 'inherit',
    env: { ...process.env, PUBLIC_URL: m[0], PORT: port }
  });
  const stop = () => {
    server.kill();
    tunnel.kill();
  };
  process.on('SIGINT', stop);
  process.on('SIGTERM', stop);
  server.on('exit', code => {
    tunnel.kill();
    process.exit(code ?? 0);
  });
};
tunnel.stdout.on('data', onData);
tunnel.stderr.on('data', onData);
setTimeout(() => {
  if (!started) {
    console.error('No tunnel URL after 30 s. Check your network, or run the companion without a tunnel: npm start');
    tunnel.kill();
    process.exit(1);
  }
}, 30_000);
