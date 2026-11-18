import { readFileSync } from "node:fs"
import path from "node:path"

import { productionHeaders } from "../../vite.config"

const headers = productionHeaders()
const policy = Object.fromEntries((headers["Content-Security-Policy"] ?? "").split(";").map((part) => {
  const [name, ...sources] = part.trim().split(/\s+/)
  return [name, sources]
}))

describe("production security headers", () => {
  it("preview serves the policy Caddy serves, with the storage origin filled in", () => {
    expect(policy["default-src"]).toEqual(["'self'"])
    expect(policy["connect-src"]).toEqual(["'self'", "http://localhost:9000"])
    expect(headers["Content-Security-Policy"]).not.toContain("{$")
    expect(headers["X-Frame-Options"]).toBe("DENY")
  })

  it("never allows inline or evaluated scripts", () => {
    expect(policy["script-src"]).toEqual(["'self'", "'wasm-unsafe-eval'"])
    expect(policy["object-src"]).toEqual(["'none'"])
    expect(policy["frame-ancestors"]).toEqual(["'none'"])
  })

  it("the page itself carries no inline script the policy would block", () => {
    const html = readFileSync(path.resolve(import.meta.dirname, "../../index.html"), "utf8")
    const inline = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>/g)]
    expect(inline).toEqual([])
  })
})
