#!/usr/bin/env node
// Waykeep marketing screenshot framer.
//
// Wraps each capture in screenshots/ in a branded dark phone mockup and writes
// screenshots-marketing/<name>.png (1080x1920) via headless Chrome.
//
//   node tools/marketing-shots.mjs
//
// Requires: node, google-chrome (headless). Fonts are vendored in
// tools/marketing-fonts/, so this runs offline.
import { readFileSync, writeFileSync, mkdirSync, readdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, basename, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '..');
const SRC = join(ROOT, 'screenshots');
const OUT = join(ROOT, 'screenshots-marketing');
const BUILD = join(HERE, '.build');
const FONTS = join(HERE, 'marketing-fonts');
const CHROME = process.env.CHROME ?? 'google-chrome';

mkdirSync(OUT, { recursive: true });
mkdirSync(BUILD, { recursive: true });

const font = (w) => readFileSync(join(FONTS, `inter-${w}.woff2`)).toString('base64');

// The real app-icon mark: "Sealed Path W" from ic_logo_color_108dp.xml,
// cyan on the black adaptive-icon tile.
const logo = (id) => `
<svg viewBox="0 0 108 108" width="100%" height="100%" aria-hidden="true">
  <defs>
    <linearGradient id="tile${id}" x1="20" y1="8" x2="90" y2="100" gradientUnits="userSpaceOnUse">
      <stop stop-color="#141B26"/><stop offset="1" stop-color="#080B10"/>
    </linearGradient>
    <linearGradient id="w${id}" x1="24" y1="34" x2="84" y2="74" gradientUnits="userSpaceOnUse">
      <stop stop-color="#00E5FF"/><stop offset="1" stop-color="#00A6B8"/>
    </linearGradient>
  </defs>
  <rect x="3" y="3" width="102" height="102" rx="27" fill="url(#tile${id})"/>
  <rect x="3.5" y="3.5" width="101" height="101" rx="26.5" fill="none" stroke="rgba(0,229,255,.28)"/>
  <path d="M24,34 L38,74 L54,50 L70,74 L84,34" fill="none" stroke="url(#w${id})"
        stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="54" cy="74" r="6" fill="#00E5FF"/>
</svg>`;

