# 2026-11-19 — running the API locally
`.env` is read only by Docker Compose. `./mvnw spring-boot:run` starts without it, so keyed feeds
are off: with no `MIZAN_IQWEALTH_API_KEY` the Baghdad market dollar (`USDIQD_PARALLEL`) falls back to
the `stub` source and never changes. Export only the keys you need from `.env` before running (not
the whole file: it sets `SPRING_PROFILES_ACTIVE` and Docker hostnames). Check with
`select source from price_quote where instrument = 'USDIQD_PARALLEL'`.
Source: T-mobile-app, owner report that the dollar was not updating
