#!/bin/sh
# Waits for Kibana, creates the data views and imports the Mizan dashboard. Safe to run again.
set -eu
KIBANA="${KIBANA_URL:-http://kibana:5601}"
until curl -sf "$KIBANA/api/status" >/dev/null; do
  echo "waiting for kibana at $KIBANA"; sleep 5
done

create_view() {
  curl -s -o /dev/null -w "data view $1: %{http_code}\n" -X POST "$KIBANA/api/data_views/data_view" \
    -H "kbn-xsrf: true" -H "Content-Type: application/json" \
    -d "{\"data_view\":{\"id\":\"$1\",\"title\":\"$2\",\"name\":\"$3\",\"timeFieldName\":\"$4\"},\"override\":true}"
}
create_view mizan-logs "mizan-logs-*" "Mizan logs" "@timestamp"
create_view mizan-metrics "mizan-metrics-*" "Mizan metrics" "@timestamp"
create_view metricbeat "metricbeat-*" "Containers" "@timestamp"
create_view containers-logs "containers-logs-*" "Container logs" "@timestamp"

curl -s -X POST "$KIBANA/api/saved_objects/_import?overwrite=true" -H "kbn-xsrf: true" \
  --form file=@/setup/mizan-dashboard.ndjson
echo
echo "Kibana ready: $KIBANA/app/dashboards"
