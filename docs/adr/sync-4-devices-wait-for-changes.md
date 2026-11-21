# ADR sync-4 — Devices wait for changes instead of checking every 5 minutes

- Date: 2026-11-21
- Status: accepted
- Task: T-instant-sync

## Context
Devices pulled on start, on coming back online or to the foreground, after their own writes, and
every 5 minutes. A change made on one device could take minutes to show on another.

## Decision
- `GET /api/v1/sync/wait?since=<seq>` is a long poll. It answers `{changed: true}` at once when the
  household's feed (row-level security limits `sync_log` to it) is past `since`; otherwise it is held
  until a push for the household commits on this instance (`SyncSignal`, woken after commit) or
  `mizan.sync.wait-timeout` (25 s) passes, and answers `{changed: false}`.
- The wait is opened before the check, so a change committed in between still wakes it. A person
  holds at most 4 waits; more end the oldest. Async re-dispatches pass security, since the request
  was authorised when it arrived.
- Long polling rather than server-sent events or WebSockets: it passes Caddy and the Cloudflare
  Worker (which allows 100 s) unchanged and works with React Native's fetch.
- With more than one API instance, a push on another instance is seen when the wait times out and
  the device asks again (at most 25 s late). The timed sync stays as a fallback.

## Consequences
Changes reach other open devices in about a second. Each open app holds one idle request; the
devices stop waiting in the background or offline.
