#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-demo-$(date +%s)@example.com}"
PASSWORD="${PASSWORD:-Pw123456!}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin1234!}"

curl -s "$BASE_URL/api/v1/health"
echo

curl -s -XPOST "$BASE_URL/api/v1/auth/signup" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"name\":\"Demo User\"}"
echo

TOKEN=$(curl -s -XPOST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

curl -s -XPOST "$BASE_URL/api/v1/chats" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"첫 번째 질문입니다."}'
echo

curl -s "$BASE_URL/api/v1/chats?page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
echo

curl -sN -XPOST "$BASE_URL/api/v1/chats" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: text/event-stream' \
  -H 'Content-Type: application/json' \
  -d '{"question":"스트리밍으로 짧게 답변해주세요.","isStreaming":true}'
echo

ADMIN_TOKEN=$(curl -s -XPOST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

if [[ -n "$ADMIN_TOKEN" ]]; then
  curl -s "$BASE_URL/api/v1/admin/activity" \
    -H "Authorization: Bearer $ADMIN_TOKEN"
  echo

  curl -s "$BASE_URL/api/v1/admin/reports/chats.csv" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | head -5
fi
