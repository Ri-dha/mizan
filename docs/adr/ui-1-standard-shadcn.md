# ADR ui-1 — Standard shadcn/ui with device light and dark

- Date: 2026-11-18
- Status: accepted
- Task: T-shadcn-ui
- Supersedes: the "neobrutalism.dev components" choice in ADR foundation-1

## Context
The web app used neobrutalism.dev, a restyled shadcn registry with hard shadows and thick
borders. The owner wants the standard shadcn/ui look, with light and dark following the device.
The app already had a working System/Light/Dark switch (`src/app/theme.ts`).

## Decision
- Components come from the official shadcn registry (`npx shadcn@latest add <name>`, style
  new-york, Tailwind v4, CSS variables). They are vendored in `src/components/ui` and may be edited.
- Tokens are shadcn's set on `:root` and `.dark` in `src/index.css`: a neutral base, with Mizan
  blue as `--primary` and as `chart-1`. Screens use tokens (`bg-primary`, `text-muted-foreground`,
  `text-destructive`), never raw colours.
- Variant mapping from the old kit: button `neutral` → `outline`; badge `neutral` → `secondary`;
  losses and errors → `destructive`.
- The theme follows the device by default and keeps the Settings override. The class is set by an
  inline script in `index.html` before first paint; `theme-color` follows the resolved theme.

## Consequences
- Screens and tours lose the hard-shadow styling; new screens use shadcn blocks and components.
- Arabic stays right-to-left through logical utilities; each new component is checked in RTL.
