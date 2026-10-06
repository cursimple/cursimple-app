# Today's overview

The overview appears above courses only in today's day view. It shows completed count, current or next class, progress and countdowns. Tapping opens a full-day timeline with conflicts and course details. The display preference defaults on and does not alter week view.

## Resolution rules

- Count untimed courses, but count upcoming classes only with known start times.
- Keep courses with missing or invalid timing and prompt for configuration rather than displaying fabricated countdowns.
- Round remaining seconds upward; change state exactly at start and end.
- Apply week coverage, holidays, cancellations and moves before deriving occurrences. Cross-week moves retain the source week's location.
- Detect timed overlaps by actual intervals; adjacent boundaries do not conflict. Untimed courses use period intersections.
- Exclude hidden and reminder-only placeholders. Missing term dates require an explicit week-configuration hint.
- Respect teacher and location preferences. Refresh visible overview data every 30 seconds and stop in the background.

## Shared model and validation

`ScheduledCourseOccurrence` in `core-kernel` serves both overview and class-notice planning. Tests cover boundaries, invalid timing, parity, source-week rooms, overrides and conflicts.

`TodayOverviewUiTest` requires `todayOverviewQa=true` on a dedicated emulator. Fixed dates and sample courses verify day/week visibility, timeline navigation, countdowns, conflicts and course entry points.
