# 2026-09-15 — Tests must not write the tailed log file
Logstash tails `api/logs/mizan.json`; tests writing there flood Kibana. The `test` profile in
`logback-spring.xml` logs plainly to the console only.
Source: T-observability
