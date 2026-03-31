# JMeter — smoke test plans

## Plans

| File | Description |
|------|-------------|
| `admin-search-smoke.jmx` | Sign-in as `admin`, then `POST /api/admin/transactions/search` |
| `user-bankaccount-smoke.jmx` | **User accounts**: sign-up → sign-in → `GET /me`. **Bank accounts**: admin sign-in → create EUR account → `GET /accounts/me` → `GET /accounts` (paginated) → `POST /transfer` (self-transfer, expects 400) → `GET /balance-sheet` |
| `currency-country-exchangerate-smoke.jmx` | **Currencies**: admin create → list → get-by-code → delete. **Countries**: admin create → list → get-by-code → delete. **Exchange rates**: set EUR→RON → list all → get rate → update → health check |

## Before running

1. Start MongoDB (e.g. `docker compose up -d`) and set **`ADMIN_PASSWORD`** in the environment so the Spring app can create the default admin user (see `application.properties` / `docker-compose.yml`).
2. Run `init-mongo.js` to seed currencies, countries, and exchange rates.
3. Start the API: `./gradlew bootRun` (port **8080** by default).
4. Open the plan in JMeter and set **User Defined Variables** → `ADMIN_PASSWORD` to the **same** value as in step 1 (replace `change-me`).

## Run

- GUI: open the `.jmx` file, then Run.
- CLI:
  ```bash
  # Admin search smoke
  jmeter -n -t admin-search-smoke.jmx

  # User & bank-account smoke
  jmeter -n -t user-bankaccount-smoke.jmx

  # Currency, country & exchange-rate smoke
  jmeter -n -t currency-country-exchangerate-smoke.jmx
  ```

## Notes

- Rate limiting applies to `/api/auth/**` (20 req/min per IP); keep thread count modest for local runs.
- The user-accounts thread group creates unique users per thread via a BeanShell preprocessor that generates a UUID-based username (`jms_<uuid>`).
- The bank-accounts thread group signs in as `admin` once (OnceOnlyController) then creates/queries accounts across loops.
- Do not commit real passwords; use local env vars only.
