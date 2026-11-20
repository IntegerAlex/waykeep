# Waykeep — Evidence-Based Summary: What's Actually Needed, What Isn't

**Date:** 7 Nov 2026 · **Basis:** Reddit review mining, Codeberg/GitHub issues, 8 academic studies, competitor teardowns (Strava, Garmin, Nike, Komoot), codebase audit of Waykeep (440 files, +9,365 lines)
**Method note:** Every claim below = (source, N / issue #, date). No vibes.

---

## 1. The Evidence in 60 Seconds

| Finding | Evidence | Strength |
|---|---|---|
| **GPS trust is the #1 churn driver** | 50% of negative Strava reviews mention GPS drift/teleporting; 182 corrupted activities in 2,300 reviews (Dec 2025 scrape) | 🔴 Strong — large N, replicated |
| **Paywall + junk segments = revolt** | "Canceled after 5 years" (Oct 2025), "enshitification" (284-pt Dec 2025 Year-in-Sport post), segments: 10 overlapping on one trail, fake CRs | 🔴 Strong — repeated threads |
| **Social comparison harms motivation** | CHI 2025, N=101: ↑comparison → ↓intrinsic motivation, ↑stress, ↑external validation; good UX only *buffers*, doesn't fix | 🔴 Strong — peer-reviewed |
| **Social features DO help — if it's relatedness** | Run Together 2025, N=120: social ↑motivation; Aalto Strava 2025, N=16: sharing + feature depth per user type drive retention | 🟡 Medium — small N, consistent direction |
| **Ease of use > social/gamification for choice** | DCE health apps, N=162: ease-of-use β=0.67, trusted developer β=1.38; social/gamification *not significant* | 🔴 Strong — experimental |
| **Glanceability + audio wins live UX** | Nike Run Club analysis (Multiple Resource Model): visual channel is busy with road; audio cues shift load; one huge metric = salience | 🟡 Medium — theory + product data |
| **Depth without disclosure = clutter** | Garmin Connect review 2026: deepest stack (Load, ACWR, Readiness) but "poor signal-to-noise for casuals" | 🟡 Medium — expert review |
| **Privacy concern is identity-shaped** | CHI 2025, N=322: 64% say identity shapes sharing comfort; 58% experienced a privacy risk; top mitigation = don't share (78%) | 🔴 Strong — large N |
| **Upstream users ask for voice/lap/elevation** | #2038/#2400 voice per-metric (open, 2024-2026), #2039 lap pace repeats, #2272 button blocks metrics, #1570/#1873 GPS elevation, #1973 target HR | 🔴 Strong — direct issue tracker |

**One-line synthesis:** Users don't choose a tracker for social features. They choose it for **trust (GPS honesty), ease (30s to first record), and mastery (am I improving?)**. Social is retention garnish, not acquisition driver — and public competition actively harms wellbeing.

---

## 2. ✅ ACTUALLY NEEDED — Build These

### Tier 1: Trust & Core Loop (P0 — no evidence, no ship)

| Need | Why (evidence) | Waykeep status | Effort |
|---|---|---|---|
| **Honest GPS UI** — `±Nm · used/visible sats · elapsed` always on recording screen | 50% GPS complaints; your own runs: TTFF 90s vs 201s, both invisible to user before beta.3 | ✅ Logged, ❌ not surfaced as confidence | S |
| **Customizable voice cues per metric** (distance/time/pace/HR, each own trigger) | Upstream #2038/#2400 open 2 yrs; Nike audio-channel research; #2039 interval pace repeats same value | ❌ 2 global prefs only | M |
| **Lap button + correct interval pace** (last N m from end) | #2039; interval training is core runner workflow; Sun Runners N=64: feedback/performance = top motivator | ❌ Missing | S |
| **GPS elevation fallback** (smoothed, labeled "±GPS") when no barometer | #1570/#1873; most budget Androids (your POCO included) have no barometer → permanent "–" looks broken | ❌ Barometer-only | S |
| **Battery forecast** (`64% · ~6h20 at this rate`) | Walker/Forgade pattern; 2h+ runs = drop-out anxiety; cheap to compute | ❌ Missing | S |
| **Journal that survives a 1h run** (sample `gps_fix`, dedup payload) | Your data: 4MiB cap at 08:05, 30 min into run — diagnostics die exactly when needed | ⚠️ Cap hits | S |

### Tier 2: Insight & Retention (P1)

| Need | Why (evidence) | Waykeep status | Effort |
|---|---|---|---|
| **Training load verdict** (Acute 7d vs Chronic 28d → Productive/Recovery/Strained) | Garmin's core value; SDT competence need; only 1/8 apps personalize motivation (SDT review) | ❌ PBs + recap only, no verdict | L |
| **Rolling-7-day + streaks + personal goals** | Strava Monday-0km complaint; NRC streaks +22% activation; Sun Runners: challenges 24% top motivator (personal, not public) | ❌ Calendar month only | M |
| **Target-HR / zone alerts live** | #1973; Forgade 95% warning = safety; HR in notification #1693 | ❌ HR zones post-run only | S |
| **Turn cues + off-route vibrate (40m)** | Komoot/Footpath/Agad pattern; your Suggested Routes dead-ends without it | ⚠️ Dashed line only | M |
| **Personalized home hero** (new / casual / training → different home) | NRC segmentation: +34% weekly sessions; Aalto: feature depth per user type | ❌ One feed for all | M |

### Tier 3: Completeness (P2)

| Need | Why (evidence) | Effort |
|---|---|---|
| GPX import full fidelity (HR min/max/avg) — #1588/#181 | Migrants from Strava/Garmin lose best data, feels broken | M |
| Health Connect **import** (watch HR/sleep) | Watch users live there; export-only = half integration | M |
| Offline router (bundle BRouter segments) | Suggested Routes fails off-grid — when you need it most | L |
| Private safety share (opt-in SMS/browser link, expires on stop) | Beacon pattern; CHI N=322: granular + default-off is the only safe shape | L |
| Private Year-in-Sport story cards | Strava Wrapped virality; your share-image renderer already exists — ~free | M |

---

## 3. ⛔ NOT NEEDED — Do Not Build (Evidence Against)

| Temptation | Evidence against | Verdict |
|---|---|---|
| **Public social feed / leaderboards / segments** | CHI 2025: ↑stress, ↓intrinsic motivation; 10+-junk-segments per trail (Reddit); women less comfortable sharing (CHI N=322); DCE: social not significant for choice | **Harmful. Skip.** If ever: friend-scoped, self-vs-last-month only |
| **Cloud accounts / sync** | 55% worry about AI/data privacy (ABC Fitness 2025); your moat is no-account; upstream deliberately removed Drive/Analytics | **Anti-moat. Skip.** Offer file export + Health Connect |
| **Calorie estimation / AI coaching black box** | Upstream removed calories deliberately; ABC: 37% cautious of over-reliance, 35% distrust lack of personalization; Garmin VO2max error bars | **Skip.** On-device, explainable load verdict only |
| **18-chart dashboards / full Garmin clone** | Garmin review: "cluttered, overwhelming for casuals"; signal-to-noise poor | **Skip.** One verdict → tap → factors |
| **Gamification slot machine (confetti, coins)** | Turku thesis: competition → validation pressure; Smartwatch N=378: 4 motivation segments need *different* elements; one-size fails | **Skip.** Streaks + PBs + load = enough |
| **Route planning as full Komoot clone** | Komoot sells regions; Strava paywalls planner; your Suggested Loops (BRouter) already cover the 80% ("give me a 5km loop from here") | **Skip full planner.** Finish loops first |

---

## 4. ⚠️ NICE-TO-HAVE (Build Only After Tier 1-2)

- Record button position pref (#2272) — XS effort, do anytime
- HR in notification picker (#1693) — S effort, bundle with voice work
- Delete-all crash fix (#2401) — same class as your Finish crash, audit now
- Onboarding 30s flow — DCE says ease-of-use is #2 driver, but recording screen already *is* the onboarding for a tracker
- Year-in-Sport **video** — static cards first; video is polish, not evidence

---

## 5. The Decision Rule

Ship a feature iff it passes **3 of 4**:

1. **Trust:** Does it make GPS/data more honest?
2. **Live:** Does it help with eyes off phone (audio/glance)?
3. **Mastery:** Does it answer "am I improving / should I rest?" privately?
4. **Upstream:** Does it close a numbered Codeberg issue (cite it)?

**Fails 2+ = don't build.** Public feed fails 1,3,4. Cloud fails 1,4. Dashboard clone fails 2,3.

---

## 6. Bottom Line

Waykeep's next 90 days should be **beta.5 Trust + Live Run → beta.6 Insight Without Shame → 1.0 Plan→Run→Share**. The evidence says users will forgive missing social features. They will *not* forgive lying GPS, paywalled basics, or a recap that shames them on Monday morning. Build honesty, voice, laps, elevation, load, and streaks. Leave the feed, the cloud, and the leaderboards to Strava — that's why their users are leaving.
