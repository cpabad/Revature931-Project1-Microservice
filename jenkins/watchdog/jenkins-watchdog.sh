#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Jenkins watchdog — who watches the watcher.
#
# Cron-driven (every 5 min) health check of the Jenkins controller with
# Kuma-style alerting: ONE Discord ping when Jenkins goes down, silence while
# it stays down, ONE ping when it recovers. Runs on the HOST, outside Docker
# and outside K3s, so it survives anything short of the box itself dying —
# docker's restart policy heals crashes silently; this tells you when healing
# didn't happen.
#
# Arming (out-of-band, nothing tracked): put the Discord webhook URL — and
# nothing else — in ~/.config/ers/discord-webhook (chmod 600). Until that
# file exists this script exits silently, so the cron entry can be installed
# before Jenkins or the webhook are ready.
#
# Install:  crontab -e  ->  */5 * * * * <absolute path to this script>
# ---------------------------------------------------------------------------
set -u

JENKINS_URL="http://127.0.0.1:8090/login"
WEBHOOK_FILE="${HOME}/.config/ers/discord-webhook"
STATE_DIR="${HOME}/.local/state/ers"
STATE_FILE="${STATE_DIR}/jenkins-watchdog.state"

# Not armed yet -> do nothing, say nothing.
[ -r "$WEBHOOK_FILE" ] || exit 0
WEBHOOK_URL="$(head -n1 "$WEBHOOK_FILE")"
[ -n "$WEBHOOK_URL" ] || exit 0

mkdir -p "$STATE_DIR"
LAST_STATE="$(cat "$STATE_FILE" 2>/dev/null || echo up)"

# --max-time so a hung Jenkins reads as down, not as a stuck cron job.
if curl -fsS -o /dev/null --max-time 10 "$JENKINS_URL"; then
  CURRENT=up
else
  CURRENT=down
fi

notify() {
  # $1 = message. Discord ignores extra fields; content is all we need.
  curl -fsS -o /dev/null --max-time 10 \
    -H 'Content-Type: application/json' \
    -d "{\"content\": \"$1\"}" \
    "$WEBHOOK_URL" || true   # a dead Discord must not crash the watchdog
}

if [ "$CURRENT" != "$LAST_STATE" ]; then
  if [ "$CURRENT" = down ]; then
    notify ":red_circle: **Jenkins is DOWN** — ${JENKINS_URL} unreachable from $(hostname) at $(date '+%Y-%m-%d %H:%M %Z'). No more pings until it recovers. Try: docker start ers-jenkins"
  else
    notify ":sunny: **Jenkins is back UP** — ${JENKINS_URL} responding again at $(date '+%Y-%m-%d %H:%M %Z')."
  fi
  echo "$CURRENT" > "$STATE_FILE"
fi
