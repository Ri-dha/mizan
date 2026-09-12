import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"

import { useSession } from "@/api/auth"
import { Field } from "@/components/Field"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { createCashAccount, updateCashAccount } from "@/db/cashAccounts"
import type { CashAccount, CashAccountKind } from "@/db/schema"
import { fromMinorUnits, toMinorUnits } from "@/domain/money/format"

const KINDS: CashAccountKind[] = ["WALLET", "BANK", "CASH_AT_HOME", "OTHER"]
const CURRENCIES = ["IQD", "USD"]

interface Props {
  open: boolean
  account: CashAccount | null
  onClose: () => void
  onDelete: (account: CashAccount) => void
}

export function AccountSheet({ open, account, onClose, onDelete }: Props) {
  const { t } = useTranslation()
  const session = useSession()
  const [name, setName] = useState("")
  const [kind, setKind] = useState<CashAccountKind>("WALLET")
  const [institution, setInstitution] = useState("")
  const [currency, setCurrency] = useState("IQD")
  const [balance, setBalance] = useState("0")
  const [isPrivate, setIsPrivate] = useState(false)

  useEffect(() => {
    if (!open) return
    setName(account?.name ?? "")
    setKind(account?.kind ?? "WALLET")
    setInstitution(account?.institution ?? "")
    setCurrency(account?.currency ?? session?.baseCurrency ?? "IQD")
    setBalance(account ? fromMinorUnits(account.balance, account.currency) : "0")
    setIsPrivate(account?.visibility === "PRIVATE")
  }, [open, account, session])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const input = {
      name: name.trim(),
      kind,
      institution: institution.trim() || null,
      currency,
      balance: toMinorUnits(balance, currency),
      visibility: isPrivate ? ("PRIVATE" as const) : ("SHARED" as const),
    }
    if (account) await updateCashAccount(account.id, input)
    else await createCashAccount(input)
    onClose()
  }

  return (
    <Sheet open={open} onOpenChange={(next) => !next && onClose()}>
      <SheetContent className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle>{account ? t("accounts.edit") : t("accounts.add")}</SheetTitle>
        </SheetHeader>
        <form onSubmit={submit} className="flex flex-col gap-4 px-4">
          <Field id="name" label={t("accounts.name")}>
            <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
          </Field>
          <Field id="kind" label={t("accounts.kind")}>
            <Select value={kind} onValueChange={(value) => setKind(value as CashAccountKind)}>
              <SelectTrigger id="kind"><SelectValue /></SelectTrigger>
              <SelectContent>
                {KINDS.map((k) => <SelectItem key={k} value={k}>{t(`accounts.kinds.${k}`)}</SelectItem>)}
              </SelectContent>
            </Select>
          </Field>
          <Field id="institution" label={t("accounts.institution")}>
            <Input id="institution" value={institution} onChange={(e) => setInstitution(e.target.value)} maxLength={80} />
          </Field>
          <div className="grid grid-cols-[1fr_auto] gap-2">
            <Field id="balance" label={t("accounts.balance")}>
              <Input id="balance" inputMode="decimal" dir="ltr" value={balance} onChange={(e) => setBalance(e.target.value)} />
            </Field>
            <Field id="currency" label={t("accounts.currency")}>
              <Select value={currency} onValueChange={setCurrency}>
                <SelectTrigger id="currency" className="w-24"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {CURRENCIES.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
          </div>
          <div className="flex items-center gap-2">
            <Switch id="private" checked={isPrivate} onCheckedChange={setIsPrivate} />
            <Label htmlFor="private">{t("accounts.private")}</Label>
          </div>
          <SheetFooter className="flex-row justify-between px-0">
            {account && (
              <Button type="button" variant="neutral" onClick={() => onDelete(account)}>{t("accounts.delete")}</Button>
            )}
            <Button type="submit">{t("accounts.save")}</Button>
          </SheetFooter>
        </form>
      </SheetContent>
    </Sheet>
  )
}
