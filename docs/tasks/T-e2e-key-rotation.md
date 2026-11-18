# T-e2e-key-rotation — Rotate the household key when a member leaves
- Status: proposed (2026-11-18)
- Context: ADR e2e-1.
- Notes: A removed member still holds household key version N. Create version N+1, grant it to
  remaining members, re-encrypt shared rows under it, and refuse writes under N.
