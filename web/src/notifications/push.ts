import { api, unwrap } from "@/api/client"

export type PushState = "unsupported" | "denied" | "off" | "on"

function urlBase64ToUint8Array(base64: string): Uint8Array<ArrayBuffer> {
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4)
  const raw = atob(padded.replace(/-/g, "+").replace(/_/g, "/"))
  const bytes = new Uint8Array(new ArrayBuffer(raw.length))
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
  return bytes
}

function supported(): boolean {
  return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window
}

async function registration(): Promise<ServiceWorkerRegistration | undefined> {
  return navigator.serviceWorker.getRegistration()
}

export async function pushState(): Promise<PushState> {
  if (!supported()) return "unsupported"
  if (Notification.permission === "denied") return "denied"
  const existing = await (await registration())?.pushManager.getSubscription()
  return existing ? "on" : "off"
}

/** FR-NTF-01: asks for permission, subscribes this browser and registers the endpoint with the server. */
export async function enablePush(): Promise<PushState> {
  if (!supported()) return "unsupported"
  const permission = await Notification.requestPermission()
  if (permission !== "granted") return "denied"
  const reg = await registration()
  if (!reg) throw new Error("SERVICE_WORKER_MISSING")
  const { publicKey } = await unwrap(api.GET("/api/v1/notifications/vapid-key"))
  if (!publicKey) throw new Error("PUSH_NOT_CONFIGURED")
  const subscription = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey),
  }))
  const json = subscription.toJSON()
  await unwrap(api.POST("/api/v1/notifications/subscriptions", {
    body: { endpoint: subscription.endpoint, p256dh: json.keys?.p256dh ?? "", auth: json.keys?.auth ?? "" },
  }))
  return "on"
}

export async function disablePush(): Promise<PushState> {
  const subscription = await (await registration())?.pushManager.getSubscription()
  if (!subscription) return "off"
  await unwrap(api.DELETE("/api/v1/notifications/subscriptions", { body: { endpoint: subscription.endpoint } })).catch(() => undefined)
  await subscription.unsubscribe()
  return "off"
}

export async function sendTestPush(): Promise<number> {
  const result = await unwrap(api.POST("/api/v1/notifications/test"))
  return result.sent ?? 0
}

/** iOS only delivers push to an installed app; the settings copy says so. */
export function isIosBrowser(): boolean {
  return /iPhone|iPad|iPod/.test(navigator.userAgent) && !window.matchMedia("(display-mode: standalone)").matches
}
