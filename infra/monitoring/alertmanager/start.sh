#!/bin/sh
# ADR obs-5: Alertmanager reads no environment, so its config is written here at start. With a
# Telegram bot token and chat id set, alerts go there; without, they only show in Grafana.
set -eu
CONFIG=/tmp/alertmanager.yml
if [ -n "${TELEGRAM_BOT_TOKEN:-}" ] && [ -n "${TELEGRAM_CHAT_ID:-}" ]; then
  printf '%s' "$TELEGRAM_BOT_TOKEN" > /tmp/telegram-token
  cat > "$CONFIG" <<CONF
route:
  receiver: telegram
  group_by: [alertname]
  group_wait: 30s
  repeat_interval: 4h
receivers:
  - name: telegram
    telegram_configs:
      - bot_token_file: /tmp/telegram-token
        chat_id: $TELEGRAM_CHAT_ID
        send_resolved: true
CONF
else
  cat > "$CONFIG" <<CONF
route:
  receiver: none
receivers:
  - name: none
CONF
fi
exec /bin/alertmanager --config.file="$CONFIG" --storage.path=/alertmanager
