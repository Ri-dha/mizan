import { useSyncExternalStore } from "react"

export type Theme = "system" | "light" | "dark"

const STORAGE_KEY = "mizan.theme"
const listeners = new Set<() => void>()

function stored(): Theme {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === "light" || value === "dark" ? value : "system"
  } catch {
    return "system"
  }
}

function prefersDark() {
  return window.matchMedia("(prefers-color-scheme: dark)").matches
}

const THEME_COLOR = { light: "#ffffff", dark: "#0a0a0a" } as const
let followingDevice = false

function apply() {
  const theme = stored()
  const dark = theme === "dark" || (theme === "system" && prefersDark())
  document.documentElement.classList.toggle("dark", dark)
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", dark ? THEME_COLOR.dark : THEME_COLOR.light)
}

/** `public/theme-boot.js` sets the class before first paint; this keeps it in step with the device. */
export function applyStoredTheme() {
  apply()
  if (followingDevice) return
  followingDevice = true
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (stored() === "system") apply()
  })
}

export function setTheme(theme: Theme) {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage may be unavailable; the choice then lasts for this visit only.
  }
  apply()
  listeners.forEach((listener) => listener())
}

export function useTheme(): Theme {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    stored,
  )
}

export function resolvedTheme(): "light" | "dark" {
  return document.documentElement.classList.contains("dark") ? "dark" : "light"
}
