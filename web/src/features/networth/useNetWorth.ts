import { useLiveQuery } from "dexie-react-hooks"

import { useSession } from "@/api/auth"
import { liveAssets, liveValuations } from "@/db/assets"
import { liveCashAccounts } from "@/db/cashAccounts"
import { liveDebts, livePayments } from "@/db/debts"
import { liveMonthCloses, liveNetWorth, liveSnapshots } from "@/db/networth"
import type { Asset, AssetValuation, MonthClose, NetWorthSnapshot } from "@/db/schema"
import { todayIso } from "@/domain/calendar/month"
import type { NetWorthResult } from "@/domain/networth/networth"
import { useMetals } from "@/features/metals/useMetals"

export interface NetWorthView {
  current: NetWorthResult
  closes: MonthClose[]
  snapshots: NetWorthSnapshot[]
  assets: Asset[]
  valuations: AssetValuation[]
  rateSet: Record<string, unknown>
}

const EMPTY: NetWorthResult = { totalAssets: 0, totalLiabilities: 0, netWorth: 0, liquidAssets: 0, illiquidAssets: 0, composition: [] }

/** FR-NET-01: the live figure reconciles to its components by construction; snapshots come from the server. */
export function useNetWorth(): NetWorthView {
  const session = useSession()
  const base = session?.baseCurrency ?? "IQD"
  const metals = useMetals()
  const metalsValue = metals.lines.reduce((sum, line) => sum + line.valueNow, 0)
  const live = useLiveQuery(async () => {
    const [accounts, debts, payments, closes, snapshots, assets, valuations] = await Promise.all([
      liveCashAccounts(), liveDebts(), livePayments(), liveMonthCloses(), liveSnapshots(), liveAssets(), liveValuations(),
    ])
    return { current: liveNetWorth(accounts, base, metals.prices, metalsValue, debts, payments, assets, valuations, todayIso()), closes, snapshots, assets, valuations }
  }, [base, metals.prices, metalsValue], { current: EMPTY, closes: [] as MonthClose[], snapshots: [] as NetWorthSnapshot[], assets: [] as Asset[], valuations: [] as AssetValuation[] })

  return {
    ...live,
    rateSet: {
      rateKind: metals.prices.rateKind,
      usdIqdMicros: metals.prices.usdIqdMicros,
      usdIqdSource: metals.prices.rateSource.label,
      xauSource: metals.prices.spot.GOLD.source.label,
      xagSource: metals.prices.spot.SILVER.source.label,
    },
  }
}
