import { useCallback, useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { toast } from "sonner"

import { updateHousehold, useSession } from "@/api/auth"
import { acceptInvitation, cancelHouseholdDeletion, changeRole, createInvitation, leaveHousehold, listInvitations, listMembers, listMemberships, removeMember, requestHouseholdDeletion, revokeInvitation, switchHousehold, transferOwnership, type Role } from "@/api/household"
import { describeError } from "@/app/errors"
import { Field } from "@/components/Field"
import { HelpButton } from "@/components/HelpButton"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useScreenTour } from "@/tours/useTour"

type Member = Awaited<ReturnType<typeof listMembers>>[number]
type Invitation = Awaited<ReturnType<typeof listInvitations>>[number]
type Membership = Awaited<ReturnType<typeof listMemberships>>[number]

const INVITE_ROLES: Role[] = ["MEMBER", "VIEWER", "DEPENDENT"]

export function HouseholdPage() {
  const { t, i18n } = useTranslation()
  const session = useSession()
  const navigate = useNavigate()
  const [members, setMembers] = useState<Member[]>([])
  const [invitations, setInvitations] = useState<Invitation[]>([])
  const [memberships, setMemberships] = useState<Membership[]>([])
  const [name, setName] = useState(session?.householdName ?? "")
  const [monthStartDay, setMonthStartDay] = useState(session?.monthStartDay ?? 1)
  const [inviteRole, setInviteRole] = useState<Role>("MEMBER")
  const [inviteContact, setInviteContact] = useState("")
  const [lastLink, setLastLink] = useState<string | null>(null)
  const [password, setPassword] = useState("")
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [joinToken, setJoinToken] = useState("")
  const [offline, setOffline] = useState(!navigator.onLine)
  useScreenTour("household", members.length > 0)

  const canManage = session?.role === "OWNER"
  const date = (iso: string) => new Intl.DateTimeFormat(i18n.language === "ar" ? "ar-IQ" : "en-GB", { dateStyle: "medium" }).format(new Date(iso))

  const load = useCallback(async () => {
    try {
      const [m, ms] = await Promise.all([listMembers(), listMemberships()])
      setMembers(m)
      setMemberships(ms)
      if (canManage) setInvitations(await listInvitations())
      setOffline(false)
    } catch (e) {
      setOffline(true)
      if (navigator.onLine) toast(describeError(e))
    }
  }, [canManage])

  useEffect(() => {
    void load()
  }, [load])

  async function run(action: () => Promise<unknown>, done?: string) {
    try {
      await action()
      if (done) toast(done)
      await load()
    } catch (e) {
      toast(describeError(e))
    }
  }

  async function saveSettings(event: FormEvent) {
    event.preventDefault()
    await run(() => updateHousehold({ name, monthStartDay }), t("settings.saved"))
  }

  async function invite(event: FormEvent) {
    event.preventDefault()
    try {
      const created = await createInvitation(inviteRole, inviteContact.trim() || null)
      setLastLink(`${location.origin}/join/${created.token}`)
      setInviteContact("")
      await load()
    } catch (e) {
      toast(describeError(e))
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      toast(t("household.copied"))
    } catch {
      toast(text)
    }
  }

  async function rebuild(action: () => Promise<void>, done: string) {
    try {
      await action()
      toast(done)
      navigate("/")
    } catch (e) {
      toast(describeError(e))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-3xl">{t("household.title")}</h1>
        <HelpButton tour="household" />
      </div>

      {offline && <Alert><AlertDescription>{t("household.needsOnline")}</AlertDescription></Alert>}

      {session?.householdDeletionRequestedAt && (
        <Alert>
          <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
            <span>{t("household.deletionPending", { date: date(session.householdDeletionRequestedAt) })}</span>
            {canManage && <Button size="sm" onClick={() => void run(async () => { await cancelHouseholdDeletion(); await updateHousehold({}) }, t("household.deletionCancelled"))}>{t("household.cancelDeletion")}</Button>}
          </AlertDescription>
        </Alert>
      )}

      <Card data-tour="household-members">
        <CardHeader>
          <CardTitle>{t("household.members")}</CardTitle>
          <CardDescription>{t("household.membersBody")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {members.map((member) => (
            <div key={member.userId} className="flex flex-wrap items-center justify-between gap-2 rounded-base border-2 border-border p-3">
              <div className="flex min-w-0 flex-col">
                <span className="font-heading">{member.displayName} {member.you && <Badge variant="neutral">{t("household.you")}</Badge>}</span>
                <span className="text-sm opacity-70">{member.contact} · {t("household.since", { date: date(member.joinedAt) })}</span>
              </div>
              <div className="flex items-center gap-2">
                {canManage && !member.you && member.role !== "OWNER" ? (
                  <>
                    <Select value={member.role} onValueChange={(role) => void run(() => changeRole(member.userId, role as Role), t("household.roleChanged"))}>
                      <SelectTrigger className="w-36"><SelectValue /></SelectTrigger>
                      <SelectContent>{INVITE_ROLES.map((r) => <SelectItem key={r} value={r}>{t(`household.roles.${r}`)}</SelectItem>)}</SelectContent>
                    </Select>
                    <Button size="sm" variant="neutral" onClick={() => void run(() => transferOwnership(member.userId).then(() => updateHousehold({})), t("household.transferred"))}>{t("household.makeOwner")}</Button>
                    <Button size="sm" variant="neutral" onClick={() => void run(() => removeMember(member.userId), t("household.removed"))}>{t("household.remove")}</Button>
                  </>
                ) : (
                  <Badge>{t(`household.roles.${member.role}`)}</Badge>
                )}
              </div>
            </div>
          ))}
          {members.length === 0 && !offline && <p className="opacity-70">{t("household.loading")}</p>}
          <p className="text-xs opacity-70">{t("household.rolesHint")}</p>
        </CardContent>
      </Card>

      {canManage && (
        <Card data-tour="household-invite">
          <CardHeader>
            <CardTitle>{t("household.invite")}</CardTitle>
            <CardDescription>{t("household.inviteBody")}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <form onSubmit={invite} className="grid gap-2 sm:grid-cols-[1fr_10rem_auto]">
              <Field id="contact" label={t("household.contact")}>
                <Input id="contact" value={inviteContact} onChange={(e) => setInviteContact(e.target.value)} placeholder={t("household.contactHint")} maxLength={160} />
              </Field>
              <Field id="role" label={t("household.role")}>
                <Select value={inviteRole} onValueChange={(v) => setInviteRole(v as Role)}>
                  <SelectTrigger id="role"><SelectValue /></SelectTrigger>
                  <SelectContent>{INVITE_ROLES.map((r) => <SelectItem key={r} value={r}>{t(`household.roles.${r}`)}</SelectItem>)}</SelectContent>
                </Select>
              </Field>
              <Button type="submit" className="self-end">{t("household.createLink")}</Button>
            </form>
            {lastLink && (
              <Alert>
                <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
                  <span className="break-all text-sm" dir="ltr">{lastLink}</span>
                  <Button size="sm" onClick={() => void copy(lastLink)}>{t("household.copyLink")}</Button>
                </AlertDescription>
              </Alert>
            )}
            {invitations.map((invitation) => (
              <div key={invitation.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>{t(`household.roles.${invitation.role}`)}{invitation.contact ? ` · ${invitation.contact}` : ""} · {t("household.expires", { date: date(invitation.expiresAt) })}</span>
                <Button size="sm" variant="neutral" onClick={() => void run(() => revokeInvitation(invitation.id), t("household.revoked"))}>{t("household.revoke")}</Button>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader><CardTitle>{t("settings.household")}</CardTitle></CardHeader>
        <CardContent>
          <form onSubmit={saveSettings} className="flex flex-col gap-4">
            <Field id="householdName" label={t("settings.householdName")}>
              <Input id="householdName" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} required disabled={!canManage && session?.role !== "MEMBER"} />
            </Field>
            <Field id="monthStartDay" label={t("settings.monthStartDay")}>
              <Input id="monthStartDay" type="number" min={1} max={28} dir="ltr" value={monthStartDay} onChange={(e) => setMonthStartDay(Number(e.target.value))} disabled={!canManage && session?.role !== "MEMBER"} />
            </Field>
            {(canManage || session?.role === "MEMBER") && <Button type="submit" className="self-start">{t("accounts.save")}</Button>}
          </form>
        </CardContent>
      </Card>

      <Card data-tour="household-switch">
        <CardHeader>
          <CardTitle>{t("household.yours")}</CardTitle>
          <CardDescription>{t("household.yoursBody")}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          {memberships.map((m) => (
            <div key={m.householdId} className="flex items-center justify-between gap-2">
              <span>{m.householdName} <Badge variant="neutral">{t(`household.roles.${m.role}`)}</Badge></span>
              {m.current ? <Badge>{t("household.current")}</Badge> : <Button size="sm" variant="neutral" onClick={() => void rebuild(() => switchHousehold(m.householdId), t("household.switched", { name: m.householdName }))}>{t("household.switch")}</Button>}
            </div>
          ))}
          <form onSubmit={(e) => { e.preventDefault(); const token = joinToken.trim().split("/").pop() ?? ""; if (token) void rebuild(() => acceptInvitation(token), t("household.joined")) }} className="flex flex-wrap items-end gap-2">
            <Field id="joinToken" label={t("household.joinWithLink")}>
              <Input id="joinToken" dir="ltr" value={joinToken} onChange={(e) => setJoinToken(e.target.value)} placeholder={`${location.origin}/join/…`} />
            </Field>
            <Button type="submit" variant="neutral">{t("household.join")}</Button>
          </form>
          {session?.role !== "OWNER" && (
            <Button variant="neutral" className="self-start" onClick={() => void rebuild(leaveHousehold, t("household.left"))}>{t("household.leave")}</Button>
          )}
        </CardContent>
      </Card>

      {canManage && !session?.householdDeletionRequestedAt && (
        <Card>
          <CardHeader>
            <CardTitle>{t("household.deleteTitle")}</CardTitle>
            <CardDescription>{t("household.deleteBody")}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="neutral" onClick={() => setConfirmDelete(true)}>{t("household.delete")}</Button>
          </CardContent>
        </Card>
      )}

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("household.deleteTitle")}</DialogTitle>
            <DialogDescription>{t("household.deleteConfirm")}</DialogDescription>
          </DialogHeader>
          <form onSubmit={(e) => { e.preventDefault(); void run(async () => { await requestHouseholdDeletion(password); await updateHousehold({}); setConfirmDelete(false); setPassword("") }, t("household.deletionRequested")) }} className="flex flex-col gap-4">
            <Field id="password" label={t("auth.password")}>
              <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="current-password" />
            </Field>
            <DialogFooter>
              <Button type="button" variant="neutral" onClick={() => setConfirmDelete(false)}>{t("settings.cancel")}</Button>
              <Button type="submit">{t("household.delete")}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
