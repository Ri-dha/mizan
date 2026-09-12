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

export function applyStoredTheme() {
  const theme = stored()
  document.documentElement.classList.toggle("dark", theme === "dark" || (theme === "system" && prefersDark()))
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (stored() === "system") applyStoredTheme()
  })
}

export function setTheme(theme: Theme) {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage may be unavailable; the choice then lasts for this visit only.
  }
  applyStoredTheme()
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