// caption (supports <em> for the accent word), sub, chips
const COPY = {
  'home-screen': {
    cap: 'Your training,\n<em>private</em> by default.',
    sub: 'No account, no cloud, no tracking. Everything stays on your phone.',
    chips: ['Offline first', 'No account', 'Open source'],
  },
  'home-widget-weekly-stats': {
    cap: 'Weekly progress,\nright on your <em>home screen</em>.',
    sub: 'Distance, sessions and moving time, updated the moment a run ends.',
    chips: ['Widget', 'This week', 'This month'],
  },
  'personal-bests': {
    cap: 'Chase your\n<em>personal bests</em>.',
    sub: 'Longest distance, fastest average, top speed — tracked automatically.',
    chips: ['Records', 'Trends', 'History'],
  },
  'aggregated-stats-month': {
    cap: 'All your stats,\n<em>at a glance</em>.',
    sub: 'Aggregate by day, week, month or year across every activity type.',
    chips: ['Day', 'Month', 'Year'],
  },
  'suggested-routes': {
    cap: 'Find a loop,\njust press <em>generate</em>.',
    sub: 'Suggested running loops from your location, with distance and climb.',
    chips: ['3 / 5 / 10 km', 'Elevation', 'One tap'],
  },
  'recording-live-stats': {
    cap: 'Live stats while\nyou <em>move</em>.',
    sub: 'Real-time distance, pace, speed and time — even with the screen off.',
    chips: ['Live', 'GPS', 'Offline'],
  },
  'track-detail-stats': {
    cap: 'The numbers\nthat <em>matter</em>.',
    sub: 'Distance, moving time, pace and elevation for every single track.',
    chips: ['Pace', 'Time', 'Elevation'],
  },
  'track-detail-intervals': {
    cap: 'Splits that\nkeep you <em>honest</em>.',
    sub: 'Per-kilometre or per-mile intervals with pace and gain, automatically.',
    chips: ['1 km', 'Splits', 'Gain'],
  },
  'track-detail-chart-pace-distance': {
    cap: 'Every metric,\none clean <em>chart</em>.',
    sub: 'Pace, speed, elevation, heart rate and cadence — over time or distance.',
    chips: ['Pace', 'Toggles', 'Scrub'],
  },
  'track-detail-map-cyclosm': {
    cap: 'Your route,\n<em>beautifully</em> mapped.',
    sub: 'CyclOSM and standard OSM basemaps, drawn from your own GPS data.',
    chips: ['OSM', 'CyclOSM', 'Offline'],
  },
  'track-detail-map-esri-dark': {
    cap: 'Maps that match\nyour <em>dark mode</em>.',
    sub: 'Switch basemaps in a tap — the route, markers and stats stay yours.',
    chips: ['Dark map', 'Markers', 'Layers'],
  },
  'insert-marker-dialog': {
    cap: 'Mark the moments\nthat <em>matter</em>.',
    sub: 'Drop named waypoints mid-run with a type, an icon and a note.',
    chips: ['Waypoints', 'Icons', 'Notes'],
  },
  'track-detail-share-sheet': {
    cap: 'Export anywhere,\n<em>no lock-in</em>.',
    sub: 'Share an image, a GPX/KML file, or hand the run to your AI of choice.',
    chips: ['GPX / KML', 'Image', 'AI'],
  },
  'share-editor-story-clear': {
    cap: 'Share a beautiful\n<em>route card</em>.',
    sub: 'Pick a size, content and background, then export or share in seconds.',
    chips: ['Story', 'Stats card', 'Clear'],
  },
  'share-editor-feed-dark': {
    cap: 'Made to be\n<em>posted</em>.',
    sub: 'Feed-ready cards with your map, stats and the Waykeep mark.',
    chips: ['Feed', 'Dark', 'Stats'],
  },
};

// Captures that are not marketing-ready (e.g. a widget showing "0 activities").
const SKIP = new Set([
  'home-widget-weekly-stats',
]);

