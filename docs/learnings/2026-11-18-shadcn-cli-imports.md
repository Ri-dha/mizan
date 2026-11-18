# 2026-11-18 — shadcn CLI can import `cn` from npm
`npx shadcn@3.5.0 add … --overwrite` in this repo wrote `import { cn } from "cn"` into every
component and installed the unrelated npm package `cn`; the toast component also pulled in
`next-themes`. After any `shadcn add`, check `package.json` and the imports: `cn` must come from
`@/lib/utils`, and the toast theme from `@/app/theme`. Also replace physical sides (`right-4`,
`pl-8`, `text-left`, the switch's `translate-x`) with logical ones for Arabic.
Source: T-shadcn-ui
Adding a component that depends on one already present (`chart` needs `card`) makes the CLI stop
at an overwrite prompt after it has changed `package.json` (it pinned `recharts@3.8.0`). For new
components, read `https://ui.shadcn.com/r/styles/new-york-v4/<name>.json` and write the file
yourself with `@/lib/utils` and `@/components/ui/*` imports.
