# T-split-repos — Move the server and the clients into two repositories
- Status: done (2026-11-18), both repositories pushed and merged to main
- Context: owner request 2026-11-18; ADR split-1. Before T-mobile-app Phase 2.
- Notes:
  - `Ri-dha/mizan` (local `myWallet/MizanApi`): branch `chore/split-backend` from develop removes
    the client paths; tag `monorepo-final` marks the last combined state.
  - `Ri-dha/MizanApp` (local `myWallet/MizanApp`): history filtered to the client paths; branches
    `main`, `develop`, `feat/mobile-crypto-spike`, and `chore/split-clients` with the clean-up.
  - The old `myWallet/Mizan` folder is left untouched as a fallback; delete it once both repos are
    pushed and checked.
