# ADR households-1 — Several households per user; the current membership drives the token

- Date: 2026-09-13
- Status: accepted
- Task: T-v2

## Context
A person joins a partner's household but keeps their own; advisors get temporary access.

## Decision
`membership` rows carry `is_current` and an optional `expires_at`. The access token's household
and permissions come from the current active membership; joining or switching takes effect on the
next refresh. Dependents lack `HOUSEHOLD_VIEW` and are bound restricted; advisors hold
`REPORTS_VIEW` only and expire (30 days by default).

## Consequences
Switching households is a token refresh, not a new account. Every role is a permission set on
`HouseholdRole`; code never checks role names.
