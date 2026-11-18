# ADR obs-3 — Access lines only for failures and slow requests

- Date: 2026-11-19
- Status: accepted (narrows the access logging of ADR obs-1)
- Task: T-lean-backend

## Context
ADR obs-1 wrote one INFO access line per request. Every open app syncs every five minutes and
after each edit, with about ten requests per sync, so nearly all lines record fast, successful
syncs. They cost CPU to write, disk in Elasticsearch, and make the lines that matter harder to find.

## Decision
- Server errors (5xx) stay at ERROR; client errors (4xx) and requests slower than
  `mizan.logging.requests.slow-after` (1 s) at INFO; every other access line is DEBUG.
- Request metrics use four fixed latency bands (100 ms, 500 ms, 1 s, 5 s) instead of a full
  histogram, and are exported every 60 s instead of 30 s.

## Consequences
- Per-path traffic counts come from the metrics, not from counting log lines.
- To see every request again, set `logging.level.iq.mizan.http=DEBUG`.
