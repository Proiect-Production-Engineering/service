#!/bin/bash
# Generate Lab 8 metric traffic. Idempotent enough for repeated runs.
set +e
BASE=http://localhost:8080
TS=$(date +%s)

echo "==> Sign up alice${TS}"
JWT1=$(curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"username\":\"alice${TS}\",\"email\":\"alice${TS}@test.com\",\"password\":\"Passw0rd1\"}")
echo "    JWT len=${#JWT1}"

echo "==> Sign up bob${TS}"
JWT2=$(curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d "{\"username\":\"bob${TS}\",\"email\":\"bob${TS}@test.com\",\"password\":\"Passw0rd1\"}")
echo "    JWT len=${#JWT2}"

echo "==> Open EUR/RO accounts for both"
A1=$(curl -s -X POST $BASE/api/accounts -H "Authorization: Bearer $JWT1" -H 'Content-Type: application/json' \
  -d '{"currencyCode":"EUR","countryCode":"RO","accountHolderName":"Alice"}')
A2=$(curl -s -X POST $BASE/api/accounts -H "Authorization: Bearer $JWT2" -H 'Content-Type: application/json' \
  -d '{"currencyCode":"EUR","countryCode":"RO","accountHolderName":"Bob"}')
ID1=$(echo "$A1" | jq -r '.id // empty')
ID2=$(echo "$A2" | jq -r '.id // empty')
echo "    ID1=$ID1 ID2=$ID2"

echo "==> Failing transfer alice -> bob (insufficient funds) -> failure counter + timer"
curl -s -o /dev/null -w "   transfer_fail status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
  -H "Authorization: Bearer $JWT1" -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ID1\",\"targetAccountId\":\"$ID2\",\"amount\":50,\"description\":\"underfunded\"}"

echo "==> Sign in as admin"
ADMIN_JWT=$(curl -s -X POST $BASE/api/auth/signin -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}')
echo "    admin JWT len=${#ADMIN_JWT}"
ADMIN_ACCT=$(curl -s -H "Authorization: Bearer $ADMIN_JWT" $BASE/api/accounts/me | jq -r '.[0].id // empty')
echo "    admin account ID=$ADMIN_ACCT"

echo "==> Successful admin -> alice transfers (success counter + timer)"
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "   transfer_ok $i status=%{http_code}\n" -X POST $BASE/api/accounts/transfer \
    -H "Authorization: Bearer $ADMIN_JWT" -H 'Content-Type: application/json' \
    -d "{\"sourceAccountId\":\"$ADMIN_ACCT\",\"targetAccountId\":\"$ID1\",\"amount\":10,\"description\":\"seed traffic $i\"}"
done

echo "==> Validation error on signup -> errors counter"
curl -s -o /dev/null -w "   bad signup status=%{http_code}\n" -X POST $BASE/api/auth/signup \
  -H 'Content-Type: application/json' -d '{"username":"x","email":"bad","password":"short"}'

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
