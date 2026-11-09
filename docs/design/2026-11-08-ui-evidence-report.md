# Waykeep UI Evidence Report — 2026-11-08

**Status:** evidence-based design audit. This version supersedes the earlier opinion-led draft.
**Build:** `com.waykeep.app.debug`, `v1.0.0`, versionCode `6890`.
**Device:** POCO `25028PC03I`, Android 15, 720×1640 px at 320 dpi (360 dp wide), font scale 1.0 and 1.3, light and dark themes.

**Evidence rule used here**
1. **Observed evidence** is a measured value, database fact, screenshot, UIAutomator bound, or source-code path.
2. **External evidence** is a WCAG success criterion, platform guideline, peer-reviewed paper, field study, or named professional guideline.
3. **Recommendation** is labeled as a derived interpretation. Where no external evidence supports a claim, it is moved to *Open questions* instead of being presented as a finding.

Screenshots are local evidence in `/tmp/opencode/`; they are not committed because they contain the user's real location and activity history.

**Implementation status (2026-11-08, `feature/ui-evidence-fixes`)**

| Finding | Status | Change |
|---|---|---|
| F1 | fixed | Goal progress now uses the same rolling 7-day distance as the hero; `WeeklyStreak` removed. |
| F2 | fixed | `waykeep_accent` role: `#007A87` on light, bright cyan on dark; header text and tab indicator use it. |
| F3 | fixed | Header and row-context labels wrap to two lines; row hairline follows the final height. |
| F4 | fixed | `icon_badge_surface` has a dark night value, so the themed glyph stays visible. |
| F5 | fixed | Tile attribution is drawn with a wrapped `StaticLayout` and a scrim. |
| F6 | fixed (labels) | Both icon buttons now have content descriptions and tooltips; visible text labels remain an open design question. |
| F7 | fixed | Pace/speed use a 5th–95th percentile axis; clipped outliers get edge triangles; the x-axis unit is muted and smaller than the ticks. |
| F8 | fixed | Auto-generated titles use `DateUtils.formatDateTime` instead of the ISO timestamp. |
| F9 | partial | The repeated interval gain/loss column is hidden when no interval has data; elevation min/max is still not shown on Stats, and the recording screen still shows its default Gain/Loss cells as dashes. |
| F10 | fixed | RecyclerView bottom padding is computed from the bottom bar plus half the FAB height. |

## Typography decision — 2026-11-09

**Decision:** Inter for UI text, Barlow Condensed Bold for large display numbers, JetBrains Mono for small tabular data.

**Evidence**

- Sawyer et al. (2017) and NN/g: condensed text required 11.2% more time in glanceable reading, and the penalty is worst at small sizes [16][17]. This rules out setting UI labels or body text in Barlow Condensed.
- *Journal of Vision* (2020) and *Ergonomics* (2021): wider letter shapes improve letter recognition at small visual angles [18][19]. The UI face stays regular-width.
- Butterick, Google Fonts Knowledge and Fonts.com: tabular lining figures are the right choice for columns, tables and values that change in place [20][21][22]. Barlow Condensed carries `tnum`; JetBrains Mono is tabular by construction.
- Butterick, *Mixing fonts*: most documents tolerate a second font, few a third [20]. The app uses three roles, not three competing voices.
- Monotype's sports-typography case study: numbers must stay distinct and legible at speed, and condensed cuts are used for long names and tight space [23]. That supports a display face for large numerals, not for small text.
- Typeface-personality research shows fonts influence perceived brand personality [24][25], but it does not establish that Barlow is objectively more "sporty" than Archivo or Saira. That part is a brand decision.
- Localization: Inter covers Cyrillic and Greek; Barlow, Archivo and Saira do not. Waykeep ships 57 locales, including Russian, Ukrainian, Bulgarian and Greek. The display face is therefore limited to digits; UI text stays Inter.

**Not supported by the evidence:** that a condensed face improves legibility, or that one display face is objectively more sporty than another.


---

## 1. Today's run check

**Track 45 — 2026-11-08, 07:47 +0530**

