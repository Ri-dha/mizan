import type { Placement } from "react-joyride"

export interface TourStep {
  target: string
  titleKey: string
  bodyKey: string
  placement?: Placement | "auto" | "center"
}

export interface TourDefinition {
  id: string
  /** Manual page the tour's last step links to. */
  helpSlug: string
  steps: TourStep[]
}

const step = (target: string, key: string, placement?: TourStep["placement"]): TourStep => ({
  target: target === "body" ? "body" : `[data-tour="${target}"]`,
  titleKey: `tours.${key}.title`,
  bodyKey: `tours.${key}.body`,
  placement,
})

export const TOURS: Record<string, TourDefinition> = {
  welcome: {
    id: "welcome",
    helpSlug: "getting-started",
    steps: [
      step("body", "welcome.intro", "center"),
      step("home-networth", "welcome.networth"),
      step("home-plan", "welcome.plan"),
      step("quick-add", "welcome.quickAdd", "top"),
      step("home-bills", "welcome.bills"),
      step("home-metals", "welcome.metals"),
      step("nav-more", "welcome.more", "top"),
    ],
  },
  plan: {
    id: "plan",
    helpSlug: "plan",
    steps: [
      step("plan-figures", "plan.figures"),
      step("plan-editor", "plan.editor"),
      step("plan-balance", "plan.balance", "top"),
      step("plan-save", "plan.save", "top"),
    ],
  },
  income: {
    id: "income",
    helpSlug: "income",
    steps: [
      step("income-month", "income.month"),
      step("income-add-source", "income.addSource"),
      step("income-sources", "income.sources", "top"),
    ],
  },
  bills: {
    id: "bills",
    helpSlug: "bills",
    steps: [
      step("bills-due", "bills.due"),
      step("bills-add", "bills.add", "top"),
    ],
  },
  ledger: {
    id: "ledger",
    helpSlug: "ledger",
    steps: [
      step("ledger-filters", "ledger.filters"),
      step("ledger-list", "ledger.list"),
      step("quick-add", "ledger.quickAdd", "top"),
    ],
  },
  metals: {
    id: "metals",
    helpSlug: "gold-and-silver",
    steps: [
      step("metals-holdings", "metals.holdings"),
      step("metals-prices", "metals.prices"),
      step("metals-add", "metals.add", "bottom"),
      step("metals-sell", "metals.sell", "top"),
    ],
  },
  networth: {
    id: "networth",
    helpSlug: "net-worth",
    steps: [
      step("networth-headline", "networth.headline"),
      step("networth-trend", "networth.trend"),
      step("networth-months", "networth.months", "top"),
    ],
  },
  settings: {
    id: "settings",
    helpSlug: "settings",
    steps: [
      step("settings-security", "settings.security"),
      step("settings-data", "settings.data"),
      step("settings-account", "settings.account", "top"),
    ],
  },
}
