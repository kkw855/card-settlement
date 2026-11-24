#!/usr/bin/env bash
# 승인 API 스모크 테스트. 실제로 띄운 서버에 curl로 요청하고 응답 코드와 결과를 확인한다.
#
# 준비 (터미널 3개):
#   docker compose up -d
#   ./gradlew :simulator:run
#   ./gradlew :online-api:bootRun     # local 프로필: 가맹점 M000000001을 넣는다
# 실행:
#   scripts/smoke-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

command -v jq > /dev/null || { echo "jq가 필요하다 (brew install jq)"; exit 1; }
API_KEY=${LOCAL_MERCHANT_API_KEY:-$(sed -n 's/^LOCAL_MERCHANT_API_KEY=//p' .env)}
[[ -n $API_KEY ]] || { echo ".env에 LOCAL_MERCHANT_API_KEY가 없다"; exit 1; }

BASE_URL=${BASE_URL:-http://localhost:8080}
RUN=$(uuidgen)   # 멱등 키는 DB에 영구 보관되므로 실행마다 새로 만든다
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
FAILED=0

# approve <멱등 키> <금액> <할부 개월> [API 키] -> $STATUS, $TMP/body, $TMP/headers
approve() {
  STATUS=$(curl -sS -o "$TMP/body" -D "$TMP/headers" -w '%{http_code}' -X POST "$BASE_URL/v1/payments" \
  -H "X-Api-Key: ${4:-$API_KEY}" -H "Idempotency-Key: $1" -H 'Content-Type: application/json' \
  -d "{\"cardNumber\":\"4111111111111111\",\"cardExpiry\":\"2812\",\"installmentMonths\":$3,\"amount\":$2}")
}
# get <txnId> -> $STATUS, $TMP/body
get() {
  STATUS=$(curl -sS -o "$TMP/body" -w '%{http_code}' -H "X-Api-Key: $API_KEY" "$BASE_URL/v1/payments/$1")
}
# expect <설명> <HTTP 상태> <본문 조건 (jq)>
expect() {
  if [[ $STATUS == "$2" ]] && jq -e "$3" "$TMP/body" > /dev/null 2>&1; then
    echo "ok     $1"
  else
    echo "FAIL   $1 -> HTTP $STATUS $(cat "$TMP/body")"
    FAILED=1
  fi
}

approve "$RUN-1" 10000 0
expect "승인: 201 APPROVED" 201 '.status == "APPROVED" and .result.code == "0000"'
TXN_ID=$(jq -r .txnId "$TMP/body")
approve "$RUN-1" 10000 0
expect "같은 키로 다시: 처음 결과를 재현" 201 ".txnId == \"$TXN_ID\" and .status == \"APPROVED\""
grep -qi '^idempotent-replayed: true' "$TMP/headers" || { echo "FAIL   Idempotent-Replayed 헤더가 없다"; FAILED=1; }
get "$TXN_ID"
expect "조회: 200, 카드번호는 마스킹" 200 '.status == "APPROVED" and .cardMasked == "411111******1111"'
approve "$RUN-2" 10001 0
expect "끝자리 001: 카드사 거절도 201" 201 '.status == "DECLINED" and .result.code == "1001"'
approve "$RUN-3" 10009 0
expect "끝자리 009: 응답 지연 -> 망취소" 201 '.status == "NET_CANCELLED" and .result.code == "P002"'
approve "$RUN-4" 10000 0 "wrong-key"
expect "틀린 API 키: 401" 401 '.code == "UNAUTHORIZED"'
approve "$RUN-5" 10000.5 0
expect "소수 금액: 400" 400 '.code == "VALIDATION_FAILED"'
approve "$RUN-6" 10000 3
expect "5만 원 미만 할부: 422" 422 '.code == "INSTALLMENT_NOT_ALLOWED"'
approve "$RUN-1" 20000 0
expect "같은 키에 다른 금액: 422" 422 '.code == "IDEMPOTENCY_KEY_REUSED"'
exit $FAILED
