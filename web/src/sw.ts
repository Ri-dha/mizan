/// <reference lib="webworker" />
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from "workbox-precaching"
import { NavigationRoute, registerRoute } from "workbox-routing"
import { NetworkOnly } from "workbox-strategies"

declare let self: ServiceWorkerGlobalScope

interface PushPayload {
  kind: string
  title: string
  body: string
  url: string
}

precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

registerRoute(({ url }) => url.pathname.startsWith("/api/"), new NetworkOnly())
registerRoute(new NavigationRoute(createHandlerBoundToURL("index.html"), { denylist: [/^\/api\//, /^\/docs\//] }))

self.addEventListener("message", (event) => {
  if (event.data?.type === "SKIP_WAITING") void self.skipWaiting()
})

/** FR-NTF-01: the server sends the message already worded in the member's language. */
self.addEventListener("push", (event) => {
  if (!event.data) return
  const payload = event.data.json() as PushPayload
  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      icon: "/icons/icon-192.png",
      badge: "/icons/icon-192.png",
      tag: payload.kind,
      data: { url: payload.url },
    }),
  )
})

self.addEventListener("notificationclick", (event) => {
  event.notification.close()
  const url = new URL((event.notification.data as { url?: string } | undefined)?.url ?? "/", self.location.origin).href
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clients) => {
      const open = clients.find((client) => "focus" in client)
      if (open) {
        void open.navigate(url)
        return open.focus()
      }
      return self.clients.openWindow(url)
    }),
  )
})
