import { useEffect, type ReactNode } from "react"
import { Navigate, Outlet } from "react-router"

import { useSession } from "@/api/auth"
import { LockScreen } from "@/features/lock/LockScreen"
import { SetPinScreen } from "@/features/lock/SetPinScreen"
import { useLockState } from "@/lock/store"
import { startSyncEngine } from "@/sync/engine"

export function RequireSession({ children }: { children: ReactNode }) {
  const session = useSession()
  const lock = useLockState()

  useEffect(() => {
    if (session) startSyncEngine()
  }, [session])

  if (session === undefined) return null
  if (session === null) return <Navigate to="/login" replace />
  if (lock === "locked") return <LockScreen />
  if (lock === "no-pin") return <SetPinScreen />
  return <>{children}</>
}

export function RedirectIfSession() {
  const session = useSession()
  if (session === undefined) return null
  if (session) return <Navigate to="/" replace />
  return <Outlet />
}
