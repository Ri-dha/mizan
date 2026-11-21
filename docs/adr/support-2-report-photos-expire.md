# ADR support-2 — Report photos expire after the report is resolved

- Date: 2026-11-21
- Status: accepted
- Task: T-followups-2026-11-21

## Context
ADR support-1 kept report images until the account was deleted. Screenshots often show a person's
numbers, and they are only useful while a problem is being worked on. The person chose six months
after a report is resolved.

## Decision
- A nightly job (`ReportImageExpiry`, JobLock `report-image-expiry`) deletes from storage the images
  of reports that are RESOLVED and whose last message is older than `mizan.support.image-retention`
  (180 days). Only images created before that cutoff go, so a report reopened later keeps its newer
  ones.
- The attachment row stays with `removed_at` set, so the thread can say a photo was there. Asking
  for a removed image answers 410 IMAGE_REMOVED.

## Consequences
Supersedes the "kept until the account is deleted" consequence of ADR support-1. Operators cannot
look at old screenshots when a problem comes back after six months.
