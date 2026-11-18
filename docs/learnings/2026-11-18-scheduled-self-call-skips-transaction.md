# 2026-11-18 — Scheduled jobs and @Transactional
A `@Scheduled` method that calls a `@Transactional` method of the same class (`this::purge`)
gets no transaction: Spring's proxy is bypassed. `set_config('app.system', 'on', true)` then
lasts one statement, and the next query runs without the system flag, which row-level security
answers with nothing. Tests that call the transactional method through the bean pass, so the
bug only shows on the schedule. Run the body through a `TransactionTemplate` (as
`RecycleBinPurge` and `AttachmentObjectPurge` now do) or move the schedule to another bean.
Source: T-receipt-retention
