# ADR tenancy-1 — Row-level security enforces households and private records

- Date: 2026-09-13
- Status: accepted
- Task: T-m0-m1

## Context
Every query must be scoped to a household, and private records (BR-16) must stay invisible to
other members, including through the generic sync endpoints that read tables dynamically.
Application-level filters are easy to forget in one query.

## Decision
Household tables use `FORCE ROW LEVEL SECURITY`. Each request transaction runs `set local role
mizan_app` and sets `app.user_id` and `app.household_id`. Policies admit shared rows of the
household and private rows of their owner. `app.system = on` lets server jobs and household
aggregates see all rows; `app.restricted = on` (later, for dependents) hides shared rows.

## Consequences
A forgotten filter returns nothing rather than someone else's data. Server code that needs
cross-member totals must elevate explicitly. Tests run on real Postgres; H2 cannot exercise this.
