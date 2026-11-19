# ADR home-1 — Each member arranges their own Home, synced as a private record

- Date: 2026-11-19
- Status: accepted
- Task: T-mobile-app (Phase 5 step 5, settings)

## Context
The owner wants each person to choose the cards on Home, their order, their size and what each
card shows, and asked for the choice to be the same on every device they use. Display preferences
so far (digit style, Hijri dates, dollar figures) live per browser in `localStorage`, so they do
not follow a person to the phone. Since ADR e2e-1 the server stores every synced record in
`vault_record` as per-field ciphertext and only needs a `SyncTable` bean naming the fields, so a
new record type needs no database migration. Considered: per-device storage (simpler, but the
owner chose the same layout everywhere) and a generic key/value preference table (broader than
this need, and every key would need its own merge rules).

## Decision
- A new synced record type `home_layout`: one row per member, `visibility` PRIVATE, one sealed
  field `widgets` holding the ordered list of cards. Private rows are sealed with the user key and
  row-level security keeps them from other members, so neither the household nor the server can
  read it.
- The server registers it as `HomeLayoutSyncTable` (fields `visibility`, `widgets`, `deletedAt`).
- The card catalogue lives in core (`packages/core/src/db/homeLayout.ts`): kind, whether a new
  account shows it, and each option with its allowed values. A card is `{ kind, visible, size
  ("full" | "compact"), options }`; each kind appears once. `normaliseLayout` drops kinds and
  values the running app does not know and adds new kinds hidden at the end, so an older device
  never breaks a layout a newer one wrote.
- The whole list is one field, so the last edit wins as a whole (field-level merge, ADR sync-1).
  Two devices editing the layout at the same moment is rare and harmless.
- Both apps draw Home from the layout and edit it in Settings ("Home screen"); the phone also
  links to it from the bottom of Home.

## Consequences
- Adding a card means a catalogue entry in core, a renderer in each app, and strings in both
  languages; old devices simply do not show it.
- Tours skip steps whose card is hidden.
- Other per-person preferences can follow the same pattern later if they should sync.
