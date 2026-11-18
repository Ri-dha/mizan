# ADR obs-1 — Logstash-shaped JSON logs into Elasticsearch, viewed in Kibana

- Date: 2026-09-15
- Status: accepted
- Task: T-observability

## Context
The owner wanted logs of everything plus statistics and performance, with a specific flat JSON
line shape including correlation, trace and user ids. Filebeat and Metricbeat images failed to
pull from the registry at the time; Logstash, Elasticsearch and Kibana 9.3.3 were available.

## Decision
Spring Boot's structured Logstash encoder, Brave tracing for ids, a request filter for method,
path, status and duration, and `logback-spring.xml` per profile. Logstash ships API lines to
`mizan-logs-*` with a fixed template and all other containers to `containers-logs-*`; Micrometer
pushes metrics to `mizan-metrics-*`; a setup container imports the Kibana dashboard.

## Consequences
One correlation id finds every line of a request. Foreign container fields cannot break the
mapping. The stack needs about 3 GB of RAM; Metricbeat is optional. Security is off, so Kibana
and Elasticsearch bind to localhost only.
