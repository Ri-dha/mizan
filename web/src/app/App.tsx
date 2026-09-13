import { lazy, Suspense } from "react"
import { DirectionProvider } from "@radix-ui/react-direction"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { createBrowserRouter, RouterProvider } from "react-router"

import { loadSession } from "@/api/auth"
import { Toaster } from "@/components/ui/sonner"
import { directionFor } from "@/i18n"
import { initialiseLock, startLockWatcher } from "@/lock/store"
import { AppShell } from "./AppShell"
import { RequireSession, RedirectIfSession } from "./guards"
import { LoginPage } from "@/features/auth/LoginPage"
import { RegisterPage } from "@/features/auth/RegisterPage"
import { ResetPasswordPage } from "@/features/auth/ResetPasswordPage"
const VerifyPage = lazy(() => import("@/features/auth/VerifyPage").then((m) => ({ default: m.VerifyPage })))
import { HomePage } from "@/features/home/HomePage"
const AccountsPage = lazy(() => import("@/features/accounts/AccountsPage").then((m) => ({ default: m.AccountsPage })))
const IncomePage = lazy(() => import("@/features/income/IncomePage").then((m) => ({ default: m.IncomePage })))
const PlanPage = lazy(() => import("@/features/plan/PlanPage").then((m) => ({ default: m.PlanPage })))
const TransactionsPage = lazy(() => import("@/features/transactions/TransactionsPage").then((m) => ({ default: m.TransactionsPage })))
const BillsPage = lazy(() => import("@/features/bills/BillsPage").then((m) => ({ default: m.BillsPage })))
const DebtsPage = lazy(() => import("@/features/debts/DebtsPage").then((m) => ({ default: m.DebtsPage })))
const GoalsPage = lazy(() => import("@/features/goals/GoalsPage").then((m) => ({ default: m.GoalsPage })))
const MorePage = lazy(() => import("@/features/more/MorePage").then((m) => ({ default: m.MorePage })))
const MetalsPage = lazy(() => import("@/features/metals/MetalsPage").then((m) => ({ default: m.MetalsPage })))
const NetWorthPage = lazy(() => import("@/features/networth/NetWorthPage").then((m) => ({ default: m.NetWorthPage })))
const AssetsPage = lazy(() => import("@/features/assets/AssetsPage").then((m) => ({ default: m.AssetsPage })))
const HouseholdPage = lazy(() => import("@/features/household/HouseholdPage").then((m) => ({ default: m.HouseholdPage })))
const JoinPage = lazy(() => import("@/features/household/JoinPage").then((m) => ({ default: m.JoinPage })))
const HelpPage = lazy(() => import("@/features/help/HelpPage").then((m) => ({ default: m.HelpPage })))
const ReportPage = lazy(() => import("@/features/report/ReportPage").then((m) => ({ default: m.ReportPage })))
const YearReportPage = lazy(() => import("@/features/report/YearReportPage").then((m) => ({ default: m.YearReportPage })))
const AdvisorPage = lazy(() => import("@/features/advisor/AdvisorPage").then((m) => ({ default: m.AdvisorPage })))
const SyncPage = lazy(() => import("@/features/sync/SyncPage").then((m) => ({ default: m.SyncPage })))
const SettingsPage = lazy(() => import("@/features/settings/SettingsPage").then((m) => ({ default: m.SettingsPage })))

const router = createBrowserRouter([
  {
    element: <RedirectIfSession />,
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
      { path: "/reset", element: <ResetPasswordPage /> },
    ],
  },
  { path: "/join/:token", element: <Suspense fallback={null}><JoinPage /></Suspense> },
  {
    path: "/",
    element: (
      <RequireSession>
        <AppShell />
      </RequireSession>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: "verify", element: <VerifyPage /> },
      { path: "transactions", element: <TransactionsPage /> },
      { path: "plan", element: <PlanPage /> },
      { path: "bills", element: <BillsPage /> },
      { path: "debts", element: <DebtsPage /> },
      { path: "goals", element: <GoalsPage /> },
      { path: "metals", element: <MetalsPage /> },
      { path: "networth", element: <NetWorthPage /> },
      { path: "report", element: <ReportPage /> },
      { path: "report/year", element: <YearReportPage /> },
      { path: "help", element: <HelpPage /> },
      { path: "help/:slug", element: <HelpPage /> },
      { path: "more", element: <MorePage /> },
      { path: "income", element: <IncomePage /> },
      { path: "accounts", element: <AccountsPage /> },
      { path: "assets", element: <AssetsPage /> },
      { path: "sync", element: <SyncPage /> },
      { path: "settings", element: <SettingsPage /> },
      { path: "household", element: <HouseholdPage /> },
      { path: "advisor", element: <AdvisorPage /> },
    ],
  },
])

export default function App() {
  const { i18n } = useTranslation()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    void Promise.all([loadSession(), initialiseLock()]).then(() => setReady(true))
    startLockWatcher()
  }, [])

  if (!ready) return null

  return (
    <DirectionProvider dir={directionFor(i18n.language)}>
      <RouterProvider router={router} />
      <Toaster position="bottom-center" />
    </DirectionProvider>
  )
}
