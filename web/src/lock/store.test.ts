import { act, renderHook } from "@testing-library/react"
import { beforeEach, describe, expect, it } from "vitest"

import { clearLocalData } from "@/api/auth"
import { db } from "@/db/schema"
import { currentDataKey, initialiseLock, lock, setPin, useLockState } from "./store"

describe("lock state across a change of account", () => {
  beforeEach(async () => {
    await db.delete()
    await db.open()
    await initialiseLock()
  })

  it("asks for a new PIN once the previous account's local data is cleared", async () => {
    const { result } = renderHook(() => useLockState())
    await act(async () => {
      await setPin("1234")
      lock()
    })
    expect(result.current).toBe("locked")

    await act(() => clearLocalData())

    expect(result.current).toBe("no-pin")
    expect(currentDataKey()).toBeNull()
  })
})
