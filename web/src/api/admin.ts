import { api, unwrap } from "./client"
import { authKeyFor } from "./auth"
import type { components } from "./generated"

export type Overview = components["schemas"]["Overview"]
export type Setting = components["schemas"]["SettingResponse"]
export type Account = components["schemas"]["AccountSummary"]
export type Announcement = components["schemas"]["AnnouncementResponse"]
export type AuditEntry = components["schemas"]["Entry"]
export type Operator = components["schemas"]["OperatorSummary"]

export const adminSession = () => unwrap(api.GET("/api/v1/admin/session"))
export const elevate = async (password: string) => unwrap(api.POST("/api/v1/admin/session", { body: { authKey: await authKeyFor(password) } }))
export const fetchOverview = () => unwrap(api.GET("/api/v1/admin/overview"))
export const fetchSettings = () => unwrap(api.GET("/api/v1/admin/settings"))
export const saveSetting = (key: string, value: string) =>
  unwrap(api.PUT("/api/v1/admin/settings/{key}", { params: { path: { key: key as never } }, body: { value } }))
export const resetSetting = (key: string) => unwrap(api.DELETE("/api/v1/admin/settings/{key}", { params: { path: { key: key as never } } }))
export const pollNow = (target: "DOLLAR" | "ALL") => unwrap(api.POST("/api/v1/admin/feeds/poll", { body: { target } }))
export const correctPrice = (instrument: string, priceMicros: number, reason: string) =>
  unwrap(api.POST("/api/v1/admin/prices/corrections", { body: { instrument: instrument as never, priceMicros, reason } }))
export const deletePriceChange = (id: number, reason: string) =>
  unwrap(api.DELETE("/api/v1/admin/prices/changes/{id}", { params: { path: { id } }, body: { reason } }))
export const searchAccounts = (query: string) => unwrap(api.GET("/api/v1/admin/accounts", { params: { query: { query } } }))
export const suspendAccount = (userId: string, reason: string) =>
  unwrap(api.POST("/api/v1/admin/accounts/{userId}/suspend", { params: { path: { userId } }, body: { reason } }))
export const restoreAccount = (userId: string) => unwrap(api.POST("/api/v1/admin/accounts/{userId}/restore", { params: { path: { userId } } }))
export const revokeSessions = (userId: string) => unwrap(api.POST("/api/v1/admin/accounts/{userId}/revoke-sessions", { params: { path: { userId } } }))
export const cancelDeletion = (userId: string) => unwrap(api.POST("/api/v1/admin/accounts/{userId}/cancel-deletion", { params: { path: { userId } } }))
export const fetchAnnouncements = () => unwrap(api.GET("/api/v1/admin/announcements"))
export const publishAnnouncement = (body: { messageEn: string; messageAr: string; level: "INFO" | "WARNING"; startsAt?: string; endsAt?: string }) =>
  unwrap(api.POST("/api/v1/admin/announcements", { body }))
export const retractAnnouncement = (id: string) => unwrap(api.DELETE("/api/v1/admin/announcements/{id}", { params: { path: { id } } }))
export const searchAudit = (query: { action?: string; before?: string; limit?: number }) => unwrap(api.GET("/api/v1/admin/audit", { params: { query } }))
export const fetchOperators = () => unwrap(api.GET("/api/v1/admin/operators"))
export const grantOperator = (identifier: string) => unwrap(api.POST("/api/v1/admin/operators", { body: { identifier } }))
export const revokeOperator = (userId: string) => unwrap(api.DELETE("/api/v1/admin/operators/{userId}", { params: { path: { userId } } }))
export const liveAnnouncements = () => unwrap(api.GET("/api/v1/announcements"))
