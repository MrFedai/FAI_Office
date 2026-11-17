// Loads a built web app in headless Chromium and lists what the first load actually fetches: per file raw and
// gzip -9 size (the file on disk), off-origin requests (offline-first check), time until the network is idle,
// and a screenshot. No npm dependencies. Throwaway (MASTER_SPEC section 15).
// Usage: node webmeasure.mjs <dir> <label> [settle_ms=4000]
import { spawn } from 'node:child_process';
import { readdirSync, readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const [dir, label, settleArg] = process.argv.slice(2);
const settle = Number(settleArg || 4000);
const pw = `${homedir()}/.cache/ms-playwright`;
const chrome = `${pw}/${readdirSync(pw).find(d => /^chromium-\d+$/.test(d))}/chrome-linux64/chrome`;
const port = 8600 + Math.floor(Math.random() * 300);
const dport = 9600 + Math.floor(Math.random() * 300);
const sleep = t => new Promise(r => setTimeout(r, t));

// Static server with correct MIME types (wasm must be application/wasm); no compression, sizes are computed.
const server = spawn('python3', ['-c', `
import http.server, socketserver, sys
http.server.SimpleHTTPRequestHandler.extensions_map.update({'.wasm':'application/wasm','.mjs':'text/javascript','.js':'text/javascript'})
class H(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cross-Origin-Opener-Policy','same-origin'); self.send_header('Cross-Origin-Embedder-Policy','require-corp')
        super().end_headers()
    def log_message(self,*a): pass
socketserver.TCPServer(('127.0.0.1', ${port}), H).serve_forever()`], { cwd: dir, stdio: 'ignore' });
const proc = spawn(chrome, ['--headless=new', `--remote-debugging-port=${dport}`, '--no-first-run',
  `--user-data-dir=/tmp/fai-poc-chrome-${dport}`, '--window-size=1280,800', 'about:blank'], { stdio: 'ignore' });

let target;
for (let i = 0; i < 50 && !target; i++) {
  await sleep(200);
  try { target = (await (await fetch(`http://127.0.0.1:${dport}/json`)).json()).find(t => t.type === 'page'); } catch {}
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let id = 0;
const pending = new Map();
const requests = new Map();
let lastNet = Date.now();
ws.addEventListener('message', e => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
  if (m.method === 'Network.requestWillBeSent') { requests.set(m.params.requestId, { url: m.params.request.url }); lastNet = Date.now(); }
  if (m.method === 'Network.loadingFinished' || m.method === 'Network.loadingFailed') {
    const r = requests.get(m.params.requestId);
    if (r) r.failed = m.method === 'Network.loadingFailed';
    lastNet = Date.now();
  }
  if (m.method === 'Runtime.exceptionThrown') console.log('page exception:', m.params.exceptionDetails.exception?.description?.split('\n')[0]);
});
const send = (method, params = {}) => new Promise(r => { pending.set(++id, r); ws.send(JSON.stringify({ id, method, params })); });
await send('Network.enable');
await send('Runtime.enable');
await send('Network.setCacheDisabled', { cacheDisabled: true });
const t0 = Date.now();
await send('Page.navigate', { url: `http://127.0.0.1:${port}/` });
while (Date.now() - lastNet < settle && Date.now() - t0 < 60000) await sleep(100);
const idleMs = lastNet - t0;
const shot = await send('Page.captureScreenshot', { format: 'png' });
mkdirSync('out', { recursive: true });
writeFileSync(`out/${label}.png`, Buffer.from(shot.data, 'base64'));

let raw = 0, gz = 0;
const rows = [];
for (const r of requests.values()) {
  const u = new URL(r.url);
  if (u.protocol === 'data:') continue;
  if (u.host !== `127.0.0.1:${port}`) { rows.push(`  OFF-ORIGIN ${r.url}${r.failed ? ' (failed)' : ''}`); continue; }
  let p = join(dir, decodeURIComponent(u.pathname));
  if (p.endsWith('/')) p = join(p, 'index.html');
  if (!existsSync(p)) { rows.push(`  missing    ${u.pathname}`); continue; }
  const b = readFileSync(p);
  const g = gzipSync(b, { level: 9 }).length;
  raw += b.length; gz += g;
  rows.push(`  ${(b.length / 1024).toFixed(0).padStart(7)} KB raw ${(g / 1024).toFixed(0).padStart(6)} KB gzip  ${u.pathname}`);
}
console.log(`${label}: first load fetched ${(raw / 1048576).toFixed(2)} MB raw, ${(gz / 1048576).toFixed(2)} MB gzip -9; network idle after ${idleMs} ms (local server)`);
console.log(rows.sort().join('\n'));
ws.close(); proc.kill(); server.kill();
