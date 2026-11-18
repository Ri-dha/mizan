# Status — T-e2e-key-rotation

- Merged: MizanApi 58774e0, MizanApp 99d7106, on develop and main.
- Validated: API 105 tests (KeyRotationIntegrationTest 10: every trigger, refusals, the race,
  stale push, newcomer with both versions, deleted account; reset clears state). Core 182 tests
  (rotation, lost race, not holding current, every version shared, push recovery), web 40, type
  checks for core, web and mobile.
- End to end in the browser (four accounts, throwaway database): after C's removal A's next sync
  rotated both keys to v2 for A and B and deleted C's grants; "Before removal" is sealed h.1,
  "After removal" h.2; the v1 key C held opens the first and fails on the second; B reads both;
  D, who joined afterwards, received v1 and v2 and reads both.
- Seen, unrelated: one 500 from the known price-refresh race on an empty database.
