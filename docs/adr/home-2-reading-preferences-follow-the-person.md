# ADR home-2 — Reading preferences follow the person on the Home layout row

- Date: 2026-11-21
- Status: accepted
- Task: T-synced-preferences

## Context
Digit style, Hijri dates, the net worth in US dollars and the months before an asset's value
counts as old were kept per device (web `localStorage`, phone kv-store). A person who set them on
the web had to set them again on the phone. ADR home-1 already gives each member one private row,
sealed with the user key, that follows them across devices. Considered: a new record type for
preferences (more plumbing in both apps and the server for four values) and one JSON field on the
existing row (simpler, but two devices changing different preferences would overwrite each other).

## Decision
- The `home_layout` row gains four optional sealed fields: `digitStyle`, `showUsd`, `showHijri`,
  `valuationReminderMonths`. One field each, so field-level merge (ADR sync-1) keeps a digit change
  on the phone and a Hijri change on the web made at the same time.
- Language, theme, display currency, seen tours and dismissed notices stay per device: a phone and
  a computer often differ there.
- Each app keeps its local copy as the offline and before-unlock cache. `setPreferences` writes the
  four to the row too; a small bridge (`SyncedPreferencesSync`) applies the row's values locally
  when they differ.
- A row without any of the four, on a device that has synced at least once, is seeded with that
  device's own non-default choices, so updating the app never resets what someone had chosen. The
  first device to seed wins; the others take its values.
- Core reads only values it understands (`syncedPreferencesOf`); anything unknown is left to the
  device.

## Consequences
- The server must know the new fields before a client sends them (`SYNC_FIELD_UNKNOWN`
  otherwise), so the server ships first.
- A later preference that should follow the person is one more field here, plus the key in
  `SYNCED_KEYS`.
