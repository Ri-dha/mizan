# ADR foundation-1 — Spring Boot API with an offline-first React PWA, PostgreSQL and MinIO

- Date: 2026-09-13
- Status: accepted
- Task: T-m0-m1

## Context
The BRD asks for budgeting and net worth on phones in Iraq, where connectivity is uneven and app
store distribution is not a given. The owner chose React with neobrutalism.dev components, a PWA
that works offline and syncs later, Java with Spring Boot, and MinIO rather than S3. Node, managed
backends (Supabase, Firebase) and native apps were the alternatives.

## Decision
A Java 25 / Spring Boot 4.1 API over PostgreSQL 16 with Flyway, MinIO through the S3 SDK for files,
and a React 19 PWA built with Vite that keeps its own database in IndexedDB (Dexie) and syncs.
Everything runs in Docker Compose on one host.

## Consequences
The app installs from the browser and works without a signal. Every feature is built twice where
it touches data (server table and local store) and domain maths exists in two languages, which is
why shared test vectors are mandatory. A native app later reuses the API and the vectors.
