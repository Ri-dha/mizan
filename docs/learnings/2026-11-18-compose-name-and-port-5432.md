# 2026-11-18 — Compose project name and port clashes
Docker names volumes after the folder unless `name:` is set; it is pinned to `mizan` in
`docker-compose.yml`. Postgres binds 5432 on localhost, which the owner's tariffcalc project also
uses; stop that project's database before running Mizan's dev stack.
Source: T-relocate
