# ADR foundation-2 — Integer minor units and shared test vectors

- Date: 2026-09-13
- Status: accepted
- Task: T-m0-m1

## Context
Allocation splits, debt payoff and metal valuation run on the phone (offline) and on the server
(snapshots, reports). Floats and independent implementations drift by a dinar, which breaks
reconciliation (BRD NFR-06, BR-01, BR-02).

## Decision
All money is BIGINT minor units with a currency. Splits use largest remainder. Rounding is
half-up at fixed points; metal valuation uses BigInteger/BigInt. Every function implemented in
both Java and TypeScript has a JSON case file in `shared/test-vectors/` that both suites run, and
the vector is written before the code.

## Consequences
The client and server agree to the dinar by construction. Adding domain maths costs a vector
file. Display formatting is the only place a minor unit becomes a decimal.