const html = (imgB64, c) => {
  const cap = c.cap.replace(/\n/g, '<br>');
  const chips = c.chips.map((x) => `<span class="chip">${x}</span>`).join('');
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><style>
@font-face{font-family:'Inter';font-weight:400;font-style:normal;src:url(data:font/woff2;base64,${font(400)}) format('woff2')}
@font-face{font-family:'Inter';font-weight:600;font-style:normal;src:url(data:font/woff2;base64,${font(600)}) format('woff2')}
@font-face{font-family:'Inter';font-weight:700;font-style:normal;src:url(data:font/woff2;base64,${font(700)}) format('woff2')}
@font-face{font-family:'Inter';font-weight:800;font-style:normal;src:url(data:font/woff2;base64,${font(800)}) format('woff2')}
:root{--cyan:#00E5FF;--muted:#93A1B0;}
*{margin:0;padding:0;box-sizing:border-box}
html,body{width:1080px;height:1920px}
body{
  font-family:'Inter',system-ui,sans-serif;color:#F4F7FA;
  background:
    radial-gradient(1100px 720px at 50% -8%, rgba(0,229,255,.20), transparent 62%),
    radial-gradient(1000px 820px at 88% 108%, rgba(59,157,255,.14), transparent 60%),
    radial-gradient(760px 620px at 6% 82%, rgba(0,166,184,.12), transparent 60%),
    linear-gradient(180deg,#0E1623 0%,#0A0E14 52%,#06090E 100%);
  position:relative;overflow:hidden;
  display:flex;flex-direction:column;justify-content:space-between;
  padding:78px 84px 86px;
}
.grid{position:absolute;inset:0;pointer-events:none;
  background-image:linear-gradient(rgba(255,255,255,.05) 1px,transparent 1px),
                   linear-gradient(90deg,rgba(255,255,255,.05) 1px,transparent 1px);
  background-size:64px 64px;background-position:center;
  -webkit-mask-image:radial-gradient(closest-side at 50% 42%,#000,transparent 82%);
          mask-image:radial-gradient(closest-side at 50% 42%,#000,transparent 82%);}
.vignette{position:absolute;inset:0;pointer-events:none;box-shadow:inset 0 0 340px 60px rgba(0,0,0,.75);}
header{position:relative;display:flex;align-items:center;gap:18px;z-index:2}
.mark{width:52px;height:52px;filter:drop-shadow(0 6px 18px rgba(0,229,255,.35))}
.wordmark{font-weight:800;font-size:36px;letter-spacing:-.5px}
.wordmark b{color:var(--cyan)}
.tagline{margin-left:auto;font-weight:600;font-size:21px;letter-spacing:3px;text-transform:uppercase;color:var(--muted)}
.copy{position:relative;z-index:2;text-align:center;margin-top:26px}
h1{font-weight:800;font-size:72px;line-height:1.05;letter-spacing:-2px}
h1 em{font-style:normal;color:var(--cyan);text-shadow:0 0 44px rgba(0,229,255,.45)}
.sub{margin:24px auto 0;max-width:860px;font-weight:500;font-size:29px;line-height:1.42;color:#B9C5D2}
.stage{position:relative;z-index:2;display:flex;justify-content:center;align-items:center}
.glow{position:absolute;width:820px;height:820px;border-radius:50%;
  background:radial-gradient(closest-side,rgba(0,229,255,.34),rgba(0,229,255,.06) 58%,transparent 72%);
  filter:blur(14px);}
.phone{position:relative;width:588px;padding:15px;border-radius:66px;
  background:linear-gradient(158deg,#2C3745 0%,#0F141B 46%,#1A212B 100%);
  box-shadow:0 0 0 1px rgba(255,255,255,.10) inset,0 3px 1px rgba(255,255,255,.14) inset,
    0 60px 130px -34px rgba(0,229,255,.30),0 34px 70px rgba(0,0,0,.70);}
.phone::after{content:"";position:absolute;inset:8px;border-radius:58px;border:1px solid rgba(255,255,255,.06);pointer-events:none}
.phone img{display:block;width:100%;border-radius:52px}
.chips{position:relative;z-index:2;display:flex;justify-content:center;gap:16px}
.chip{padding:15px 28px;border-radius:999px;font-weight:600;font-size:24px;color:#CBD6E1;
  border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.045);
  box-shadow:0 1px 0 rgba(255,255,255,.06) inset}
</style></head><body>
<div class="grid"></div><div class="vignette"></div>
<header>
  <div class="mark">${logo('h')}</div>
  <div class="wordmark">Way<b>keep</b></div>
  <div class="tagline">Sport tracker</div>
</header>
<section class="copy"><h1>${cap}</h1><p class="sub">${c.sub}</p></section>
<section class="stage"><div class="glow"></div>
  <div class="phone"><img src="data:image/png;base64,${imgB64}" alt=""></div>
</section>
<footer class="chips">${chips}</footer>
</body></html>`;
};

const files = readdirSync(SRC).filter((f) => f.endsWith('.png')).sort();
let count = 0;
for (const f of files) {
  const name = basename(f, '.png');
  if (SKIP.has(name)) {
    console.log('-', name, '(skipped)');
    continue;
  }
  const c = COPY[name] ?? { cap: name.replace(/-/g, ' '), sub: '', chips: ['Offline', 'Private', 'Open source'] };
  const b64 = readFileSync(join(SRC, f)).toString('base64');
  const h = join(BUILD, `${name}.html`);
  writeFileSync(h, html(b64, c));
  execFileSync(CHROME, [
    '--headless=new', '--no-sandbox', '--disable-gpu', '--hide-scrollbars',
    '--force-device-scale-factor=1', '--window-size=1080,1920',
    `--screenshot=${join(OUT, `${name}.png`)}`, `file://${h}`,
  ], { stdio: 'ignore' });
  console.log('✓', name);
  count++;
}
console.log(`\n${count} images -> ${OUT}`);
