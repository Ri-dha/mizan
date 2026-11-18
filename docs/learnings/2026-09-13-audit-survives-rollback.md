# 2026-09-13 — Writes that must survive a failing request
A 401 rolls back the request transaction, so failed-login counters and lockouts written in it
vanish. Record them in a separate `REQUIRES_NEW` bean (`LoginAttemptRecorder`,
`AuditService.recordNow`).
Source: T-m0-m1
