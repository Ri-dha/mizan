# ADR manual-1 — One Markdown source for the in-app manual and the static site

- Date: 2026-09-13
- Status: accepted
- Task: T-m9

## Context
Users need help offline in Arabic and English, and the project wanted a public manual too.

## Decision
Pages live in `docs/manual/<lang>/<order>-<slug>.md` with title, screen and summary front matter.
The app bundles them at `/help`; `web/scripts/build-docs.mjs` renders the same files to `/docs`.
The build fails when an English page has no Arabic counterpart.

## Consequences
One edit updates both outputs. Every user-facing change includes a manual edit in both languages.
