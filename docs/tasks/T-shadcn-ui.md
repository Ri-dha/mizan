# T-shadcn-ui — Move the web app from neobrutalism to standard shadcn/ui, light and dark
- Status: done (2026-11-18), commit b77e121 on feat/e2e-prices-admin, fast-forwarded to main at d5099dc
- Context: owner request 2026-11-18. Supersedes the "neobrutalism.dev components" part of ADR
  foundation-1 (needs ADR ui-1 before work starts). Do this before T-price-history and
  T-admin-dashboard so their new screens are built once, in the new look.
- Decisions (owner, 2026-11-18): first of the three; neutral base with Mizan blue as the accent;
  follow the device by default and keep the Light/Dark/System choice in Settings.
- Notes:
  - Today: `components.json` already points at shadcn (new-york, lucide). The 16 files in
    `src/components/ui` are neobrutalism variants (`variant="neutral"` in 38 files, hard shadow,
    `border-2`, `rounded-base`, `font-heading` in 47). `src/app/theme.ts` already follows the
    device (`System` default, live switch) with a Light/Dark/System choice in Settings.
  - Plan:
    1. ADR ui-1: standard shadcn/ui, CSS-variable tokens, light and dark from the device.
    2. Replace the tokens in `src/index.css` with the shadcn set (background, foreground, card,
       popover, primary, secondary, muted, accent, destructive, border, input, ring, chart-1..5,
       sidebar) for `:root` and `.dark`. Keep Mizan's accent as `--primary`.
    3. Re-add the 16 components from the shadcn registry, then add what the app lacks:
       `chart` (Recharts wrapper with theme colours), `sidebar` (desktop navigation), `drawer`
       (mobile sheets), `skeleton`, `separator`, `toggle-group` (chart ranges).
    4. Codemod the screens: `variant="neutral"` to `outline` or `secondary`, drop the hard-shadow,
       `border-2`, `rounded-base` and `font-heading` classes, map `text-main`/`bg-main` to
       `primary`, `text-chart-2` (losses) to `destructive`.
    5. Dark mode: keep `System` as the default and the Settings override. Apply the class before
       first paint (inline script in `index.html`), set both `theme-color` metas and the PWA
       manifest colours, theme Sonner, Joyride tours, Recharts and the receipt viewer.
    6. Arabic: keep logical utilities, check every component in RTL, charts stay in an LTR island.
    7. Visual QA: screenshot every route in light and dark, English and Arabic, phone and
       desktop; fix contrast to WCAG AA.
  - Out of scope: changing screen layouts or flows beyond what the new components need.
  - Done: 16 components re-fetched from the official registry (CLI 3.5.0; its stray `cn` npm
    import and `next-themes` were removed in favour of `@/lib/utils` and `src/app/theme.ts`);
    shadcn tokens with Mizan blue; screens converted (101 buttons to `outline`, 14 badges to
    `secondary`, old classes mapped); sidebar and bottom bar restyled; logical sides in select,
    dropdown, dialog, sheet, table and the switch knob; `theme-boot.js` and `theme-color`; tours
    and chart axes themed; plain tables padded; reminder copy in Settings corrected.
  - Not yet: the shadcn `chart`, `sidebar`, `drawer` and `toggle-group` components (added when
    T-price-history needs them); a full screenshot pass of every route.
  - Validated: tsc, 164 web tests, build; screenshots of Home, Ledger with tour, Add transaction,
    Net worth, Settings and Gold & silver in light and dark, English and Arabic, desktop and phone.
