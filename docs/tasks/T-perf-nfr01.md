# T-perf-nfr01 — Meet the 1.5 s interactive target on a mid-range phone
- Status: proposed (2026-09-13)
- Context: NFR-01; Lighthouse mobile measured 2.9 s time to interactive under slow 4G.
- Notes: Candidates: smaller initial bundle (Recharts and Radix are large), defer Dexie live queries off the home screen, preload the session.
