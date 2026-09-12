import type { ReactNode } from "react"

import { Label } from "@/components/ui/label"

export function Field({ id, label, hint, children }: { id: string; label: string; hint?: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && <p className="text-xs opacity-70">{hint}</p>}
    </div>
  )
}