| Observed metric | Value |
|---|---|
| Distance | **3.239 km** |
| Total time | **26:58** |
| Moving time | **26:29** |
| Track points | **295** |
| Types | 289 normal, 3 idle, 1 manual segment start, 1 automatic segment start, 1 manual segment end |
| Horizontal accuracy | avg **±1.58 m**, max **±27.43 m** |
| Speed | avg **2.32 m/s** (8.35 km/h), max **7.01 m/s** (25.2 km/h) |
| Elevation samples | 214.27–251.00 m |
| Vertical accuracy | **none** |
| Elevation gain/loss | **none** (`UNKNOWN`) |
| HR / cadence / power | **none recorded** |
| Gaps | 3 >10 s, 0 >30 s |

**Observed conclusion:** the GPS trace is healthy. Gain/loss is empty because every point lacks vertical accuracy and the GPS elevation fallback is opt-in; no barometer value is present. This is a data-display gap, not a corrupted track.

**1 km splits:** 5:42, 10:03, 10:31, then 0.24 km at 3:00 min/km.

Evidence: `/tmp/opencode/waykeep-ui-report-db.db`, `/tmp/opencode/ui-03-today-stats.png`, `/tmp/opencode/ui-04-intervals.png`.

---

## 2. Findings

### F1 — P0: two different windows are both labeled “This week”

**Observed evidence**
- Home hero: **“This week 13.8 km.”**
- Same card: **“3.2 km of 20.0 km this week.”**
- Code: the first value is `RollingLoad.sevenDayDistance()` (rolling 7 days); the goal value is `WeeklyStreak.currentWeekDistance()` (calendar week using `WeekFields.of(Locale.getDefault())`).
- On this `en_IN` device the calendar week starts Sunday, so the calendar-week distance is only today's 3.24 km.

**External evidence**
- Locke and Latham's goal-setting research found that specific goals plus **feedback on progress in relation to the goal** are jointly necessary for goal effects; feedback that is not aligned with the goal measure is not usable feedback [12].
- Dai, Milkman, and Riis found that the **start of a new week** is a salient temporal landmark that changes goal pursuit [11]. “This week” therefore has a strong expected calendar meaning, not an arbitrary rolling window.

**Recommendation (derived):** label the top value **“Last 7 days”** and the goal **“This week (Sun–Sat)”**, or use one window for both. Do not put two different windows under one label.

**Severity rationale:** P0 because the two primary numbers in the hero contradict each other; this is a correctness/trust defect, not a style preference.

---

### F2 — P1: brand cyan fails WCAG text contrast on light surfaces

**Observed evidence**
- `waykeep_cyan` = `#00A6B8`.
- Measured contrast: **2.94:1** on white, **2.74:1** on the light surface.
- Used for active tab text, stat labels, and interval headers.

**External evidence**
- WCAG 2.2 SC 1.4.3 requires **4.5:1** for normal text and **3:1** for large text [1].
- Android's accessibility guidance repeats the same 4.5:1/3:1 thresholds [4].

**Recommendation (derived):** use `waykeep_cyan_dim` `#007A87` for text/icons on light (measured **5.08:1** on white, **4.74:1** on surface), and keep bright cyan for dark surfaces/fills/routes. This is a token-role fix, not a brand change.

---

### F3 — P1: text is lost at 130% font scale

**Observed evidence**
- Stats at font scale 1.3: **“AVG. MOVING PA…”** (label clipped).
- Feed at 1.3: **“Today 07:47 · street runni…”** and **“Thursday 07:11 · street r…”**.

**External evidence**
- WCAG 2.2 SC 1.4.4 requires text to resize up to **200%** without loss of content or functionality [2].
- Android accessibility guidance requires layouts to remain usable when users enlarge text [4].

**Recommendation (derived):** allow stat labels to wrap to two lines or shorten the label; allow the feed context line to wrap. Failing at 130% is already a failure of the 200% requirement.

---

### F4 — P1: dark-mode activity icons are white on white

**Observed evidence:** in dark mode the feed activity icon is a white running figure inside a white circle; measured contrast is approximately **1:1**.

**External evidence**
- WCAG 2.2 SC 1.4.11 requires **3:1** non-text contrast for meaningful graphical objects [3].
- Android's dark-theme guidance explicitly warns against hardcoded colors and static-color drawables; use theme attributes and test both themes [6].

