# 2026-11-18 — Class-name codemods must not touch other strings
A regex pass that trimmed whitespace inside every quoted string removed meaningful spaces
(`{" "}`, CSV quoting) across 60 files. Change only the class tokens themselves, never normalise
whitespace, and snapshot the tree first when uncommitted work is mixed in.
Source: T-shadcn-ui
