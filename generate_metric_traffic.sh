#!/bin/bash
# Generate Lab 8 metric traffic.
#
# Goals:
#   * Plenty of *real* (successful) transfers so app_transfers_total{status="success"}
#     and the app_transfer_duration_seconds histogram have meaningful samples.
#   * A handful of intentional failures so app_transfers_total{status="failure"}
#     and app_errors_total still increment (insufficient funds, bad signup,
#     currency mismatch).
#   * Idempotent enough for repeated runs (each invocation uses a fresh ${TS}).
#
# Pre-reqs (already satisfied by init-mongo.js):
#   * admin user exists
#   * admin has a seeded EUR account with 1,000,000 EUR
#   * admin has a seeded RON account with    50,000 RON
set +e
BASE=http://localhost:8080
TS=$(date +%s)

# ---------------------------------------------------------------------------
# Users
# ---------------------------------------------------------------------------
echo "==> Sign up alice${TS}"
JWT1=$(curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"username\":\"alice${TS}\",\"email\":\"alice${TS}@test.com\",\"password\":\"Passw0rd1\"}")
echo "    JWT len=${#JWT1}"

echo "==> Sign up bob${TS}"
JWT2=$(curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"username\":\"bob${TS}\",\"email\":\"bob${TS}@test.com\",\"password\":\"Passw0rd1\"}")
echo "    JWT len=${#JWT2}"

echo "==> Sign in as admin"
ADMIN_JWT=$(curl -s -X POST $BASE/api/auth/signin -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}')
echo "    admin JWT len=${#ADMIN_JWT}"

# ---------------------------------------------------------------------------
# Accounts
# ---------------------------------------------------------------------------
echo "==> Open EUR/RO accounts for alice & bob"
A1=$(curl -s -X POST $BASE/api/accounts -H "Authorization: Bearer $JWT1" -H 'Content-Type: application/json' \
  -d '{"currencyCode":"EUR","countryCode":"RO","accountHolderName":"Alice"}')
A2=$(curl -s -X POST $BASE/api/accounts -H "Authorization: Bearer $JWT2" -H 'Content-Type: application/json' \
  -d '{"currencyCode":"EUR","countryCode":"RO","accountHolderName":"Bob"}')
ALICE_EUR_ID=$(echo "$A1" | jq -r '.id // empty')
BOB_EUR_ID=$(echo "$A2"   | jq -r '.id // empty')
echo "    ALICE_EUR_ID=$ALICE_EUR_ID  BOB_EUR_ID=$BOB_EUR_ID"

echo "==> Open RON/RO account for alice"
ALICE_RON_RESP=$(curl -s -X POST $BASE/api/accounts -H "Authorization: Bearer $JWT1" -H 'Content-Type: application/json' \
  -d '{"currencyCode":"RON","countryCode":"RO","accountHolderName":"Alice RON"}')
ALICE_RON_ID=$(echo "$ALICE_RON_RESP" | jq -r '.id // empty')
echo "    ALICE_RON_ID=$ALICE_RON_ID"

echo "==> Fetch admin EUR & RON accounts (seeded by init-mongo.js)"
ADMIN_ACCTS=$(curl -s -H "Authorization: Bearer $ADMIN_JWT" $BASE/api/accounts/me)
ADMIN_EUR_ID=$(echo "$ADMIN_ACCTS" | jq -r '[.[] | select(.currencyCode=="EUR")] | .[0].id // empty')
ADMIN_RON_ID=$(echo "$ADMIN_ACCTS" | jq -r '[.[] | select(.currencyCode=="RON")] | .[0].id // empty')
echo "    ADMIN_EUR_ID=$ADMIN_EUR_ID  ADMIN_RON_ID=$ADMIN_RON_ID"

# ---------------------------------------------------------------------------
# Intentional FAILURE 1: alice tries to transfer EUR to bob with no balance.
# Hits IllegalArgumentException ("Insufficient funds") -> failure counter +
# timer + app_errors_total.
# ---------------------------------------------------------------------------
echo "==> [fail] alice -> bob EUR with no funds (expect 400)"
curl -s -o /dev/null -w "   transfer_fail status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
  -H "Authorization: Bearer $JWT1" -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ALICE_EUR_ID\",\"targetAccountId\":\"$BOB_EUR_ID\",\"amount\":50,\"description\":\"underfunded\"}"

