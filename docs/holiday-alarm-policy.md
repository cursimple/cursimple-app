# Holiday alarm policy

Holiday skipping defaults on. Existing explicit preferences remain unchanged. Evaluate dates in the app's selected time zone.

Policy precedence:

1. Manually muted dates always suppress ringing and vibration.
2. An alarm's explicit `allowOnHoliday` bypasses automatic holiday skipping, never manual date muting.
3. Explicit make-up class and workday declarations follow resolved schedule policy.
4. Remaining holidays follow the global skip preference.

Filter during planning and recheck immediately before audio, vibration and ringing notifications. Repeated rings recheck each round, including after midnight. Skipping an occurrence preserves its recurring rule for the next class day.

Persist `allowOnHoliday` through records, Intents, snooze and rebuilding. Missing legacy values mean no holiday exception. Skipped alarms do not produce pre-alarm notices; the alarm list exposes skip and date-mute status.

From 20:00 on the preceding evening, WorkManager or foreground entry can issue a silent, date-deduplicated explanation. Background timing may vary; alarm policy does not depend on that notice arriving. Runtime skips still record diagnostics.

This policy governs host alarms and class notices. Component-owned service settings remain independent.