**Recommendation (derived):** tint the circle from `colorSurface`/`colorSurfaceVariant` and the glyph from `colorOnSurface` or the accent token; test light and dark variants.

---

### F5 — P1: map attribution is clipped

**Observed evidence:** the map shows a single line beginning “Esri, HERE, Garmin, (c) OpenStreetMap contributors, and the GIS us…” cut off at the right edge.

**External evidence**
- The OpenStreetMap Foundation attribution guidelines require attribution to be **legible and understandable**, visible without interaction, and not hidden [8].
- The OSM tile usage policy says not to hide attribution behind UI or off-screen [8].

**Recommendation (derived):** wrap the attribution to two lines, make it horizontally scrollable, or use a compact attribution control that opens a details sheet.

---

### F6 — P1: two icon-only bottom-bar actions have no accessible label

**Observed evidence:** UIAutomator reports `NAF="true"` and empty `content-desc` for `aggregated_stats_button` and `sensor_start_button`; “Suggested routes” and “Record” have labels.

**External evidence**
- Android accessibility guidance requires each interactive UI element to have a purpose description via `contentDescription` [4].
- NN/g icon research found that most icons are ambiguous without a visible text label; labels reduce interpretation errors [7].

**Recommendation (derived):** add content descriptions/tooltips and visible labels where space allows; alternatively reduce the bottom bar to three labeled actions.

---

### F7 — P1: the pace chart scale hides the useful band, and the right-edge label collides

**Observed evidence**
- Pace y-axis runs **0–50 min/km**; most of the run sits around 8–15 min/km.
- The right edge shows the axis title **“Time”** where the final tick/label should be, overlapping the plot.

**External evidence**
- Cleveland and McGill's graphical-perception experiments rank **position along a common scale** as the most accurate elementary task; graphs should use tasks high in that hierarchy and make the data variation perceptible [9].
- Tufte's principle of graphical integrity requires clear, detailed labeling and representations proportional to the data; labels must not be ambiguous or collide [10].

**Recommendation (derived):** choose a scale that makes the 8–15 band legible (clamp, robust/percentile scale, or annotate outliers), and draw the x-axis title separately from the final tick. This is a scale/labeling fix, not a demand to hide outliers.

---

### F8 — P2: the track title is a machine timestamp

**Observed evidence:** the detail header reads **“2026-11-08T07:47+0530”**, followed by “8 Nov 2026, 7:47:26 am.”

**External evidence**
- Google's developer style guide recommends spelled-out month/day/year for human-facing dates, with ISO 8601 reserved for numeric-only contexts [14].
- NN/g date guidance emphasizes unambiguous, task-appropriate date presentation [15].

**Recommendation (derived):** default to a human name such as **“Morning run · 8 Nov”**, keep the ISO value as editable metadata, and reuse the human name in shares/exports.

**Severity rationale:** P2 because the timestamp is valid data, not wrong data; the cost is readability and duplication.

---

### F9 — P2: missing elevation is repeated as eight dashes instead of one explanation

**Observed evidence:** the Intervals table repeats `▲ - m` / `▼ - m` on every row; the Stats screen omits elevation even though altitude samples exist.

**External evidence**
- Tufte's data-ink principle says to erase redundant non-data ink and let the data show through [10].
- Goal-setting research requires feedback that is specific and actionable; a repeated placeholder is neither [12].

**Recommendation (derived):** show elevation min/max when altitude exists; if gain/loss is unavailable, show one neutral explanation (“No barometer data; GPS estimate is off”) and hide the Gain & Loss column. Make the GPS-estimate setting reachable from that message.

---

### F10 — P2: at 130% the FAB covers feed content

**Observed evidence:** at 1.3 font scale the red record FAB covers the “31 Oct” row.

**External evidence:** WCAG 1.4.4 requires no loss of content/functionality when text is resized [2]; an overlapping control blocks both reading and tapping.

**Recommendation (derived):** add bottom content padding equal to FAB height + margin, or collapse the FAB during scroll.

---

## 3. What the evidence does **not** support

These are **open questions**, not findings. They require user testing or a product decision, not a code change justified by the sources above.

