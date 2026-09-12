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
import { VerifyPage } from "@/features/auth/VerifyPage"
import { HomePage } from "@/features/home/HomePage"
import { AccountsPage } from "@/features/accounts/AccountsPage"
import { IncomePage } from "@/features/income/IncomePage"
import { PlanPage } from "@/features/plan/PlanPage"
import { TransactionsPage } from "@/features/transactions/TransactionsPage"
import { BillsPage } from "@/features/bills/BillsPage"
import { DebtsPage } from "@/features/debts/DebtsPage"
import { GoalsPage } from "@/features/goals/GoalsPage"
import { MorePage } from "@/features/more/MorePage"
import { SyncPage } from "@/features/sync/SyncPage"
import { SettingsPage } from "@/features/settings/SettingsPage"

const router = createBrowserRouter([
  {
    element: <RedirectIfSession />,
    children: [
      { path: "/login", element: <LoginPage /> },
      { path: "/register", element: <RegisterPage /> },
      { path: "/reset", element: <ResetPasswordPage /> },
    ],
  },
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
      { path: "more", element: <MorePage /> },
      { path: "income", element: <IncomePage /> },
      { path: "accounts", element: <AccountsPage /> },
      { path: "sync", element: <SyncPage /> },
      { path: "settings", element: <SettingsPage /> },
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
