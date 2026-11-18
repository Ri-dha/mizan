import { useTranslation } from "react-i18next"

/** IQWealth's free licence requires this credit wherever its numbers are shown. */
const CREDITED_SOURCES: Record<string, { name: string; url: string }> = {
  "iraqsm.com": { name: "IQWealth", url: "https://iraqsm.com" },
}

export function SourceCredit({ source }: { source: string | null | undefined }) {
  const { t } = useTranslation()
  const credit = source ? CREDITED_SOURCES[source] : undefined
  if (!credit) return null
  return (
    <a href={credit.url} target="_blank" rel="noopener noreferrer" className="text-xs underline opacity-70">
      {t("metals.dataCredit", { name: credit.name })}
    </a>
  )
}
