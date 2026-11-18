// PoC WP7: opens test/index.html with the LibreOffice WASM engine in headless Chromium, waits for the in-engine
// test (fai_test.js) to finish, and prints its timings, memory, and the size of every file the page fetched
// (raw and gzip -9). No npm dependencies. Throwaway (MASTER_SPEC section 15).
// Usage: node measure.mjs <web dir> [timeout_s=600]
import { spawn } from 'node:child_process';
import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const [dir, timeoutArg] = process.argv.slice(2);
const timeout = Number(timeoutArg || 600) * 1000;
const pw = `${homedir()}/.cache/ms-playwright`;
const chrome = `${pw}/${readdirSync(pw).find(d => /^chromium-\d+$/.test(d))}/chrome-linux64/chrome`;
const port = 8900 + Math.floor(Math.random() * 300);
const dport = 9900 + Math.floor(Math.random() * 300);
const sleep = t => new Promise(r => setTimeout(r, t));

// Cross-origin isolation is required (SharedArrayBuffer for pthreads).
const server = spawn('python3', ['-c', `
import http.server, socketserver
http.server.SimpleHTTPRequestHandler.extensions_map.update({'.wasm':'application/wasm','.js':'text/javascript'})
class H(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cross-Origin-Opener-Policy','same-origin'); self.send_header('Cross-Origin-Embedder-Policy','require-corp')
        super().end_headers()
    def log_message(self,*a): pass
socketserver.ThreadingTCPServer(('127.0.0.1', ${port}), H).serve_forever()`], { cwd: dir, stdio: 'ignore' });
const proc = spawn(chrome, ['--headless=new', `--remote-debugging-port=${dport}`, '--no-first-run',
  `--user-data-dir=/tmp/fai-poc-chrome-${dport}`, '--enable-precise-memory-info', 'about:blank'], { stdio: 'ignore' });

let target;
for (let i = 0; i < 50 && !target; i++) {
  await sleep(200);
  try { target = (await (await fetch(`http://127.0.0.1:${dport}/json`)).json()).find(t => t.type === 'page'); } catch {}
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let id = 0;
const pending = new Map();
const urls = new Set();
ws.addEventListener('message', e => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
  if (m.method === 'Network.requestWillBeSent') urls.add(m.params.request.url);
  if (m.method === 'Runtime.exceptionThrown') console.log('page exception:', m.params.exceptionDetails.exception?.description?.split('\n')[0]);
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') console.log('console error:', m.params.args.map(a => a.value ?? a.description).join(' ').slice(0, 300));
});
const send = (method, params = {}) => new Promise(r => { pending.set(++id, r); ws.send(JSON.stringify({ id, method, params })); });
await send('Network.enable');
await send('Runtime.enable');
await send('Network.setCacheDisabled', { cacheDisabled: true });
const t0 = Date.now();
await send('Page.navigate', { url: `http://127.0.0.1:${port}/index.html` });

let res = {};
while (Date.now() - t0 < timeout) {
  await sleep(500);
  const r = await send('Runtime.evaluate', { expression: 'JSON.stringify(typeof faiResults!=="undefined"?faiResults:{})', returnByValue: true });
  res = JSON.parse(r?.result?.value || '{}');
  if (res.finished) break;
  process.stdout.write(`\r  ${Math.round((Date.now() - t0) / 1000)} s, ${Object.keys(res).length - 1} results `);
}
process.stdout.write('\n');
const metrics = Object.fromEntries((await send('Performance.getMetrics')).metrics?.map(m => [m.name, m.value]) || []);
console.log(Object.entries(res).map(([k, v]) => `${k.padEnd(28)} ${v}`).join('\n'));
if (!res.finished) console.log('TIMEOUT: engine test did not finish');

// Real memory: PSS of the Chromium process tree after the test (the shared WASM heap is split fairly between the
// threads' processes; untouched heap pages are not counted).
const kids = new Map();
for (const p of readdirSync('/proc').filter(d => /^\d+$/.test(d))) {
  try { const ppid = readFileSync(`/proc/${p}/stat`, 'utf8').split(') ')[1].split(' ')[1]; kids.set(p, ppid); } catch {}
}
const tree = [String(proc.pid)];
for (let i = 0; i < tree.length; i++) for (const [p, pp] of kids) if (pp === tree[i]) tree.push(p);
const pss = tree.map(p => { try { return Number(/^Pss:\s+(\d+)/m.exec(readFileSync(`/proc/${p}/smaps_rollup`, 'utf8'))[1]) / 1024; } catch { return 0; } });
console.log(`chromium PSS: ${pss.reduce((a, b) => a + b, 0).toFixed(0)} MB total, largest process ${Math.max(...pss).toFixed(0)} MB (${tree.length} processes)`);

let raw = 0, gz = 0;
const rows = [];
for (const u of urls) {
  const p = new URL(u);
  if (p.protocol === 'data:' || p.protocol === 'blob:') continue;
  if (p.host !== `127.0.0.1:${port}`) { rows.push(`  OFF-ORIGIN ${u}`); continue; }
  const f = join(dir, decodeURIComponent(p.pathname));
  if (!existsSync(f) || /\/docs\//.test(f)) continue;
  const b = readFileSync(f);
  const g = gzipSync(b, { level: 6 }).length;
  raw += b.length; gz += g;
  rows.push(`  ${(b.length / 1048576).toFixed(1).padStart(6)} MB raw ${(g / 1048576).toFixed(1).padStart(6)} MB gzip  ${p.pathname}`);
}
console.log(`engine download (page fetches, docs excluded): ${(raw / 1048576).toFixed(1)} MB raw, ${(gz / 1048576).toFixed(1)} MB gzip -6`);
console.log(rows.sort().join('\n'));
writeFileSync('out/result.json', JSON.stringify({ res, raw, gz }, null, 1));
ws.close(); proc.kill(); server.kill();