# ---------------------------------------------------------------------------
# Real funding: admin EUR -> alice EUR and admin EUR -> bob EUR.
# These both succeed and feed the success counter + transfer-duration timer.
# ---------------------------------------------------------------------------
echo "==> [ok] admin EUR -> alice EUR (1000)"
curl -s -o /dev/null -w "   fund_alice status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
  -H "Authorization: Bearer $ADMIN_JWT" -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ADMIN_EUR_ID\",\"targetAccountId\":\"$ALICE_EUR_ID\",\"amount\":1000,\"description\":\"fund alice EUR\"}"

echo "==> [ok] admin EUR -> bob EUR (500)"
curl -s -o /dev/null -w "   fund_bob   status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
  -H "Authorization: Bearer $ADMIN_JWT" -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ADMIN_EUR_ID\",\"targetAccountId\":\"$BOB_EUR_ID\",\"amount\":500,\"description\":\"fund bob EUR\"}"

# ---------------------------------------------------------------------------
# Loop of real transfers: alice EUR -> bob EUR.
# Each iteration succeeds (alice has 1000 EUR, transfers 50 each time).
# ---------------------------------------------------------------------------
echo "==> [ok] alice EUR -> bob EUR x5 (50 each)"
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "   alice_to_bob $i status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
    -H "Authorization: Bearer $JWT1" -H 'Content-Type: application/json' \
    -d "{\"sourceAccountId\":\"$ALICE_EUR_ID\",\"targetAccountId\":\"$BOB_EUR_ID\",\"amount\":50,\"description\":\"alice->bob #$i\"}"
done

# ---------------------------------------------------------------------------
# Loop of real transfers: admin RON -> alice RON.
# Exercises a different currency path through the timer/success counter.
# ---------------------------------------------------------------------------
echo "==> [ok] admin RON -> alice RON x5 (10 each)"
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "   admin_to_alice_ron $i status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
    -H "Authorization: Bearer $ADMIN_JWT" -H 'Content-Type: application/json' \
    -d "{\"sourceAccountId\":\"$ADMIN_RON_ID\",\"targetAccountId\":\"$ALICE_RON_ID\",\"amount\":10,\"description\":\"admin->alice RON #$i\"}"
done

# ---------------------------------------------------------------------------
# Intentional FAILURE 2: validation error on signup.
# Hits MethodArgumentNotValidException -> app_errors_total.
# ---------------------------------------------------------------------------
echo "==> [fail] bad signup (validation error, expect 400)"
curl -s -o /dev/null -w "   bad_signup status=%{http_code}\n" -X POST $BASE/api/auth/signup \
  -H 'Content-Type: application/json' -d '{"username":"x","email":"bad","password":"short"}'

# ---------------------------------------------------------------------------
# Intentional FAILURE 3: currency mismatch transfer (admin EUR -> alice RON).
# Hits IllegalArgumentException ("same currency") -> failure counter + timer.
# ---------------------------------------------------------------------------
echo "==> [fail] admin EUR -> alice RON currency mismatch (expect 400)"
curl -s -o /dev/null -w "   currency_mismatch status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
  -H "Authorization: Bearer $ADMIN_JWT" -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ADMIN_EUR_ID\",\"targetAccountId\":\"$ALICE_RON_ID\",\"amount\":5,\"description\":\"mismatch\"}"

# ---------------------------------------------------------------------------
# Read traffic to populate http_server_requests.
# ---------------------------------------------------------------------------
echo "==> 10x reads to populate http_server_requests"
for i in $(seq 1 10); do
  curl -s -o /dev/null $BASE/api/health
  curl -s -o /dev/null -H "Authorization: Bearer $JWT1" $BASE/api/accounts/me
  curl -s -o /dev/null -H "Authorization: Bearer $JWT1" $BASE/api/users/me
  curl -s -o /dev/null -H "Authorization: Bearer $ADMIN_JWT" $BASE/api/users
done

echo
echo "=== Custom metrics now ==="
curl -s $BASE/actuator/prometheus | grep -E '^app_' | sort
