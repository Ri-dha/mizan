import { useLiveQuery } from "dexie-react-hooks"

import { db, type NotificationSetting } from "./schema"
import { writeFields } from "./write"

export type NotificationSwitch = "billDue" | "payDay" | "overspend" | "monthClose" | "metalPrice"
export const NOTIFICATION_SWITCHES: NotificationSwitch[] = ["billDue", "payDay", "overspend", "monthClose", "metalPrice"]

export type NotificationValues = Omit<NotificationSetting, keyof import("./schema").Syncable | "visibility">

/** FR-NTF-06 defaults; they match the server's, which applies them when no row was ever saved. */
export const NOTIFICATION_DEFAULTS: NotificationValues = {
  billDue: true, billLeadDays: 3, payDay: true, overspend: true, overspendThresholdBp: 9000,
  monthClose: true, metalPrice: false, metalMoveBp: 200, quietMode: false,
}

export const liveNotificationSetting = () => db.notificationSettings.filter((n) => n.deletedAt === null).first()

export function useNotificationSettings(): NotificationValues {
  const setting = useLiveQuery(liveNotificationSetting, [], undefined)
  if (!setting) return NOTIFICATION_DEFAULTS
  const { billDue, billLeadDays, payDay, overspend, overspendThresholdBp, monthClose, metalPrice, metalMoveBp, quietMode } = setting
  return { billDue, billLeadDays, payDay, overspend, overspendThresholdBp, monthClose, metalPrice, metalMoveBp, quietMode }
}

export async function saveNotificationSettings(patch: Partial<NotificationValues>) {
  const existing = await liveNotificationSetting()
  const id = existing?.id ?? crypto.randomUUID()
  await writeFields<NotificationSetting>("notification_setting", id, { ...(existing ? {} : { ...NOTIFICATION_DEFAULTS, visibility: "PRIVATE" as const }), ...patch })
}
