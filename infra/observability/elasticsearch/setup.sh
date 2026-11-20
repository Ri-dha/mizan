#!/bin/sh
# ADR obs-2: every index Mizan writes is deleted after its retention period. Creates the
# lifecycle policies and the index templates, and attaches the policies to indexes that predate
# them. Runs before Filebeat starts; safe to run again.
set -eu
ES="${ELASTICSEARCH_URL:-http://elasticsearch:9200}"
until curl -sf "$ES/_cluster/health" >/dev/null; do
  echo "waiting for elasticsearch at $ES"; sleep 5
done

put() {
  curl -sf -o /dev/null -w "$1: %{http_code}\n" -X PUT "$ES/$1" -H "Content-Type: application/json" -d "$2"
}

policy() {
  put "_ilm/policy/$1" "{\"policy\":{\"phases\":{\"hot\":{\"actions\":{}},\"delete\":{\"min_age\":\"${2}d\",\"actions\":{\"delete\":{}}}}}}"
}
policy mizan-logs-retention "${LOG_RETENTION_DAYS:-14}"
policy containers-logs-retention "${CONTAINER_LOG_RETENTION_DAYS:-7}"
policy metrics-retention "${METRICS_RETENTION_DAYS:-7}"

# The log index templates (field types, compression, lifecycle). Filebeat ships into these
# indexes but leaves templates to this script (ADR obs-4).
for name in mizan-logs containers-logs; do
  curl -sf -o /dev/null -w "_index_template/$name: %{http_code}\n" -X PUT "$ES/_index_template/$name" \
    -H "Content-Type: application/json" --data-binary "@/setup/$name-template.json"
done

# Metrics moved to VictoriaMetrics (ADR obs-5); metric indexes written before then still age out.
curl -s -o /dev/null -X DELETE "$ES/_index_template/mizan-metrics"

attach() {
  curl -s -o /dev/null -w "existing $1: %{http_code}\n" -X PUT "$ES/$1/_settings?allow_no_indices=true&expand_wildcards=open" \
    -H "Content-Type: application/json" -d "{\"index.lifecycle.name\":\"$2\"}"
}
attach "mizan-logs-*" mizan-logs-retention
attach "containers-logs-*" containers-logs-retention
attach "mizan-metrics-*" metrics-retention
attach "metricbeat-*" metrics-retention
echo "retention ready"
