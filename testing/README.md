# API Testing Frontend

Zero-build static UI covering all **25 endpoints**. No npm, no framework — just open it served over HTTP.

## Run

1. Start backend:
   ```sh
   ./mvnw spring-boot:run
   # health check: http://localhost:8080/actuator/health
   ```
2. Serve this folder (must be `http`, not `file://`, for CORS + fetch):
   ```sh
   npx serve testing
   # or VS Code "Live Server" (port 5500)
   ```
3. Open the served URL, confirm **Base URL** = `http://localhost:8080`.
4. **Register** (creates EMPLOYEE + returns JWT, auto-stored) → **me** → test away.

CORS is configured in `src/.../config/CorsConfig.java` (+ `cors()` enabled in `SecurityConfig`) for
`http://localhost:5500`, `http://127.0.0.1:5500`, `http://localhost:3000`.

## Role matrix

| Endpoints | Required role |
|---|---|
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | public |
| `GET /api/v1/auth/me` | any valid token |
| `GET /api/v1/employees/**` | EMPLOYEE \| ADMIN \| APPROVER |
| `POST/PUT /api/v1/employees/**` | ADMIN |
| `/api/v1/policies/**`, `/api/v1/quotas/**` | ADMIN |
| `/api/v1/approvals/**` | APPROVER \| ADMIN |
| `/api/v1/bookings/**` | EMPLOYEE \| ADMIN \| APPROVER |

`register` only creates EMPLOYEE users. To test ADMIN-only endpoints (quotas/policies/employee write)
and APPROVER endpoints, use the dev bootstrap: set `APP_BOOTSTRAP_ENABLED=true` plus
`APP_BOOTSTRAP_ADMIN_USERNAME`/`APP_BOOTSTRAP_ADMIN_PASSWORD` (and the
`APP_BOOTSTRAP_APPROVER_*` pair for an approver), restart the backend, then log in
with those credentials. See the root `README.md` demo section. The bootstrap is
disabled by default and must never be enabled in production.

Non-ADMIN callers must omit `employeeId` in booking bodies (server uses the token principal);
ADMIN may pass `employeeId` to book on behalf of someone.

## Demo scenarios (top buttons)

- **Good → ALLOW**: `2026-10-11 09:00 LOGIN`, route OTHER, inside shift, quota free, before cut-off.
- **Bad → DENY (NIGHT_SAFETY)**: `2026-10-11 23:30 LOGOUT`, route FIRST_PICKUP, no escort. For the
  `NIGHT_SAFETY` reason to fire, the logged-in user must be registered with gender WOMAN.

Evaluate shows `decision`, `decidingReason`, `failureReasons`, and per-rule `ruleResults` in the
response pane, with the verdict highlighted.

## Notes

- Token + base URL persist in `localStorage`; **Logout** clears the token.
- Created booking/employee/quota/policy ids auto-fill the get/update/cancel/explain forms.
- Booking list: only non-empty filters are sent (`employeeId`, `date`, `status`, `page`, `size`, `sort`).
- Policy `parameters` is a free JSON textarea (e.g. `{"cutoffMinutesBefore":30}`); invalid JSON is
  rejected client-side with a toast.
- 401 toast = missing/expired token; 403 toast = wrong role for the endpoint.
