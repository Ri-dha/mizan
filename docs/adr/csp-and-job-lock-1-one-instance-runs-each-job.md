# ADR csp-and-job-lock-1 — One instance runs each scheduled job

- Date: 2026-11-18
- Status: accepted
- Task: T-csp-and-job-lock

## Context
Every API instance runs every `@Scheduled` job: purges, reminder dispatch, metal alerts, the
market poll and the IQWealth poll. With two instances reminders could be sent twice at once and
the IQWealth quota (100 a day, ADR admin-1) would drain twice as fast. Options: ShedLock, a
Postgres advisory lock, or a small lock table. ShedLock's Spring 7 support is not established
here, and an advisory lock ends with the connection, which jobs that call the network would
have to hold open.

## Decision
- A `scheduled_job_lock` table (name, locked_until, locked_at, locked_by) and
  `common/jobs/JobLock.runExclusively(name, atMostFor, atLeastFor, task)`, using the database's
  clock so instance clocks do not matter.
- Acquiring is one upsert that succeeds only when `locked_until` has passed. Releasing sets
  `locked_until` to `locked_at + atLeastFor`, so an instance whose cron fires a few seconds
  later skips the run instead of repeating it. `atMostFor` frees the lock if an instance dies
  mid-run.
- Every scheduled job and the IQWealth start-up poll go through it. Idempotent start-up work
  (bucket creation, the bootstrap operator) does not.
- The IQWealth feed answers from the stored quote when this instance has not polled, so an
  instance that never wins the lock still serves the market dollar.

## Consequences
- Scaling out no longer duplicates jobs or spends the quota twice.
- A skipped run is logged at debug level only; the next schedule tries again.
