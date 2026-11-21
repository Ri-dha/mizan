# ADR currency-1 — Any main currency, and changing it by re-expressing every figure

- Date: 2026-11-21
- Status: accepted
- Task: T-any-base-currency

## Context
Mizan is built for Iraq but should work for anyone. A household's base currency was fixed to the
dinar, and only dinars and dollars could be recorded. Records are sealed on devices (ADR e2e-1),
so the server cannot convert them, and each record keeps its own rate to the base (ADR rates-1).

## Decision
- At sign-up a person picks a country and a main currency (the country's own by default) from
  `GET /api/v1/market/currencies`, open before sign-in: every currency with a rate (ADR rates-2)
  plus the dollar and the dinar, and each ISO country with its currency. Israel and the shekel
  are not offered (product decision); Palestine is offered with the Jordanian dinar. Older apps
  that send neither get Iraq and the dinar.
- The country is the person's (`app_user.country`) and decides the Iraq-only screens (Baghdad
  dollar, Central Bank rate, local gold). Followed currencies are the person's too
  (`tracked_currencies`), set with `PATCH /api/v1/me`.
- The owner changes the household currency with `PUT /households/current/base-currency`, sending
  the factor their device used; the server refuses one more than twice off its own estimate,
  increments `base_generation` and records the step in `household_rebase`.
- Changing re-expresses every base figure at that factor: rates on records (a record already in
  the new currency gets exactly 1), base amounts, bucket amounts, paid bills, sold lots, gold
  prices entered by hand, snapshots and reports. The owner's device converts the shared records
  and its owner's private ones. Every other member's private records are converted by exactly one
  of their devices, which wins `POST /households/current/rebase-claims` (an atomic compare on
  `membership.rebased_generation`) and multiplies every factor since its last claim.
- A device converts in one local transaction, with a marker written first and cleared inside it,
  so a crash leaves all or nothing and the next start finishes it.
- Cash and assets in another currency count at today's rate; records with a rate keep theirs.

## Consequences
History keeps its shape in the new currency, and changing back does not return the exact numbers.
Changes another device pushes in the old currency between its last pull and the change are not
converted; the owner's device syncs first to keep that window small. The phone app's screens
still offer only dinars and dollars until they are updated.
