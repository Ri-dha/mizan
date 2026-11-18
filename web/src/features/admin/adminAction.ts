import { createContext, useContext } from "react"
import { useTranslation } from "react-i18next"

export type Action = () => Promise<unknown>
export type Run = (action: Action, done?: string) => Promise<boolean>

export const AdminActionContext = createContext<Run>(async () => false)

export function useAdminAction(): Run {
  return useContext(AdminActionContext)
}

export function useWhen() {
  const { i18n } = useTranslation()
  return (iso: string | null | undefined) =>
    iso ? new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso)) : "—"
}
