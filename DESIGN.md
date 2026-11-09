# Design

<!-- impeccable:design-schema 1 -->

## World

"Track at dusk." A sporty, kinetic Android shell built on Material 3: cool bone in daylight, asphalt at night, safety cyan for the lane and the live route, recording red only for the record state. The route shape is the product's strongest visual asset, so the feed is route-first and the week is a race lane, not a stat card.

## Color roles

| Token | Light | Dark | Use |
|---|---|---|---|
| `waykeep_cyan` | `#00A6B8` | `#00E5FF` | Large fills, map routes, lane |
| `waykeep_cyan_dim` | `#007A87` | `#008B99` | Legacy dim cyan |
| `waykeep_accent` | dim cyan | bright cyan | Text and icons (`colorPrimary`) |
| `track_surface` | `#F1F4F1` | `#07090A` | Page ground |
| `track_surface_low` | `#E8EDE9` | `#0D1011` | Home lane band, status strip |
| `track_surface_container` | `#DFE6E1` | `#121617` | Containers |
| `track_surface_high` | `#D5DDD8` | `#181D1E` | Raised surfaces |
| `track_ink` | `#0B0E0D` | `#E8EDEA` | Primary text |
| `track_ink_muted` | `#46524D` | `#A7B3AD` | Secondary text |
| `track_outline` | `#6F7C76` | `#6B7872` | Control outlines |
| `track_outline_variant` | `#C3CCC7` | `#2A3330` | Hairlines, ticks |
| `track_lane` | `#C9D3CD` | `#232B29` | Lane track |
| `icon_badge_surface` | `#E6FFFFFF` | `#E61C1B1F` | Activity badge |
| `semantic_recording_red` | `#FF3B30` | `#FF3B30` | Recording only |

Bright cyan never carries small text on light. Text and icons use the accent role.

## Type

Inter speaks; Barlow Condensed headlines; JetBrains Mono measures. Large display numbers use Barlow Condensed with tabular figures; small data stays in JetBrains Mono; UI text stays in Inter so Cyrillic and Greek locales do not fall back.

| Style | Face | Size | Notes |
|---|---|---|---|
| `ScoreValue` | Barlow Condensed Bold | 48sp | -0.01 tracking, the week distance |
| `RowDistance` | Barlow Condensed Bold | 26sp | -0.01 tracking |
| `PrimaryValue` / `SecondaryValue` | Barlow Condensed Bold | 36sp | Detail and recap numbers |
| `HeroValue` | Barlow Condensed Bold | 56sp | Recording primary value |
| `LaneLabel` | Inter | 11sp | Caps, 0.12 tracking, accent |
| `MetaValue` | JetBrains Mono | 12sp | Averages and streaks |
| `RowDate` | Inter | 11sp | Caps, 0.1 tracking, accent |
| `RowContext` | Inter | 12sp | Two lines maximum |
| `TabText` | Inter | 14sp | Segmented control and tabs |

The display face is used only at large sizes. Research on glanceable reading finds condensed text is slower at small sizes; Inter stays the UI face. Tabular figures are used wherever numbers sit in columns or change in place.

## Shape and surface

- 16dp route tiles, 20dp pill controls, 2dp lane lines, 1dp hairlines.
- Structure comes from tonal surfaces, not drop shadows. The home lane is a full-width band, never a card inside a card.
- The route tile is a dark asphalt gradient with the transparent route bitmap over it.

## Motion

- The week lane fills and the runner marker travels to the current distance in 650 ms with a decelerate curve. It is the one authored moment on the home.
- Segmented tabs switch instantly; no decorative entrance animations.
- System animation scale is respected by `ValueAnimator`.

## Components

- **Home route hero**: the latest real route, rendered at the hero size with the whole shape fitted (`fitCenter`) on asphalt; fixed light ink for distance, date and time.
- **Week strip**: `WeekLaneView` plus the weekly distance and goal numbers, kept visible below the route hero.
- **Home feed**: `track_list_item.xml` is route-first: 88dp fitted route tile, 11sp tracked date, 26sp display distance, 12sp context.
- **Bottom actions**: `BottomAction` shows an icon and a visible label; content descriptions update with GPS state.
- **Detail route hero**: the recorded route above the segmented tabs, with the whole shape fitted and the distance/date/time overlay.
- **Stats strip**: the default Stats view is three metrics — moving pace, moving time, elevation range. The full instrument grid lives behind a “More stats” toggle; the duplicated start date and auto-generated date header are removed.
- **Detail shell**: `MaterialButtonToggleGroup` pills in a horizontal scroll, synced by `DetailTabs`.
- **Chart controls**: `ChartMetricChip` and the axis pills share `tab_toggle_background` / `tab_toggle_text`.
- **Map**: `WrappedCopyrightOverlay` wraps the attribution and keeps it legible.

## Rules

1. One window per label. "Last 7 days" is the rolling seven-day distance.
2. Cyan text uses the accent role; bright cyan is for fills, routes and dark surfaces.
3. Labels wrap to two lines; control rows scroll rather than clip.
4. No gray-on-gray cards. No nested cards.
5. The route and the measurement are the content; chrome recedes.
6. Light and dark are both first-class; test at 1.0 and 1.3 font scale on a real device.
