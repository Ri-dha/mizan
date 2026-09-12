import createClient, { type Middleware } from "openapi-fetch"

import type { paths } from "./generated"

const REFRESH_PATH = "/api/v1/auth/refresh"
const AUTH_PATHS_WITHOUT_TOKEN = new Set([
  "/api/v1/auth/register",
  "/api/v1/auth/login",
  REFRESH_PATH,
  "/api/v1/auth/logout",
  "/api/v1/auth/password-reset/request",
  "/api/v1/auth/password-reset/confirm",
])

let accessToken: string | null = null
let refreshInFlight: Promise<boolean> | null = null
let onSessionLost: () => void = () => {}

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function hasAccessToken() {
  return accessToken !== null
}

export function setSessionLostHandler(handler: () => void) {
  onSessionLost = handler
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly detail: string
  readonly properties: Record<string, unknown>

  constructor(status: number, code: string, detail: string, properties: Record<string, unknown> = {}) {
    super(detail)
    this.status = status
    this.code = code
    this.detail = detail
    this.properties = properties
  }
}

export const NETWORK_ERROR = new ApiError(0, "NETWORK", "Cannot reach the server")

/**
 * Refreshes through the httpOnly cookie. Only one refresh runs at a time; concurrent 401s
 * wait for it rather than each rotating the token and tripping reuse detection.
 */
export async function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch(REFRESH_PATH, { method: "POST", credentials: "include" })
        if (!response.ok) {
          accessToken = null
          if (response.status === 401) onSessionLost()
          return false
        }
        const body = (await response.json()) as { accessToken: string }
        accessToken = body.accessToken
        return true
      } catch {
        return false
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return refreshInFlight
}

const bearer: Middleware = {
  async onRequest({ request, schemaPath }) {
    if (AUTH_PATHS_WITHOUT_TOKEN.has(schemaPath)) return request
    if (!accessToken && !(await refreshAccessToken())) return request
    request.headers.set("Authorization", `Bearer ${accessToken}`)
    return request
  },
  async onResponse({ request, response, schemaPath }) {
    if (response.status !== 401 || AUTH_PATHS_WITHOUT_TOKEN.has(schemaPath)) return response
    if (!(await refreshAccessToken())) return response
    const retry = request.clone()
    retry.headers.set("Authorization", `Bearer ${accessToken}`)
    return fetch(retry)
  },
}

export const api = createClient<paths>({ baseUrl: "/", credentials: "include" })
api.use(bearer)

/** Turns openapi-fetch's `{ data, error }` into a value or a typed ApiError. */
export async function unwrap<T>(call: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  let result: { data?: T; error?: unknown; response: Response }
  try {
    result = await call
  } catch {
    throw NETWORK_ERROR
  }
  if (result.error !== undefined || !result.response.ok) {
    const problem = (result.error ?? {}) as { code?: string; detail?: string; status?: number } & Record<string, unknown>
    throw new ApiError(result.response.status, problem.code ?? "UNKNOWN", problem.detail ?? result.response.statusText, problem)
  }
  return result.data as T
}
