# Waykeep — product, UX and brand review

**24 November 2026 · Release scope supplied by the maintainer · Implementation: Java + XML, Material 3, Android 8.0+**

> **Revision note (2026-11-24):** This remains a decision brief, not an approved full redesign. The three-font system and 2×2 goal widget are preserved. The week-first Home is **proposed, not approved**. Map retirement is a separate scoped task. The weekly-streak algorithm is preserved; only stale help copy and screenshots change. Effort estimates include implementation and stated checks; translation is scoped separately.

## Page 1 of 15 — decision summary

**Waykeep should be known for this: “The phone activity log that makes your week easy to read.”**

Lead with useful recording and readable progress. Privacy is the supporting promise. The app already has the ingredients: phone GPS recording, a rolling seven-day total, an average, a goal, a weekly streak and launcher widgets. Give those a clearer hierarchy before adding features.

**One-line pitch:** Record your runs, rides and walks, then see your last seven days at a glance—on your phone, without an account.

| Decision | Recommendation |
|---|---|
| Primary audience | Phone-only runners on familiar routes, particularly people who also walk or cycle and want a simple activity log. This is a product-fit hypothesis, not demonstrated demand. |
| Secondary audience | FOSS users who want the same practical workflow. They are a useful early feedback audience, but privacy alone does not distinguish Waykeep from existing FOSS trackers. |
| Core loop | Record → save → inspect the activity → see the seven-day total update → return when ready. |
| Home hierarchy | **Proposed, not approved:** start activity; last-seven-day total and goal; latest activity; secondary history. Test a week-first arrangement before replacing the route-first Home. |
| Meaning of progress | All activities contribute to the rolling seven-day distance goal, as confirmed by the maintainer. Label that explicitly. It is not a running mileage total or a training-load score. |
| What to keep | Weekly streak, voice announcements, laps, pause controls, export/import, two widgets, speed/elevation charts and optional share cards. |
| What to remove | Daily-streak language; unsupported marketing claims; decorative route repetition; mixed-sport “fastest” comparisons. Preserve underlying activity records. Map retirement is a separate scoped task, not part of these UI changes. |
| First three bounded changes | Clarify Start/Pause labels and targets; label the seven-day goal and weekly streak; make the selected statistics period visible. Each is a bounded implementation with stated checks; translation is scoped separately. |

**Is privacy a reason to install or only to stay?** Both, for different people. It is a plausible install reason for privacy/FOSS users. For the primary audience, test the practical reason first: recording without setup and seeing the week add up. Privacy and portable files can support trust over time; no evidence here establishes a retention effect. OpenTracks and FitoTrack already publish privacy-focused offers, so “private GPS tracker” is not an ownable distinction. [C9–C10, page 3]

**Audience priority:** 1. phone-only runners; 2. FOSS crowd; 3. trekkers using Waykeep only as a log; 4. randonneurs/brevet riders. This is an estimated suitability ranking under the stated constraints. **No reliable public data** establishes the size or conversion potential of Waykeep’s reachable audience.

**Main finding from the supplied images:** route artwork has more visual priority than the information that makes the proposed position useful. The progress strip and analytic views need space, labels and stronger text hierarchy—not more decoration. [W1–W3, page 2]

**Do not promise:** better GPS accuracy than competitors, all-day battery life, complete offline maps, training recommendations, automatic backup, emergency assistance or measured habit improvement. None is established by the fact base or this review.

This is a specification, not a shipped redesign. Fifteen sections approximate report pages; Markdown pagination varies. Example activity values are fabricated; effort and impact are estimates.

---

## Page 2 of 15 — evidence, contradictions and boundaries

### What was inspected

**Every source URL was accessed on 2026-11-24.** Screenshots were visually inspected; website HTML and relevant source files were read. Competitor evidence is official documentation/developer store copy, not hands-on testing. Weaknesses are assessed mismatches for the target job unless explicitly documented.