- **Hero density:** UbiFit Garden's field trial supports a glanceable weekly goal display with recent goal attainment [13]. It does **not** establish that five numeric metrics are harmful. The hero's problem is the contradictory “This week” definitions (F1), not the number of metrics.
- **Chart chip hierarchy:** no source in this review establishes that the current Time/Distance/Elevation/Pace control grouping is worse than an alternative. This needs a usability test.
- **FAB size:** measured 128 px ≈ 64 dp, above the 48 dp Android minimum [5]; size itself is not a finding.
- **Neutral copy/tone:** “Insight Without Shame” is a product value, not a measured usability finding; it needs user research.
- **Color aesthetics:** only contrast is evidence-based here; hue preference is not.
- **Map style contrast:** the route has a black casing; no measured failure was established.

---

## 4. Design decisions that are now evidence-backed

1. **One week definition per label.** Either “Last 7 days” for the rolling value and “This week” for the calendar goal, or one window for both. Supported by goal-setting feedback alignment [12] and the salience of weekly landmarks [11].
2. **Two-cyan token system.** `#007A87` for text/icons on light, bright cyan for dark/fill/route. Supported by WCAG 1.4.3 [1] and Android accessibility [4].
3. **200% text-resize contract.** Wrap or shorten labels; test 130% and 200% at 360 dp. Supported by WCAG 1.4.4 [2].
4. **Theme-driven icon colors.** No static white icons; use theme attributes and test dark mode. Supported by WCAG 1.4.11 [3] and Android dark-theme guidance [6].
5. **Legible attribution.** Wrap/scroll or provide a details control. Supported by OSMF attribution requirements [8].
6. **Labeled icon actions.** Add content descriptions and visible labels. Supported by Android accessibility [4] and NN/g icon research [7].
7. **Chart scale and labeling.** Make the meaningful band legible; separate the axis title from ticks. Supported by Cleveland & McGill [9] and Tufte [10].
8. **Human-readable dates.** Use spelled-out dates in the primary UI. Supported by Google style guidance [14] and NN/g date guidance [15].
9. **One empty state for missing elevation.** Explain the data gap once; remove repeated dashes. Supported by Tufte's data-ink principle [10] and goal-setting feedback requirements [12].

---

## 5. Evidence index

**Device / database**
- `/tmp/opencode/waykeep-ui-report-db.db` — read-only copy of the device DB.
- Track 45: 3.239 km, 26:58 total, 26:29 moving, 295 points, no vertical accuracy, no gain/loss.

**Screenshots**
- `/tmp/opencode/ui-01-home.png` — home at 1.0, light.
- `/tmp/opencode/ui-03-today-stats.png` — today's Stats.
- `/tmp/opencode/ui-04-intervals.png` — Intervals.
- `/tmp/opencode/ui-05-chart2.png` — Chart.
- `/tmp/opencode/ui-06-map2.png` — Map.
- `/tmp/opencode/ui-07-home-font130.png` — home at 1.3.
- `/tmp/opencode/ui-08-stats-font130.png` — Stats at 1.3 (clipping).
- `/tmp/opencode/ui-09-home-dark.png` — home in dark mode.
- `/tmp/opencode/ui-10-chart-font130.png` — chart empty/loading capture at 1.3 (not counted as a defect; reproduce first).

**Code references**
- `TrackListActivity.renderInsight()` — hero values.
- `InsightSummary.compute()` — rolling load + weekly streak.
- `RollingLoad.compute()` — 7-day/28-day windows.
- `WeeklyStreak.compute()` — locale week start.
- `src/main/res/values/colors.xml` — cyan tokens.
- `track_list.xml`, `statistics_recorded.xml`, `track_list_item.xml` — layouts.
- `ChartView.java` — chart axes/scale.
- `TrackMapFragment.java` — map overlays/attribution.

---

## 6. References

