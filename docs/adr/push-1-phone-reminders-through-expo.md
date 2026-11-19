# ADR push-1 — Phones get reminders through Expo's push service

- Date: 2026-11-19
- Status: accepted
- Task: T-mobile-app (Phase 6)

## Context
Reminders (bill due, pay day, close to a bucket's limit, month end, metal price moves) reach
browsers through Web Push (ADR e2e-1: devices upload only a kind and a time, the server sends fixed
wording). The phones need the same reminders through APNs on iPhone and FCM on Android. Options:
speak APNs and FCM from the server directly (two protocols, two sets of credentials to rotate in
the server's configuration), or send through Expo's push service, which takes one token format
and relays to both, with the APNs key and FCM credentials held in the EAS project.

## Decision
- `push_subscription` gains `channel` (`WEB_PUSH` | `EXPO`). An Expo subscription keeps the
  phone's push token in `endpoint` and has no `p256dh` or `auth`; a check constraint enforces the
  pairing (V027).
- `POST /api/v1/notifications/phones {token}` registers a phone, `DELETE` removes it. The same
  token registering again (a new sign-in, another household) rebinds the row instead of adding one.
- Each `PushSender` says which channel it handles: `WebPushSender` for browsers, `ExpoPushSender`
  for phones, `LoggingPushSender` for every channel in development and tests.
  `NotificationService` picks the sender by the subscription's channel.
- `ExpoPushSender` posts to `mizan.notifications.expo.url` (Expo's send endpoint by default) with
  an optional `MIZAN_EXPO_ACCESS_TOKEN`. Expo answers 200 with a ticket per message: `ok` is sent,
  `DeviceNotRegistered` is gone (the row is deleted), anything else counts as a failure.
- Messages carry the same fixed wording as Web Push and a `url` the app opens on tap. No amounts,
  names or record ids leave the server; metal price moves name only the public market move.

## Consequences
- Expo, Apple and Google see that a Mizan reminder of some kind went to a device at a time, as
  browser push services already do; they never see financial data.
- Real delivery needs an EAS project (its id in `mobile/app.json` under `extra.eas.projectId`),
  an APNs key and FCM credentials registered with it. Development and the simulator use the
  logging transport.
- Push receipts (Expo's second step, which can report a token gone hours later) are not polled
  yet; such a token is dropped by the next failed send after five failures, or the next
  `DeviceNotRegistered` ticket.
