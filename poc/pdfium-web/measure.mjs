// Runs index.html in headless Chromium and prints the page's measurements plus browser-side numbers.
// Usage: node measure.mjs <url>   (run.sh starts the server and calls this). No npm dependencies.
// Throwaway code (MASTER_SPEC section 15).
import { spawn } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { homedir } from 'node:os';

const url = process.argv[2];
const pw = `${homedir()}/.cache/ms-playwright`;
const chromeDir = readdirSync(pw).find(d => /^chromium-\d+$/.test(d));
const chrome = process.env.CHROME || `${pw}/${chromeDir}/chrome-linux64/chrome`;
const port = 9300 + Math.floor(Math.random() * 500);

const proc = spawn(chrome, ['--headless=new', `--remote-debugging-port=${port}`, '--no-first-run',
  '--user-data-dir=/tmp/fai-poc-chrome-' + port, '--window-size=1080,1920', '--enable-precise-memory-info',
  'about:blank'], { stdio: 'ignore' });

const sleep = t => new Promise(r => setTimeout(r, t));
let target;
for (let i = 0; i < 50 && !target; i++) {
  await sleep(200);
  try { target = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find(t => t.type === 'page'); } catch {}
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let id = 0;
const pending = new Map();
ws.addEventListener('message', e => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
  if (m.method === 'Runtime.exceptionThrown') console.log('page exception:', m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text);
  if (m.method === 'Runtime.consoleAPICalled' && ['error', 'warning'].includes(m.params.type))
    console.log('page console:', m.params.args.map(a => a.value ?? a.description).join(' '));
});
const send = (method, params = {}) => new Promise(r => { pending.set(++id, r); ws.send(JSON.stringify({ id, method, params })); });

await send('Performance.enable');
await send('Runtime.enable');
await send('Network.enable');
await send('Network.setCacheDisabled', { cacheDisabled: true });
await send('Page.navigate', { url });

let res;
for (let i = 0; i < 600; i++) {
  await sleep(100);
  const r = await send('Runtime.evaluate', { expression: 'JSON.stringify(typeof pocResult!=="undefined"?pocResult:{})', returnByValue: true });
  res = JSON.parse(r.result.value || '{}');
  if (res.done || res.error) break;
}
const nav = await send('Runtime.evaluate', {
  expression: `JSON.stringify(performance.getEntriesByType('resource').map(e=>({n:e.name.split('/').pop(),kb:Math.round(e.transferSize/1024),ms:Math.round(e.duration)})))`,
  returnByValue: true,
});
const metrics = Object.fromEntries((await send('Performance.getMetrics')).metrics.map(m => [m.name, m.value]));
console.log(Object.entries(res).map(([k, v]) => `${k.padEnd(22)} ${v}`).join('\n'));
console.log('resources              ' + JSON.parse(nav.result.value).map(r => `${r.n} ${r.kb}KB ${r.ms}ms`).join(', '));
console.log(`js_heap_used_mb        ${(metrics.JSHeapUsedSize / 1048576).toFixed(1)}`);
ws.close();
proc.kill();
