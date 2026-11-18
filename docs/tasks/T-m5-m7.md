# T-m5-m7 — Deliver market data, the metals ledger, net worth and portability
- Status: done (2026-09-13, commit 76d11da)
- Context: FR-MTL-*, FR-MKT-*, FR-NET-*, FR-DAT-*; vectors metal-valuation, lot-disposal, net-worth.
- Notes: Month close writes an immutable snapshot (DB trigger) with every price used. JSON export and validated import. Account deletion with a 30-day grace.