1. W3C. **Web Content Accessibility Guidelines (WCAG) 2.2**, SC 1.4.3 Contrast (Minimum). https://www.w3.org/TR/WCAG22/
2. W3C WAI. **Understanding SC 1.4.4: Resize Text**. https://www.w3.org/WAI/WCAG22/Understanding/resize-text.html
3. W3C WAI. **Understanding SC 1.4.11: Non-text Contrast**. https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast
4. Android Developers. **Make apps more accessible** (contrast, 48 dp targets, content descriptions). https://developer.android.com/guide/topics/ui/accessibility/apps
5. Google Accessibility Help. **Touch target size** (48×48 dp). https://support.google.com/accessibility/android/answer/7101858?hl=en
6. Android Developers. **Dark theme** (theme attributes, avoid hardcoded colors/icons, test both themes). https://developer.android.com/develop/ui/views/theming/darktheme
7. Nielsen Norman Group. **Icon Usability** (icons need text labels; ambiguity). https://www.nngroup.com/articles/icon-usability/
8. OpenStreetMap Foundation. **Licence/Attribution Guidelines** (legible, understandable, visible attribution). https://osmfoundation.org/wiki/Licence/Attribution_Guidelines
9. Cleveland, W. S., & McGill, R. (1984). **Graphical Perception: Theory, Experimentation, and Application to the Development of Graphical Methods.** Journal of the American Statistical Association, 79(387), 531–554. https://faculty.washington.edu/aragon/classes/hcde511/s12/readings/cleveland84.pdf
10. Tufte, E. R. (1983). **The Visual Display of Quantitative Information.** Graphics Press. (Graphical integrity, lie factor, data-ink ratio, clear labeling.)
11. Dai, H., Milkman, K. L., & Riis, J. (2014). **The Fresh Start Effect: Temporal Landmarks Motivate Aspirational Behavior.** Management Science, 60(10), 2563–2582. https://pubsonline.informs.org/doi/10.1287/mnsc.2014.1901
12. Locke, E. A., & Latham, G. P. (2002). **Building a practically useful theory of goal setting and task motivation: A 35-year odyssey.** American Psychologist, 57(9), 705–717. https://doi.org/10.1037/0003-066X.57.9.705
13. Consolvo, S., McDonald, D. W., Toscos, T., Chen, M. Y., Froehlich, J., Harrison, B., Klasnja, P., LaMarca, A., LeGrand, L., Libby, R., Smith, I., & Landay, J. A. (2008). **Activity Sensing in the Wild: A Field Trial of UbiFit Garden.** CHI 2008. https://makeabilitylab.cs.washington.edu/media/publications/Consolvo_ActivitySensingInTheWildAFieldTrialOfUbifitGarden_CHI2008.pdf
14. Google. **Developer documentation style guide: Dates and times.** https://developers.google.com/style/dates-times
15. Nielsen Norman Group. **Date-Input Form Fields: UX Design Guidelines.** https://www.nngroup.com/articles/date-input/
16. Sawyer, B. D., et al. (2017). **The Cost of "Cool": Typographic Style Legibility in Reading.** Proceedings of the Human Factors and Ergonomics Society. https://www.bendsawyer.com/wp-content/uploads/2025/05/Sawyer_et-al_2017_The-Cost-of-Cool-Typographic-Style-Legibility-in-Reading.pdf
17. Nielsen Norman Group. **Typography for Glanceable Reading: Bigger Is Better.** https://www.nngroup.com/articles/glanceable-fonts/
18. **Fonts of wider letter shapes improve legibility.** Journal of Vision (2020). https://jov.arvojournals.org/article.aspx?articleid=2771109
19. **Fonts of wider letter shapes improve letter recognition in parafovea and periphery.** Ergonomics (2021). https://www.tandfonline.com/doi/pdf/10.1080/00140139.2021.1991001
20. Butterick, M. **Alternate figures** and **Mixing fonts.** Practical Typography. https://practicaltypography.com/alternate-figures.html · https://practicaltypography.com/mixing-fonts.html
21. Google Fonts Knowledge. **Understanding numerals.** https://fonts.google.com/knowledge/introducing_type/understanding_numerals
22. Fonts.com. **Proportional vs. Tabular Figures.** https://www.myfonts.com/pages/fontscom-learning-fontology-level-3-numbers-proportional-vs-tabular-figures/
23. Monotype. **The challenging game of designing for sports.** https://enterprise.monotype.com/resources/articles/the-challenging-game-of-designing-for-sports
24. **Using Type Font Characteristics to Communicate Brand Personality of New Brands** (2012). https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2027518
25. **Communicating brand gender through type fonts** (2014). https://doi.org/10.1080/13527266.2014.918050
