# JMeter — admin search smoke

Plan: `admin-search-smoke.jmx` (sign-in as `admin`, then `POST /api/admin/transactions/search`).

## Before running

1. Start MongoDB (e.g. `docker compose up -d`) and set **`ADMIN_PASSWORD`** in the environment so the Spring app can create the default admin user (see `application.properties` / `docker-compose.yml`).
2. Start the API: `./gradlew bootRun` (port **8080** by default).
3. Open the plan in JMeter and set **User Defined Variables** → `ADMIN_PASSWORD` to the **same** value as in step 1 (replace `change-me`).

## Run

- GUI: open `admin-search-smoke.jmx`, then Run.
- CLI: `jmeter -n -t admin-search-smoke.jmx` (from this folder).

## Notes

- Rate limiting applies to `/api/auth/**` (20 req/min per IP); keep thread count modest for local runs.
- Do not commit real passwords; use local env vars only.
