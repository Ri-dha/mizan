import { db, type Asset, type AssetType, type AssetValuation, type Liquidity, type Visibility } from "./schema"
import { softDelete, writeFields } from "./write"
import { toBaseAmount } from "./income"
import { depreciate, wholeMonthsBetween, type DepreciationMethod } from "@/domain/asset/depreciation"
import type { AssetClass } from "@/domain/networth/networth"

export interface AssetInput {
  type: AssetType
  name: string
  purchaseDate: string | null
  purchasePrice: number
  currency: string
  fxRateMicros: number
  liquidity: Liquidity
  depreciationMethod: DepreciationMethod
  annualRateBasisPoints: number
  salvageValue: number
  attributes: Record<string, string | number>
  note: string | null
  visibility: Visibility
}

/** Property is the one type a household cannot usually turn into cash within a month. */
export const DEFAULT_LIQUIDITY: Record<AssetType, Liquidity> = {
  VEHICLE: "LIQUID", PROPERTY: "ILLIQUID", ELECTRONICS: "LIQUID", EQUIPMENT: "LIQUID", FURNITURE: "LIQUID", LIVESTOCK: "LIQUID", OTHER: "LIQUID",
}

export const ATTRIBUTE_KEYS: Record<AssetType, string[]> = {
  VEHICLE: ["make", "model", "year", "plate", "mileageKm"],
  PROPERTY: ["address", "areaSqm", "deed"],
  ELECTRONICS: ["brand", "model", "serial"],
  EQUIPMENT: ["brand", "model", "serial"],
  FURNITURE: [],
  LIVESTOCK: ["headCount"],
  OTHER: [],
}

export async function createAsset(input: AssetInput): Promise<string> {
  const id = crypto.randomUUID()
  const sortOrder = await db.assets.count()
  await writeFields<Asset>("asset", id, { ...input, status: "HELD", soldOn: null, salePrice: null, sortOrder })
  return id
}

export async function updateAsset(id: string, input: Partial<AssetInput>) {
  await writeFields<Asset>("asset", id, input)
}

export async function addValuation(asset: Asset, valuedOn: string, value: number, note: string | null) {
  await writeFields<AssetValuation>("asset_valuation", crypto.randomUUID(), {
    visibility: asset.visibility, assetId: asset.id, valuedOn, value, source: "MANUAL", note,
  })
}

export async function removeValuation(id: string) {
  await softDelete("asset_valuation", id)
}

export async function sellAsset(id: string, soldOn: string, salePrice: number) {
  await writeFields<Asset>("asset", id, { status: "SOLD", soldOn, salePrice })
}

export async function unsellAsset(id: string) {
  await writeFields<Asset>("asset", id, { status: "HELD", soldOn: null, salePrice: null })
}

export const liveAssets = () => db.assets.filter((a) => a.deletedAt === null).sortBy("sortOrder")
export const liveValuations = () => db.assetValuations.filter((v) => v.deletedAt === null).toArray()

export function valuationsOf(assetId: string, valuations: AssetValuation[]): AssetValuation[] {
  return valuations.filter((v) => v.assetId === assetId).sort((a, b) => (a.valuedOn < b.valuedOn ? 1 : -1))
}

export interface AssetValue {
  /** In the asset's own currency. */
  value: number
  /** In the household's base currency at the asset's frozen rate. */
  baseValue: number
  baselineDate: string
  baselineSource: "VALUATION" | "PURCHASE"
  monthsSinceBaseline: number
}

/** BR-13: latest manual valuation, else the purchase, depreciated to today. */
export function currentValue(asset: Asset, valuations: AssetValuation[], today: string): AssetValue {
  const latest = valuationsOf(asset.id, valuations)[0]
  const baselineDate = latest?.valuedOn ?? asset.purchaseDate ?? today
  const result = depreciate({
    baselineValue: latest?.value ?? asset.purchasePrice,
    baselineDate,
    salvageValue: asset.salvageValue,
    annualRateBasisPoints: asset.annualRateBasisPoints,
    method: asset.depreciationMethod,
    asOf: today,
  })
  return {
    value: result.value,
    baseValue: toBaseAmount(result.value, asset.fxRateMicros),
    baselineDate,
    baselineSource: latest ? "VALUATION" : "PURCHASE",
    monthsSinceBaseline: wholeMonthsBetween(baselineDate, today),
  }
}

export function classOf(type: AssetType): AssetClass {
  if (type === "VEHICLE") return "VEHICLES"
  if (type === "PROPERTY") return "PROPERTY"
  return "OTHER_ASSETS"
}

export interface AssetTotals {
  vehicles: number
  property: number
  otherAssets: number
  illiquid: number
}

export function assetTotals(assets: Asset[], valuations: AssetValuation[], today: string): AssetTotals {
  const totals: AssetTotals = { vehicles: 0, property: 0, otherAssets: 0, illiquid: 0 }
  for (const asset of assets) {
    if (asset.status !== "HELD") continue
    const { baseValue } = currentValue(asset, valuations, today)
    const cls = classOf(asset.type)
    if (cls === "VEHICLES") totals.vehicles += baseValue
    else if (cls === "PROPERTY") totals.property += baseValue
    else totals.otherAssets += baseValue
    if (asset.liquidity === "ILLIQUID") totals.illiquid += baseValue
  }
  return totals
}

/** FR-AST-04: held assets whose baseline is older than the reminder interval. */
export function staleAssets(assets: Asset[], valuations: AssetValuation[], today: string, months: number): Asset[] {
  return assets.filter((a) => a.status === "HELD" && currentValue(a, valuations, today).monthsSinceBaseline >= months)
}

export function realisedGain(asset: Asset): number | null {
  if (asset.status !== "SOLD" || asset.salePrice === null) return null
  return asset.salePrice - asset.purchasePrice
}
