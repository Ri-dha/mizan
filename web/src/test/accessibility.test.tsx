import axe from "axe-core"
import { render } from "@testing-library/react"
import { MemoryRouter } from "react-router"
import { describe, expect, it } from "vitest"

import "@/i18n"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"
import { ResetPasswordPage } from "@/features/auth/ResetPasswordPage"

/** NFR-08: the screens a visitor meets first must pass WCAG 2.1 AA checks (colour contrast excluded; jsdom does not paint). */
async function violations(ui: React.ReactElement) {
  const { container } = render(<MemoryRouter>{ui}</MemoryRouter>)
  const result = await axe.run(container, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21aa"] }, rules: { "color-contrast": { enabled: false } } })
  return result.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(" ")).join(", ")}`)
}

describe("accessibility", () => {
  it("sign in", async () => expect(await violations(<LoginPage />)).toEqual([]))
  it("register", async () => expect(await violations(<RegisterPage />)).toEqual([]))
  it("password reset", async () => expect(await violations(<ResetPasswordPage />)).toEqual([]))
})