| ID | Source and use |
|---|---|
| W1 | Six raw screenshots: [Home](https://waykeep.gossorg.in/shots/home-screen.webp), [personal bests](https://waykeep.gossorg.in/shots/personal-bests.webp), [activity stats](https://waykeep.gossorg.in/shots/track-detail-stats.webp), [chart](https://waykeep.gossorg.in/shots/track-detail-chart-pace-distance.webp), [intervals](https://waykeep.gossorg.in/shots/track-detail-intervals.webp), [monthly aggregate](https://waykeep.gossorg.in/shots/aggregated-stats-month.webp). Used for visual findings, never activity-performance conclusions. |
| W2 | [Eight Play-size renders](https://github.com/IntegerAlex/waykeep/tree/main/fastlane/metadata/android/en-US/images/phoneScreenshots). All eight inspected. These are prepared assets, not proof of a live store listing. |
| W3 | [Fourteen marketing renders](https://github.com/IntegerAlex/waykeep/tree/main/screenshots-marketing). All fourteen inspected, including live recording, share editor, share sheet and out-of-scope maps/routes. |
| W4 | Website: [Home](https://waykeep.gossorg.in/), [changelog](https://waykeep.gossorg.in/changelog), [guides](https://waykeep.gossorg.in/guides), [running](https://waykeep.gossorg.in/running), [privacy](https://waykeep.gossorg.in/privacy). Used for copy critique and consistency checks. |
| W5 | [Repository snapshot](https://github.com/IntegerAlex/waykeep/tree/ac3ac595370cf62ab6cab070cd8a0357273835b6). Tree resolved to commit `ac3ac595370cf62ab6cab070cd8a0357273835b6`; relevant Java, XML and README read. Snapshot is an implementation reference, not authority over the supplied brief. |

### The brief wins where sources disagree

The maintainer explicitly reconfirmed this hierarchy during the review.

| Source discrepancy | Treatment in this document |
|---|---|
| Website describes interactive cached maps and suggested loops; images show Map tabs and Routes navigation. | Map retirement remains the confirmed target, but it is a separate implementation task with its own dependency, migration, help and regression checks. Do not bundle it with activity-header or Home layout cleanup. |
| Home image says “1-day streak”; current `WeeklyStreak.java` computes consecutive active weeks. | Preserve the weekly-streak algorithm. Correct only stale help text and screenshots that display the old daily-streak wording. |
| Website says direct APK distribution, with no store listing planned for that release. | Treat Play as the planned distribution channel in this brief. No live Waykeep listing, reviews, installs or rankings are claimed or cited. |
| Repository assets include Inter, Barlow Condensed and JetBrains Mono; the brief describes plain sans. | Keep all three existing faces. Improve hierarchy, size and contrast without a font replacement. |
| Website says network is only map tiles; brief also allows GNSS assistance. | Correct the explanatory copy to include both. Do not promise “never connects to the internet.” |
| Website offers more formats, sensors and other extras than the brief. | Store copy includes only confirmed brief capabilities. Inspecting an extra does not put it in scope. |

**Unverified:** hitboxes, TalkBack, pause/save behavior, permission denial, recovery, small widgets, font scaling and battery drain. No APK was run. These are runtime checks, not reported defects.

Waykeep acquisition, completion, retention and engagement: **no reliable public data**. Unknown does not mean zero.

---

## Page 3 of 15 — competitive set

Sources accessed **2026-11-24**. Positioning, loops and tradeoffs are interpretations of published features, relative to a simple phone log. Regional prices and market-share claims are omitted.

| App / primary source | Positioning | Core loop | Monetisation | Does well | Does badly for this job / tradeoff | Waykeep must NOT copy |
|---|---|---|---|---|---|---|
| **Strava** [C1](https://support.strava.com/en-us/articles/15402044-what-features-are-included-in-a-strava-subscription?mobile_site=true), [free tier](https://support.strava.com/en-us/articles/15402187-is-strava-free) | Shared athletic identity and performance comparison | Record/upload → compare → return | Free tier + subscription | Connects recorded efforts with analysis, goals and segment comparison | Rich comparison is excessive for simply keeping a log; advanced goals/analysis sit in the subscription offer | Feed, segments, rankings, subscription gates |
| **Garmin Connect** [C2](https://www.garmin.com/en-US/newsroom/press-release/wearables-health/elevate-your-health-and-fitness-goals-with-garmin-connect/) | Companion for Garmin health and training data | Device records → sync → review → train | Free core; optional Connect+; adjacent hardware business | Brings device data, charts and training context together | Device-dependent benefits are a poor basis for a phone-only proposition | Device ecosystem, AI insights, broad health dashboard |
| **Nike Run Club** [C3](https://www.nike.com/nrc-app), [developer listing](https://play.google.com/store/apps/details?gl=in&hl=en-GB&id=com.nike.plusgps) | Running with guidance and encouragement | Choose guidance → run → celebrate/repeat | Free app; part of Nike’s brand ecosystem, not an app-revenue estimate | Guided runs and plans provide a clear reason to start a run | Coaching/community scope exceeds the needs of someone who already knows what to run | Audio-content production, coaching library, challenges |
| **Adidas Running** [C4](https://play.google.com/store/apps/details?gl=in&hl=en-GB&id=com.runtastic.android), [Premium help](https://www.adidas.com.vn/en/help/sea-adiclub/what-is-adidas-runtastic-premiums) | Activity tracking tied to goals, challenges and rewards | Record → review → challenge/reward → repeat | Free entry; official help describes paid Premium; current regional entitlements require verification | Combines goals, activity summaries and training guidance | Rewards/community introduce another reason to manage an account instead of just a log | Rewards system, brand campaigns, social challenges |
| **Komoot** [C5](https://support.komoot.com/hc/en-us/articles/10163258809626-komoot-plans-Maps-and-Premium) | Plan and follow outdoor routes | Plan → navigate → save route | Free initial region; one-time map purchases; Premium subscription | Route planning and offline navigation have a clear product purpose | Map/account purchases and route planning are unnecessary for a known-route run | Routing engine, regional map catalogue, live tracking |
| **AllTrails** [C6](https://support.alltrails.com/hc/en-us/articles/37200882853140-The-benefits-of-AllTrails-premium-membership), [plan comparison](https://www.alltrails.com/welcome) | Find and follow trails | Discover → choose trail → navigate → log | Free Base; paid Plus and Peak | Trail content, offline areas and wrong-turn guidance | Discovery and navigation are a different job; offline tools are in paid plans | Trail database, reviews, conditions and route moderation |
| **OsmAnd** [C7](https://www.osmand.net/docs/user/purchases/android/?current-os=ios) | Detailed offline mapping and navigation | Download maps → configure → navigate/record | Free edition; paid app/Maps+ purchases; subscriptions including Pro | Offline map depth and specialist layers | The breadth of maps, layers and purchases makes the scope much larger than a focused log | Map rendering platform, layers, download manager |
| **Ghostracer** [C8](https://play.google.com/store/apps/details?hl=en-IN&id=com.bravetheskies.ghostracer) | Race virtual competitors with phone/watch data | Select ghost/route → record and compare → export | Free entry with in-app Premium features | Configurable live metrics, sensors and ghost comparison | Its listing explicitly warns of a broken Strava leaderboard integration; dependency risk is tangible | Strava API dependency, segments, Wear OS scope expansion |
| **FitoTrack** [C9](https://play.google.com/store/apps/details?hl=en_IN&id=de.tadris.fitness) | Privacy-oriented FOSS workout recording | Record → view map/charts → keep history | Free, ad-free; no paid tier stated in cited listing | Already offers the core private activity-recording job | Published offer gives little reason to choose it specifically for a weekly glance; not proof the feature is absent | A privacy-only pitch with interchangeable screenshots |
| **OpenTracks** [C10](https://github.com/OpenTracksApp/OpenTracks/blob/main/README.md), [Play](https://play.google.com/store/apps/details?hl=en_IN&id=de.dennisguse.opentracks.playstore) | Private sport recorder with portable data | Record → inspect → export/open dashboard | Free F-Droid builds; paid Play edition; donations | Export formats, sensor support and explicit local ownership | Separate map/dashboard handoff adds a step; otherwise it is the closest substitute and upstream foundation | Feature-for-feature duplication without a clearer weekly experience |
| **Geo Tracker** [C11](https://geo-tracker.org/en/faq), [developer listing](https://play.google.com/store/apps/details?hl=en_IN&id=com.ilyabogdanovich.geotracker) | Record and reuse geographic tracks | Record → inspect statistics/map → export/reuse | Free personal-use recording; current FAQ lists optional Pro. Older listing describes donations | Local tracks, file portability and practical GPS troubleshooting | Route/navigation emphasis differs from reviewing an activity habit | Track-editing suite, folder sync and commercial-use tiering |
| **Samsung Health** [C12](https://www.samsung.com/in/apps/samsung-health/) | Broad daily health and fitness hub | Capture activity/health → review → repeat | Free app; adjacent Samsung device ecosystem | Offers phone exercise tracking alongside broader wellness data | Sleep, diet, stress and device-specific services enlarge the scope far beyond recording a run | Nutrition, sleep, medical-looking scores, device ecosystem |

**Freshness:** Geo Tracker’s FAQ supersedes older donation-only copy, but build entitlements may differ. Adidas’ regional help describes Premium; its current listing emphasises rewards. Neither establishes a specific current local paywall or price.

---

## Page 4 of 15 — five credible gaps and audience ranking

### Five gaps Waykeep can credibly occupy

These are execution opportunities, **not unique-feature claims**, grounded in the brief and page 3 competitors.

1. **A weekly glance without a training suite.** Put rolling distance, its goal and the active-week streak where the user can read them before opening an activity. The widgets extend the same information to the launcher. Strava and Garmin include progress tools in their published offers; this does not establish demand for Waykeep. [C1–C2]
2. **A phone recorder for a route the user already knows.** Prioritise Start, Pause, distance, time and usable pace readouts. Route discovery and navigation are deliberately outside the job. Komoot, AllTrails and OsmAnd demonstrate how much scope those other jobs entail. [C5–C7]
3. **Consistency that allows rest days.** Keep the existing weekly streak; explain that one activity makes a week active. This is an available interaction distinction, not evidence of improved adherence. NRC explicitly promotes run-day streaks among its achievement features. [C3; W5 `WeeklyStreak.java`]
4. **A readable private log, not merely a private database.** Make history, periods and record categories understandable without knowing FOSS terminology. FitoTrack and OpenTracks establish that privacy and recording alone are already supplied. Waykeep must earn preference through presentation. [C9–C10]
5. **Sharing and leaving without joining a platform.** Keep image export, GPX/KMZ and optional Health Connect export close to the saved activity. These are portable outputs with no social graph to operate. OpenTracks and Geo Tracker already support portability; Waykeep can make the choice more legible, not claim to invent it. [C10–C11]

### Rank by product fit × scope burden × reachable feedback

**Estimated ranking; no reliable public data** for reachable audience size or adoption. Audiences overlap.

| Rank | Audience | Evidence for fit | Main cost/gap | Practical verdict |
|---|---|---|---|---|
| 1 | Phone-only runners, familiar routes | Existing GNSS, announcements, laps and seven-day review directly serve recording. NRC and Adidas show phone-running products can centre this activity. [C3–C4] | Must be readable outdoors and dependable with the screen off; this review did not establish reliability | Win the narrower self-directed runner who wants a log, not coaching or social comparison |
| 2 | FOSS crowd | OpenTracks and FitoTrack explicitly serve private/open-source tracking. [C9–C10] | Strong existing alternatives; a fork needs a clear reason to switch | Best early feedback fit, weaker differentiation if the pitch is only privacy |
| 3 | Trekkers | Hiking recording and GPX/KMZ support are in the brief | Offline navigation and trail knowledge are central competing offers. [C5–C7] | Serve as a supplementary log; do not market it as the primary tool for unfamiliar terrain |
| 4 | Randonneurs / brevets | Cycling recording and export fit a secondary log | Long-duration recording, charging interruptions and route adherence raise validation demands | Defer targeting until endurance behavior has been tested; do not infer it from short runs |

For scale of the last requirement, ACP’s published 2026 organiser rules allow **13 h 30 min for 200 km and 40 h for 600 km**. Those are event limits, not estimates of Waykeep battery life or typical rider times. [ACP rules, accessed 2026-11-24](https://www.audax-club-parisien.com/wp-content/uploads/2026/02/Reglement-organisateur-fevrier-2026.pdf).

Ask a willing runner to demonstrate normal use and a FOSS tracker user to compare weekly summaries. Report cases, not market percentages.

---

## Page 5 of 15 — positioning and objections

### Three candidate positions

| Candidate | Evidence | Weakness | Decision |
|---|---|---|---|
| **The private GPS recorder** | No account, local SQLite, no telemetry, portable files | FitoTrack and OpenTracks already make this offer. Someone indifferent to privacy has little new reason to install. [C9–C10] | Supporting promise |
| **The weekly activity log for phone runners** | Seven-day distance, average, goal, weekly streak, widgets and recording already exist | Needs clearer hierarchy; total includes cycling/walking, so it must not masquerade as running mileage | **Choose this** |
| **The offline outdoor recorder** | GNSS recording and file export work within local architecture | “Outdoor” invites expectations of maps and navigation; no verified endurance/battery advantage. [C5–C7; ACP] | Describe recording capability, not headline positioning |

**Pitch:** Record your runs, rides and walks, then see your last seven days at a glance—on your phone, without an account.

**For:** self-directed Android runners who carry their phone on familiar routes and want distance, time, pace and a weekly pattern. Walking, cycling and hiking remain supported. No minimum activity frequency is implied.

**Not for:** guided coaching, social comparison, race rankings, navigation, emergency location sharing, cloud recovery or replacing a watch ecosystem.

### Four strongest objections

| Objection | Honest answer | Product implication |
|---|---|---|
| “I can already record a run for free.” | Yes. Try Waykeep if you want the recording and the last-seven-day summary together, without account setup or a feed. If you need coaching or friends’ activity, another product fits better. | The first screenshot must show the weekly summary clearly; “free” alone cannot be the pitch. [C3, C9, C12] |
| “If I lose my phone, do I lose the history?” | There is no cloud recovery. Export GPX or KMZ and keep a separate copy. An export is a file you manage, not automatic backup. | Give export a clear name and verify re-import before describing it as a migration workflow. |
| “Where is the map?” | Waykeep records the route. Open it in a compatible separate map app; share images can include a static map. The target release retires the in-app map only through the separate map-retirement task. | Name the action “Open in map app.” Do not promise an in-app Map tab after that task ships. |
| “Can I trust it not to lose a run or drain my battery?” | No universal guarantee is supported. Try a short recording with the screen off on your phone before relying on it for a long outing. GPS and Android restrictions can affect recording. | Clear recording state and visible pause controls come before brand polish. Keep battery tests device-specific. Geo Tracker documents the same category of Android background limitation. [C11] |

**Friend recommendation:** “I use Waykeep to record on my phone and see how my week adds up. It keeps the files on the phone and doesn’t need an account.”

**Privacy-indifferent runner test:** the pitch still offers recording and a seven-day overview after removing privacy. Show Home and the widget; ask what is useful. If the answer is only privacy, the practical benefit is not visible.

**Important limitation:** a kilometre cycled and a kilometre walked contribute equally to this total. Call it **activity distance**, never effort, fitness, readiness or training load. Do not prescribe a default distance target or increase it automatically.

---

## Page 6 of 15 — UX audit, changes 1–6

Evidence: W1–W3, with W5 implementation references. Estimated effort: **S 2–6 hours; M 8–16; L >16/cross-cutting**, covering implementation plus the checks stated in this report. Translation and full device-matrix QA are estimated separately. Impact is qualitative. **No backend** is needed.

| ID / screen or element | Observed problem | Concrete change | Effort | Expected impact / backend |
|---|---|---|---|---|
| U1 · Home route hero (**proposed, not approved**) | Route artwork occupies a large upper region; the seven-day summary sits in a thin strip below. The visual hierarchy prioritises the last route over the proposed weekly benefit. | Prototype a week-first arrangement and compare it with the route-first Home before replacing it. Keep this separate from copy and label fixes. | M | Medium: validates the proposed hierarchy before a larger change. **No** |
| U2 · Home progress strip | Small uppercase “last 7 days,” goal percentage, average and the old daily-streak wording compete in one band. Period and sport scope are unclear. | Use “Last 7 days · all activities”; “12.0 of 20.0 km”; “4-week average: … km/week”; “3 active weeks in a row.” Put the streak on its own line. Label the goal as rolling seven days; reuse existing calculations. Correct the stale help text and only screenshots that still show the old daily-streak wording. | S | High: prevents calendar-week/running-only assumptions. **No** |
| U3 · Home Start and recording Pause | Start is an unlabeled red record symbol; Pause in the recording render is an unlabeled cyan icon. Color/shape have to carry the meaning. | Use a visible **Start activity** label and a visible **Pause** label, switching to **Resume** or **Stop recording** in those states. Keep the existing placement and handlers; verify the Material FAB sizing rather than enlarging controls by default, with at least a 48dp touch target. Reserve red for the existing record/stop semantics and keep TalkBack labels in sync with the visible action. | S | High: clearer state and one-handed action. **No** |
| U4 · Saved activity header | Stats, Intervals and Chart each repeat the large route header, leaving little vertical room for their actual contents. | Replace the repeated hero with a compact title/date/distance header; show route artwork only in the summary. Do not change map tabs or navigation in this task; map retirement has its own scope. | M | High: more usable detail space without mixing in map work. **No** |
| U5 · Saved activity Stats | Moving pace/time are visible; elevation is a range. More stats is a small link. A runner can easily read these as whole-session figures. | Keep “Moving pace” and “Moving time” explicit; show **Elapsed time** alongside them. Label the altitude field **Elevation range**, not simply Elevation. Make **All statistics** a 48dp-or-higher row. Do not silently substitute moving time for elapsed. | S | Medium/high: makes comparisons interpretable. **No** |
| U6 · Intervals table | The split selector is followed by a cramped three-column table; gain/loss wraps into stacked values and the next split is partly off-screen. | Rename tab **Splits**. Use 16sp distance/pace with a 14sp secondary line “Gain … m · Loss … m.” At large text, render each split as a vertical row/card rather than compressing columns. Mark any short final split “Partial.” | M | High: easier review without tiny columns. **No** |

### Implementation boundaries

Reuse `TrackListActivity.renderInsight()` and `InsightSummary`. Label the rolling distance goal separately from the calendar-week streak. Preserve `WeeklyStreak` behavior: an unfinished current week does not erase the prior streak.

U3 changes labels/targets only; inspect Finish before altering gestures or confirmations. U4 preserves coordinates and file handoff.

---

## Page 7 of 15 — UX audit, changes 7–12

| ID / screen or element | Observed problem | Concrete change | Effort | Expected impact / backend |
|---|---|---|---|---|
| U7 · Chart controls | Axis choices and metric choices appear together as a crowded chip row; chart labels are small and the plot gets limited space. | Separate **Horizontal axis: Time / Distance** from **Metric**. Show one metric at a time, label units beside its name, and use at least 14sp axis/readout text. Move controls above a larger plot after U4. Add a text summary for nonvisual access. | M | Medium/high: distinguishes controls and makes readings accessible. **No** |
| U8 · Aggregated statistics period | The supplied monthly screen says “Aggregated stats” without a visible month/date range. Period selection appears to depend on a bottom icon. | Change heading to **Activity totals**. Add a visible **November 2026** period selector and actual date range beneath it. Retain existing filter behavior, with a label such as **Change period**. | S | High: users can tell what is being summed. **No** |
| U9 · Personal bests | The image presents running and cycling achievements under one unqualified heading. Repository `computeBests()` loops across all tracks without a sport filter. | Add a sport selector and include sport in each record label. Use pace for running and speed for cycling. Hide cross-sport “fastest average.” If scope cannot accommodate the filter yet, rename to **Activity records** and keep only clearly typed records. | M | Medium/high: avoids misleading comparisons. **No** |
| U10 · Live recording metric hierarchy | Recording render gives most space to distance, with smaller moving time, speed and average speed. It does not state which activity type is active. | Show the active activity name. For running/street running, put pace, distance and time in a simple stack; cycling uses speed. Keep the user’s existing metric preference available. Use text **Recording**, **Paused** or **Waiting for GPS** from actual service state, not inferred from button color. | M | High: mid-run glance identifies the right metric and state. **No** |
| U11 · Share editor and share sheet | Multiple tiny size/background controls compete beneath the preview; selected Share uses a different accent. The share-sheet render also offers AI analysis outside this brief. | Keep **Square** and **Portrait** as readable labeled choices, with size shown below. Use the same primary action color as the app. Put **Save image** and **Share image** in separate 48dp-or-higher actions. Exclude AI analysis from target release navigation and copy. | M | Medium: more predictable output with less choice overhead. **No** |
| U12 · Small annotations across Home, stats and records | Uppercase captions, detailed timestamps and pale chart text require close reading. The image does not prove numeric contrast or hitbox failures, but the density is visible. | Replace all-caps metric labels with sentence case, use 14sp minimum for required metadata and 16sp for body/actions. Show short local dates; move timezone detail into expanded metadata. Bind all colors to the tested roles on page 8. Reflow rather than shrink at 200%. | L | High across the app, but wider regression surface. **No** |

### Acceptance gates and unanswered behavior

Screenshots cannot establish the following. Ask these in implementation review rather than filling gaps with assumed failures:

- **Recorder:** what exactly happens to elapsed and moving time during manual pause and auto-pause? Does Finish lead to an editable saved activity or an intermediate form?
- **Permissions:** what screen is shown after location denial, and what does the app say when Android can restrict background recording?
- **Accessibility:** do TalkBack focus order and announcements follow the visible order? Does Pause remain reachable at 200% text on a narrow display?
- **Share output:** can the user already omit location/name/date? What happens when a tile fails? Does export preserve required attribution?
- **Widgets:** what is rendered at the smallest allowed size and after import, delete, midnight and timezone changes?

These are code/runtime checks; no additional screenshots are required.

**Outdoor acceptance:** test sun, one-handed use while standing, compatible gloves, screen off/on, weak GPS and no mobile data. Larger targets do not support nonconductive gloves. Stale pace must not appear live after GPS loss.

---

## Page 8 of 15 — colour kit and AA checks

Keep cyan as identity. Separate identity from text contrast: **white on #00A6B8 is 2.94:1 and fails AA for ordinary text, and even the 3:1 large-text threshold.** Use dark ink on that fill. Use the existing dim cyan on light surfaces.

Ratios below were calculated from opaque sRGB hex values using WCAG relative luminance and `(Llighter + 0.05) / (Ldarker + 0.05)`, rounded to two decimals. They are pair checks, not a claim that the whole app is accessible. Normal text needs 4.5:1; meaningful graphical boundaries need 3:1. [WCAG contrast](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html), [non-text contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html); accessed 2026-11-24.

| M3/custom role | Light | Dark | Tested foreground/background; light / dark |
|---|---|---|---|
| `colorSurface` / window background | `#F7FAFA` | `#101819` | Base for following text checks |
| `colorSurfaceContainer` | `#F0F5F5` | `#1B2729` | Raised cards; separation is not a status signal |
| `colorOnSurface` | `#172124` | `#EDF5F5` | On surface: **15.64 / 16.27**; container: **14.92 / 13.87** |
| `colorOnSurfaceVariant` | `#46585B` | `#B9CBCD` | On surface: **7.13 / 10.70**; container: **6.80 / 9.12** |
| `colorPrimary` | `#007A87` | `#00A6B8` | Text/icons on surface: **4.84 / 6.13**; container: **4.62 / 5.22** |
| `colorOnPrimary` | `#FFFFFF` | `#001F24` | On primary fill: **5.08 / 5.85** |
| `colorPrimaryContainer` | `#C4F1F5` | `#004F58` | Pair with next row only |
| `colorOnPrimaryContainer` | `#00363D` | `#C4F1F5` | On primary container: **10.81 / 7.63** |
| `colorOutline` | `#65777A` | `#819496` | On surface: **4.47 / 5.67**; container: **4.27 / 4.83**. Boundary/icon role, **not small text** |
| `colorOutlineVariant` | `#D0DCDE` | `#354548` | Decorative dividers only; do not use as sole interactive boundary |
| `colorError` | `#BA1A1A` | `#FFB4AB` | Error text on base surface: **6.16 / 10.60** |
| `waykeepWarning` | `#765700` | `#F4CE72` | Warning text on base surface: **6.39 / 11.93** |

Errors/warnings include text and use the base surface. Recording uses a static icon and text. Charts use primary, one metric at a time; hue must not be the only distinction.

**Java/XML implementation:** place identical semantic resource names in `res/values/colors.xml` and `res/values-night/colors.xml`. Bind them in `styles_theme.xml` to the installed M3 library’s supported roles. Alias secondary/tertiary roles to the same restrained palette if components require them; no extra accent is needed. Use `?attr/colorOnSurface` in app views. `RemoteViews` widgets use concrete day/night `@color` resources rather than assuming the launcher resolves the app theme.

Offer **System / Light / Dark**, default System. Never switch at workout start. No translucent text or text directly over maps; use solid panels. Defer dynamic color pending independent contrast checks.

Source for the resource/night-mode implementation: [Android Views dark theme](https://developer.android.com/develop/ui/views/theming/darktheme), accessed 2026-11-24. Test outdoors; a contrast ratio does not simulate screen glare.

---

## Page 9 of 15 — type, mark and iconography

### Keep the existing three-face system

Keep Inter, Barlow Condensed and JetBrains Mono. This scale adjusts hierarchy and sizes; it does not replace the bundled typefaces. Inter handles UI text, Barlow Condensed stays on large display values, and JetBrains Mono stays on small tabular measurements.

| Role | Face | Size / nominal line height | Weight | Use |
|---|---|---|---|---|
| Display | Barlow Condensed | 48 / 56sp | 700 | One live primary metric |
| Headline | Barlow Condensed | 40 / 48sp | 700 | Home seven-day distance |
| Title large | Inter | 24 / 32sp | 500 | Screen title |
| Title medium | Inter | 20 / 28sp | 500 | Activity name, section heading |
| Body large | Inter | 18 / 26sp | 400 | Live secondary measurements |
| Body | Inter | 16 / 24sp | 400 | Descriptions, values, form text |
| Label/action | Inter | 16 / 24sp | 500 | Start, Pause, selectors |
| Supporting text | Inter | 14 / 20sp | 400 | Units, dates, chart labels, explanatory metadata |
| Small measurement | JetBrains Mono | 12 / 16sp | 400 | Streak counts and tabular metadata |

Keep number/unit pairs together; use tabular figures where the selected face supports them. No leading-zero stopwatch styling for ordinary distances. Use the existing distance/unit formatters; spoken pace should become “5 minutes 30 seconds per kilometre,” not punctuation read aloud.

At 200%, stack metric columns, allow labels to wrap, and let content scroll. Use `wrap_content` with minimum heights, never a fixed text-box height. Do not shrink text with auto-size to conceal overflow. Set text sizes in sp and spacing in dp. For custom canvas text, use `TypedValue.applyDimension()` rather than multiplying by `scaledDensity`; Android 14 scaling is nonlinear. API 26 layouts need compatible line-height handling through the installed support library. [Android font scaling](https://developer.android.com/about/versions/14/features), accessed 2026-11-24.

### Logo: a route-shaped W, not a map pin

**Monogram geometry:** a 48 × 48 viewBox. Draw one unfilled polyline:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">
  <path d="M 9 13 L 16 35 L 24 23 L 32 35 L 39 13"
        fill="none" stroke="currentColor" stroke-width="5"
        stroke-linecap="round" stroke-linejoin="round"/>
</svg>
```

Proposed geometry: broad gaps and a continuous W at 48px. No dot, mountain or compass. Expand strokes if needed for export; test both monochrome inversions.

**Wordmark geometry:** the same W path, scaled to the cap height, followed by outlined Inter Bold `aykeep`. Baseline align the W’s lower extrema with the letters’ baseline; use the font’s own kerning for `aykeep`, with approximately 0.10 cap-height separation after W. Keep the lowercase p descender intact. Export as paths once; retain a text source for edits. At 48px **wordmark height**, it is legible; at a 48px-wide launcher footprint, use only the monogram. Do not squeeze seven letters into the app icon.

**Icons:** one Material-style outlined family, 24dp viewport, optical stroke around 2dp, rounded ends/joins. Standard actions use recognisable existing vectors; do not custom-design Pause, Share or Back. Put icons in at least 48dp focus/touch areas. Keep the existing Material FAB sizing unless the label treatment makes it too small; text remains beside critical controls. Decorative icons should not create duplicate TalkBack stops. [Android accessibility for Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views), accessed 2026-11-24.

---

## Page 10 of 15 — voice, motion and outdoor behavior

### Voice: factual, brief, specific

| Do | Don’t |
|---|---|
| Name the period: “Last 7 days · all activities.” | Say “Your progress” without scope. |
| State what was saved: “Activity saved on this phone.” | Claim “Backed up” when no second copy exists. |
| Describe a state: “GPS signal lost. Waiting for a new location.” | Blame the user or say “Something went wrong” alone. |
| Make goals optional: “Set a distance goal if you want one.” | Use guilt, urgency, or “Don’t break your streak.” |
| Use the actual action: “Export GPX” or “Open in map app.” | Call file export “Sync” or external handoff “Navigation.” |

### Nine example sentences

Proposed state-specific strings, not audited existing screens. Show them when relevant, not as an introductory sequence.

| Context | Example sentence | Placement |
|---|---|---|
| First run 1 | “Record a run, ride or walk with your phone’s GPS.” | Empty Home, above Start activity |
| First run 2 | “Allow location access so Waykeep can record your activity.” | Immediately before the required permission request |
| First run 3 | “Activities stay on this phone; export a copy when you want to keep one elsewhere.” | Brief local-storage explanation, with export help reachable |
| Empty 1 | “Your recorded activities will appear here.” | Empty activity list |
| Empty 2 | “No distance recorded in the last 7 days.” | Seven-day summary with older history present |
| Empty 3 | “Save an activity to see its distance and time here.” | Empty totals view |
| Error 1 | “Location access is off. Allow it in Settings to record your route.” | Permission-denied state with Open settings |
| Error 2 | “GPS signal lost. Recording is still active, but new route points may be missing.” | Only when service state confirms recording continues |
| Error 3 | “The image could not be saved. Choose another location and try again.” | Save-image destination/write failure, not every export error |

Show “saved” only after confirmed storage. Use localised resources/plurals, not English-unit concatenation.

### Three motion principles

1. **A state change must be understandable without animation.** Use short opacity/position transitions, approximately 120–180ms, for noncritical panels. No looping recording pulse, breathing goal bar or animated route on Home.
2. **Keep controls stationary during a recording.** Updating measurements must not move Pause or change the layout width. Never animate every GPS sample or roll the distance counter like a scoreboard.
3. **Respect reduced/disabled animation.** When system animations are disabled, complete transitions immediately. Verify on API 26 and a current Android version. No essential information may exist only during motion. [Android `ValueAnimator.areAnimatorsEnabled()`](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled()), accessed 2026-11-24.

### Field behavior

Bright sun: use large opaque text and allow Light mode. One hand: keep Start/Pause reachable near the bottom without overlaying the last list item. Patchy signal: distinguish GPS loss from lack of mobile data. Battery anxiety: keep recording state visible when returning from screen-off, and use the existing voice announcements when enabled; do not add continuous screen-on behavior.

TalkBack announces state changes once, not every distance tick. Focused metrics expose current values; charts provide summaries. Verify speech and motion on-device.

---

## Page 11 of 15 — widget and share-card templates

### Goal widget: the same meaning as Home

**Default:** the existing 2 × 2 launcher footprint. Preserve the declared `minWidth`/`minHeight` and do not expand the widget as part of this work. Use `RemoteViews` TextViews/ProgressBar; share calculations across both existing widgets. Test the default 2 × 2 bounds and a compact fallback at large text.

| Position | Content | Rules |
|---|---|---|
| Top | **Last 7 days** | 14sp; secondary color. Add “All activities” in a separate line where space allows. |
| Main | **12.0 / 20.0 km** | 30sp bold; fabricate only in previews. Live values use existing formatter. |
| Progress | Horizontal bar | 6–8dp visible thickness; value also written as text. No color-only meaning. |
| Below | **60% of distance goal** | 14sp; when exceeded show “Goal reached · 24.0 of 20.0 km.” Cap the bar, not the recorded distance. |
| Bottom | **3 active weeks in a row** | 14sp; optional second line “This week is active” only when true. |
| Whole widget tap | Open Home | No unlabeled start/stop action; avoid accidental recording from the launcher. |

**No goal:** show seven-day distance and “No distance goal set.” Tapping opens Home where goal editing is available. **No recent activity:** show zero with a calm factual label, not a warning. **Tight bounds / large fonts:** prioritise period, total and goal; remove duplicate percentage copy before reducing text. Do not silently crop the streak. The 2 × 2 footprint is fixed for this work: if the promised content does not fit, simplify the content or provide a compact layout rather than increasing the declared minimum.

TalkBack example: “Last seven days, all activities. Twelve of twenty kilometres. Three active weeks in a row.” Reuse `GoalWidgetProvider`, `widget_goal.xml` and `InsightSummary` [W5]. Verify refresh after data/goal/date changes; no new frequent alarms or networking.

### Share card: a record someone may choose to send

Proposed presets: **1080 × 1080 square; 1080 × 1350 portrait**. Reuse the renderer/share flow.

| Region | Square template | Portrait template |
|---|---|---|
| Safe inset | 64px all sides | 64px all sides |
| Header | Activity type + optional title; 36px body | Same, with room for a two-line title |
| Visual | Static tinted map/route, approximately 520px high | Static map/route, approximately 700px high |
| Primary statistic | Distance at 88px, unit at 36px | Same |
| Supporting statistics | Moving time and moving pace/speed at 40px; labels 30px | Same; optional elapsed-time row |
| Footer | Small Waykeep wordmark plus map attribution | Same |

Use solid light/dark panels under all statistics. A tinted tile is not a guaranteed text background. Keep required provider attribution legible. Do not crop, recolor away or cover attribution supplied by the map source; verify its export terms before shipping. No extra map service or routing request is proposed.

Default proposal: share statistics without a map, with an explicit **Include map** choice. If this is new behavior, budget it as part of U11, not a claimed existing capability. A shape-only route can still identify a location; do not call it anonymous. Make activity title/date optional where feasible. No new privacy-zone engine is needed.

Tile failure: keep a statistics-only preview usable and tell the user “Map unavailable; you can share the statistics.” Do not block local sharing while retrying tiles indefinitely. Preview exactly what will leave the app; no automatic upload.

---

## Page 12 of 15 — Play assets plan

Replace the older map/route-generation renders with captures from the target build. Make weekly progress visible. [W2–W4]

### Icon and feature graphic

**Store icon:** 512 × 512, 32-bit PNG, under 1024KB. Use the W monogram in `#00A6B8` on `#101819`, with no letters smaller than the mark, badge, price claim or artificial notification dot. Keep the silhouette recognisable in monochrome. Use a separate adaptive launcher vector with the mark inside Android’s safe region; test circular and rounded-square masks. [Play asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en), [adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive); accessed 2026-11-24.

**Feature graphic:** 1024 × 500, opaque PNG/JPEG. Headline: **“See your week in motion.”** Use a real captured seven-day summary on a cyan-tinted background, with restrained route-line decoration at the edges. Keep text/content near the centre and avoid a large duplicate app icon. No athlete stock photography, handset mockup, claimed rating or download count. Alt text: “Waykeep activity summary showing distance and goal progress for the last seven days.” [Play asset requirements]

### Six screenshot sequence

Use 1080 × 1920 opaque captures with UI dominant and external captions. Label **Demo data**; keep fabricated dates/distances consistent. Never draw unshipped UI into a capture.

| Order | Actual capture | Caption | Why this slot / capture gate |
|---|---|---|---|
| 1 | Home with redesigned progress hierarchy | **Your last 7 days, at a glance** | Demonstrates the differentiating benefit. Total visibly says all activities. Capture after U1/U2. |
| 2 | Recording a run, readable state and Pause | **Distance, pace and time while you move** | Establishes the core job. Capture actual running mode after U3/U10; no simulated GPS accuracy claim. |
| 3 | Saved activity with useful statistics | **Review the activity you recorded** | Show moving versus elapsed labels and activity type. Remove Map tab. |
| 4 | Splits or lap view with several readable rows | **Review your activity in splits** | Captured capability must match the selected build; use laps if automatic split controls differ. |
| 5 | Android launcher with the goal widget | **Keep your distance goal in view** | Use a neutral test launcher, no unrelated personal notifications. Show this is a widget, not a screen in the app. |
| 6 | Share/export flow showing real choices | **Keep a file. Share an image.** | Show GPX/KMZ or image options without implying sync or automatic backup. |

Capture the widget from the build; the supplied set has none. A chart can replace slot 4 if more readable. Keep six images.

**Production recipe for one maintainer:** one release candidate, one demo database, one emulator size, six captures, one reusable caption frame. Flatten marketing transparency onto an opaque background before export. Keep the screenshots readable at thumbnail size; do not retain the current three small decorative chips beneath every device render. No video or paid photography is necessary.

Play requires at least two screenshots with specified formats/bounds. This plan predicts no ranking or conversion gain. [Play asset requirements, accessed 2026-11-24]

---

## Page 13 of 15 — store copy and website rewrite

### Paste-ready store copy

**Title — 25 / 30 characters**

> Waykeep: GPS Activity Log

**Short description — 76 / 80 characters**

> Record runs, rides and walks. See weekly progress. Keep your activity files.

**Full description — 1,794 / 4,000 characters**

```text
Record outdoor activity with your Android phone and see how your last seven days add up.

Waykeep is a GPS activity tracker for running, cycling, walking and hiking. Record distance and time, review your activity, and keep a log on your phone without creating an account.

SEE YOUR ACTIVITY OVER TIME
• View distance from the last seven days and a four-week weekly average.
• Set an optional distance goal. All activity types count toward the rolling seven-day total.
• See your weekly streak: consecutive weeks with at least one activity.
• Add home-screen widgets for weekly and monthly totals, or goal progress and streak.

RECORD AND REVIEW
• Record with your phone's GPS.
• Use laps, voice announcements and idle auto-pause.
• Review speed and elevation charts.
• Use stationary suppression to reduce recorded movement while stopped.

KEEP AND SHARE YOUR RECORDS
• Import and export GPX and KMZ files.
• Export to Health Connect on supported devices.
• Create square or portrait activity images, including a static map.

LOCAL STORAGE, NO ACCOUNT
Activities are stored on your phone. Waykeep has no ads, subscriptions, in-app analytics or telemetry, or cloud sync. Export copies if you want to keep your records elsewhere; there is no automatic cloud recovery.

GPS recording does not require a mobile data connection. Network access is used for map tiles and GNSS assistance. GPS reception and Android battery restrictions can affect recording.

Maps open in a separate compatible app after the separate map-retirement task ships. Waykeep does not include turn-by-turn navigation. Map images used for sharing may need a connection.

Waykeep is free and open source, based on OpenTracks. It supports Android 8.0 and newer. It is an activity log, not a coaching service or a social network.
```

**What’s new — draft for the release that actually ships these changes:** “Clearer recording buttons, seven-day goal labels and activity-period headings.” Do not publish this line before U2, U3 and U8 ship. No version number is invented.

Title/description limits: [Play listing setup](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), accessed 2026-11-24. Store copy is based on the supplied fact base, not inferred repository extras.

### Website: change the order and remove conflicting claims

The current Home headline and privacy-first sections spend prime space on ownership; the route-first presentation does not make the seven-day benefit obvious. The current guides and running page also describe interactive maps excluded from the target scope. Rewrite them together rather than leaving a contradictory FAQ. [W4]

**Hero headline:** “Record your activity. See your week.”

**Hero paragraph:** “Track runs, rides, walks and hikes with your Android phone. Review distance, time and pace, then see your last seven days and distance goal in one place.”

**Supporting line:** “No account. No ads. Your activity log stays on your phone.”

**Primary CTA:** “Get Waykeep” linking only to an available install destination. Change it to a Play CTA only when the listing is live. Before then, a functioning APK link must say **Download APK**. Do not show a dead store badge.

**First three content blocks:** (1) seven-day total and widget; (2) recording and activity review; (3) export and local storage. Put source/build verification under **About this project** rather than making cryptographic verification the first task for ordinary runners.

**Replacement network FAQ:** “GPS recording works without mobile data. Waykeep uses network access for map tiles and GNSS assistance. Map providers receive the requests needed to deliver tiles. Exporting or sharing a record gives a copy to the destination you choose.”

**Remove from target marketing after the separate map-retirement task ships:** suggested-loop generation, offline-map coverage, in-app Map tabs, daily streaks and AI analysis. Keep the OpenTracks attribution. Update the release landing page, guides and privacy explanation together; historical changelog entries can remain historical with their version context.

---

## Page 14 of 15 — measurement without in-app analytics or telemetry

### What Play Console can and cannot tell you

These platform capabilities apply after distribution and sufficient data. No current Waykeep results or in-app analytics or telemetry SDK are implied.

| Source | Useful information | Limit / decision it can support |
|---|---|---|
| Store listing reports | Visitors, acquisitions and listing conversion | Indicates store-level response; cannot tell whether a runner completed an activity. Compare like periods and channels, not isolated percentage changes. |
| Statistics | Installs, uninstalls, installed audience and available active/returning-user or retention metrics | Definitions and thresholds matter. An app-open retention metric is not workout retention or weekly habit formation. |
| Android vitals | Crashes, ANRs and available performance/battery indicators by app/device context | Partial coverage: eligible Play installs, supported devices and users who share diagnostics. Missing data is not evidence of no bugs. |
| Ratings/reviews | Voluntary descriptions of problems and perceived value | Self-selected comments, not a representative satisfaction sample. Never ask for a positive rating as a condition of support. |
| Pre-launch/device reports | Available compatibility/accessibility findings | Automated checks cannot reproduce an outdoor activity, GNSS reliability or one-handed use. Verify relevant findings manually. |

Sources, accessed 2026-11-24: [Play statistics](https://support.google.com/googleplay/android-developer/answer/139628?hl=en-in), [Android vitals coverage](https://support.google.com/googleplay/android-developer/answer/9844486?hl=en), [pre-launch reports](https://support.google.com/googleplay/android-developer/answer/9842757?hl=en).

“No in-app analytics or telemetry” does not mean the Play ecosystem collects nothing. Explain that distinction if users ask. Do not add Firebase, crash-upload SDKs, screen events or hidden identifiers to fill the gaps.

### Manual evidence that is worth the maintainer’s time

Keep private notes per consenting tester: version, phone/OS, task, observation, reproduction and disposition. Ask only relevant technical details; avoid route/address data and public GPX requests.

For a recording report, ask: “Did recording continue when the screen was off? What did you see when you opened the app again?” For progress comprehension, show Home and ask: “What period and activity types does this distance include?” For battery concerns, ask the tester to state start/end battery, duration, screen use and phone model. Those are case observations, not comparable battery benchmarks without controlled conditions.

Repeat the same unprompted task with willing testers. Report counts only if observed, e.g. **2 of 4 misread the period**, never market-wide uplift.

### Three alternatives for a one-question feedback link

Use exactly one question at a time, changed manually between releases. Put **Send feedback** in About/Help, not a modal after every activity.

1. **“What were you trying to do when Waykeep got in your way?”** Free-text reply; best during recording/UI cleanup.
2. **“What made you choose Waykeep over the tracker you used before?”** Free-text reply; tests whether weekly clarity or privacy is the real install reason.
3. **“What would make you stop using Waykeep?”** Free-text reply; surfaces missing capabilities without proposing them first.

Implementation with zero backend: open the user’s mail app with the one question in the body via `ACTION_SENDTO` and `mailto:`. The app makes no telemetry request; the user chooses whether to send. Explain that email includes their address. If no mail app is installed, let them copy the question and support address. A GitHub issue link can remain an alternative, with a warning that posts are public and require the external service’s account—not a Waykeep account.

Review feedback for roughly 20 minutes weekly. Recording/data-loss faults outrank polish; consolidate repeated wording issues.

---

## Page 15 of 15 — ranked roadmap and deliberate subtraction

**Estimated impact/effort ranking**; no numeric uplift claims. Use page 6 effort bands. All ten are Java/XML, **no backend or hired designer**. Each estimate covers a bounded implementation plus the checks named here; translation and full device-matrix QA are scoped separately.

| Rank | Change | Impact / effort judgement | Deliverable and acceptance |
|---|---|---|---|
| **1 · Bounded** | **U3: label Start activity and Pause** | High / S | Existing click handlers unchanged; preserve current FAB placement and Material sizing with at least a 48dp target; visible action and TalkBack names match; no obscured bottom row. |
| **2 · Bounded** | **U2: name the goal period/scope and weekly streak** | High / S | Home and widget say rolling seven days/all activities; streak is weeks; goal edits still use existing summary. No duplicated calculations. Correct stale help text and only affected screenshots. |
| **3 · Bounded** | **U8: show the selected totals period** | High / S | Visible period/date range changes with existing filter; heading remains readable at 200%. |
| 4 | U5: moving/elapsed labels and All statistics row | Medium/high / S | Labels match the actual stored measures; expanded detail stays reachable. |
| 5 | U1: promote the seven-day summary (**proposed, not approved**) | High / M | Prototype the week-first arrangement and compare it with the route-first Home before replacing it. |
| 6 | U4: compact activity header | High / M | Splits/chart gain usable height without changing map tabs or navigation. |
| 7 | U10: running-specific recording hierarchy/state | High / M | Running shows pace; cycling shows speed; state comes from service; old readings are not labeled live after GPS loss. |
| 8 | U6: reflow split rows | High / M | At least several rows visible at normal text; all values reachable at 200%; final partial interval identified. Depends on U4. |
| 9 | U7: separate chart controls and add text summary | Medium/high / M | Axis and metric choices are distinct; plot has units; TalkBack can read a useful summary without exploring every point. |
| 10 | U9: sport-specific activity records | Medium/high / M | A ride cannot become the unqualified fastest running record; no activity is deleted or retagged to achieve this. |

**Next:** U11 share editor; remaining U12 typography/theme sweep. Apply existing tokens and 200% behavior to each touched screen now. Logo/store captures follow a stable candidate. Recording faults override this ranking.

### Deferred feature retirement: map

Map retirement remains the confirmed target, but it is **not bundled** with U4 or any of the three bounded UI changes. Scope it as its own task and record separate acceptance evidence.

- Remove in-app map entry points and the interactive map tab only after checking the osmdroid dependency, route-following handoff, static share-card tiles, offline-cache behavior, attribution and help links.
- Replace in-app map actions with an external **Open in map app** handoff where useful, with a readable message when no compatible app is installed.
- Update Help, the website, store assets and screenshots together so they do not promise removed navigation.
- Checks: no dead menu items; share-card static maps still render; external handoff works; GPX-follow and import/export paths remain intact; Help has no contradictory map claims; regression-test the saved-activity tabs.

### What should be removed from the product?

- **Remove unsupported marketing claims and misleading entry points from release assets.** Map retirement belongs to the separate task above; do not fold it into these UI changes.
- **Remove daily-streak language and pressure:** preserve the weekly-streak algorithm. No countdowns, lost-streak warnings or “make up” demands.
- **Remove mixed-sport fastest comparisons:** replace them with sport-specific records; preserve source activities and detailed measurements.
- **Remove oversized repeated route artwork from analytic views:** keep a compact summary/thumbnail. The route does not need half the screen in every tab.
- **Remove visual redundancy:** secondary decorative chips in marketing renders, duplicated units/percentages in compact layouts, and any additional font families beyond Inter, Barlow Condensed and JetBrains Mono.

Keep activity types, both widgets, export, announcements and detailed data. Focus the default; preserve all historical records.

### Separate list: excluded work

| Request or tempting extension | Dependency | Decision |
|---|---|---|
| Accounts, cloud sync, live location sharing, social graph | Backend, security operations and ongoing support | **Out of scope; prohibited by brief** |
| Segments, leaderboards or public activity feed | Backend/data operations and moderation | **Out of scope; prohibited by brief** |
| Analytics funnels or automated user cohorts | Telemetry pipeline | **Out of scope; prohibited by brief** |
| Route discovery, full offline navigation, trail catalogue | Mapping/data maintenance; potential service costs | **Out of scope**, even if some components could run locally |
| Coached plans, editorial audio and personalised advice | Content production, domain review and ongoing maintenance | **Out of scope** for one maintainer |
| Paid illustration, campaign photography or a new font family | Designer/vendor budget | **Out of scope**; existing bundled faces, vectors and real captures suffice |

**Release evidence required:** correct scope on website/store assets; no clipping at 200%; usable TalkBack order; short outdoor screen-off recording check; import/export round trip; tile-failure share fallback. Record pass/fail with build and device. Do not turn a passed smoke test into a claim of universal reliability.
