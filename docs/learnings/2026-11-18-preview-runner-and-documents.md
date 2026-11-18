# 2026-11-18 — Running the API from the desktop preview runner
The Claude desktop preview runner cannot execute `./mvnw` inside `~/Documents` ("Operation not
permitted"), even through `sh`. Build the jar with `./mvnw -DskipTests package` from a shell and
launch `"$(/usr/libexec/java_home -v 25)/bin/java" -jar api/target/mizan-api-*.jar` with
`--spring.docker.compose.enabled=false --spring.datasource.url=...` from the session-root
`.claude/launch.json`. `npm run dev` works there as is.
Source: T-e2e-encryption
