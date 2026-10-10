# Smart Booking Eligibility & Policy Rule Engine

A Spring Boot REST API (MoveInSync backend case study) that evaluates an employee's
office cab/shuttle booking against an ordered, configurable set of policy rules and
returns exactly one decision: `ALLOW`, `DENY`, or `NEEDS_APPROVAL`. Every response
explains which rules ran, in what order, and why each passed, failed, or was skipped.

## Requirements implemented

- Five rules: shift window, booking cutoff, duplicate booking, cost-centre quota,
  gender night-safety.
- Deterministic rule engine with explicit precedence (hard-deny beats soft-failure
  beats pass) and ordered explain output.
- Advisory evaluation (`POST /api/v1/bookings/evaluate`) with zero quota side effects.
- Transactional booking creation with atomic conditional-update seat reservation.
- Idempotent cancellation that releases exactly one seat; approval flow that
  re-evaluates and reserves on approve; rejection as a distinct terminal status.
- Policy CRUD + enable/disable + priority changes that take effect on the next
  evaluation without restart.
- JWT auth with three roles (`EMPLOYEE`, `ADMIN`, `APPROVER`) and server-side
  ownership checks (the principal's linked employee decides identity, never a request id).
- Consistent `ApiError` schema, Bean Validation, safe 500s, request-id correlation.
- Flyway migrations (no Hibernate auto-DDL), PostgreSQL everywhere, Testcontainers
  integration tests including the concurrent last-seat race.
- Actuator health + Micrometer counters (`booking.evaluations`, `booking.created`,
  `booking.cancelled`, `booking.approvals`, `booking.rule_violations`).

## Architecture and packages

Controller → service → repository layering. DTOs (records) cross the API boundary;
JPA entities never leak. Business logic lives in services and small rule strategy
classes, not controllers.

```text
com.moveinsync.smart_booking_engine/
├── controller/   Auth, Employee, Booking, Policy, Quota, Approval
├── dto/          request/ + response/ + common/ (records, Bean Validation)
├── entity/       Employee, Booking, CostCentreQuota, PolicyRule, AppUser
├── enums/        Decision, BookingStatus, BookingDirection, RuleType, RuleOutcome,
│                 ReasonCode, Gender, Role, RoutePosition
├── exception/    ApiError, GlobalExceptionHandler, typed 4xx exceptions
├── repository/   Spring Data JPA + conditional-update quota methods
├── security/     JWT filter/service, user details, request-id filter
├── service/      Auth, Employee, Policy, Quota, Booking, Approval, AdminBootstrap
│   └── rule/     BookingRule strategy, RuleContext, RuleResult, RuleEngine + 5 rules
└── config/       SecurityConfig, ClockConfig
```

## Rule engine and precedence

`BookingRule` is the Strategy interface — one `@Component` per `RuleType`.
`RuleEngine.evaluate` loads enabled rules ordered by
(priority, ruleType, id), filters by scope (null scope = wildcard), runs every
applicable rule, then decides:

1. Every applicable rule runs, so the explanation shows the whole stack.
2. A failing hard-deny rule forces `DENY`, regardless of priority.
3. Otherwise a failing soft rule (`hardDeny=false`) yields `NEEDS_APPROVAL`.
4. Otherwise `ALLOW`.
5. `decidingReason` is the first failure in evaluation order within the winning
   category; `failureReasons` lists every distinct failing code in that order.

Complexity: sorting is O(R log R), evaluation is O(R) plus per-rule DB lookups
(R = applicable rules). Explanation storage is O(R). A stored snapshot
(`rule_results_json`) travels with each booking, so the original explanation
survives later policy changes.

## Database

PostgreSQL + Flyway. `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never
touches the schema.

```mermaid
erDiagram
    EMPLOYEE ||--o{ APP_USER : "linked profile"
    EMPLOYEE ||--o{ BOOKING : "owns"
    EMPLOYEE }o--|| COST_CENTRE_QUOTA : "costCentreCode"
    EMPLOYEE }o--o{ POLICY_RULE : "businessUnit + costCentre scope"
    APP_USER { uuid id PK uuid employee_id FK string username UK string password_hash string role }
    EMPLOYEE { uuid id PK string employee_code UK string business_unit string cost_centre_code string gender time shift_start time shift_end bool active }
    BOOKING { uuid id PK uuid employee_id FK date booking_date string direction timestamp requested_time string route_position string status string decision string deciding_reason text rule_results_json }
    COST_CENTRE_QUOTA { uuid id PK string cost_centre_code UK int capacity int reserved_seats }
    POLICY_RULE { uuid id PK string business_unit string cost_centre_code string rule_type bool enabled int priority bool hard_deny text parameters_json }
```

Key guards:

- Partial unique index `uq_booking_active_slot (employee, requested_time, direction)
  WHERE status IN ('CONFIRMED','PENDING_APPROVAL')` — the real duplicate protection.
- Conditional `UPDATE ... SET reserved_seats = reserved_seats + 1
  WHERE cost_centre_code = ? AND reserved_seats < capacity` — the real quota
  protection (returns 1 on success, 0 when full). No read-then-write anywhere.
- Matching conditional release guarded on `reserved_seats > 0` keeps
  `0 <= reservedSeats <= capacity` plus a CHECK constraint.

All business times are wall-clock in `app.company-time-zone` (default
`Asia/Kolkata`). `requestedTime` is the service date-time and must fall on
`bookingDate`.

## Setup

### Local PostgreSQL

```powershell
createdb smart_booking
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/smart_booking"
$env:DATABASE_USERNAME="postgres"
$env:DATABASE_PASSWORD="secret"
.\mvnw.cmd spring-boot:run
```

Unix: `./mvnw spring-boot:run` with the same variables exported.

### Neon

Neon usually gives a `postgresql://user:password@host/db?sslmode=require` URI.
Convert it to JDBC form:

```text
jdbc:postgresql://<host>/<db>?sslmode=require
```

then set `DATABASE_URL` to that value and `DATABASE_USERNAME`/`DATABASE_PASSWORD`
separately. Keep `spring.jpa.hibernate.ddl-auto=validate` — run the app once and
Flyway applies V1 + V2 automatically.

### Tests

```powershell
.\mvnw.cmd test        # needs Docker for the Testcontainers PostgreSQL
```

Without Docker, integration tests (`*IT`) cannot run; unit tests still run with
`.\mvnw.cmd -Dtest='!*IT' test`. Test reports land in `target/surefire-reports/`.

## API

Auth: `Authorization: Bearer <jwt>`. Every response carries `X-Request-Id`.

| Method & path | Role | Purpose |
|---|---|---|
| POST /api/v1/auth/register | public | Self-register an EMPLOYEE (+ employee profile) |
| POST /api/v1/auth/login | public | Get a JWT |
| GET /api/v1/auth/me | any | Own identity |
| POST /api/v1/employees | ADMIN | Create employee |
| GET /api/v1/employees/{id} | ADMIN or self | Read profile |
| GET /api/v1/employees | ADMIN (full) / EMPLOYEE (own only) | List |
| PUT /api/v1/employees/{id} | ADMIN | Update |
| POST /api/v1/bookings/evaluate?explain=true | EMPLOYEE+ | Advisory evaluation, no side effects |
| POST /api/v1/bookings | EMPLOYEE+ | Evaluate + create (409 body is the evaluation on deny) |
| GET /api/v1/bookings?employeeId=&date=&status= | ADMIN/APPROVER (any) / EMPLOYEE (own) | Search, paged |
| GET /api/v1/bookings/{id} | owner/ADMIN/APPROVER | Read |
| POST /api/v1/bookings/{id}/cancel | owner/ADMIN | Idempotent cancel, releases one seat |
| GET /api/v1/bookings/{id}/explanation | owner/ADMIN/APPROVER | Stored snapshot |
| GET/POST /api/v1/policies | ADMIN | List / create |
| GET/PUT /api/v1/policies/{id} | ADMIN | Read / full update |
| PATCH /api/v1/policies/{id}/enabled | ADMIN | Enable/disable |
| PATCH /api/v1/policies/{id}/priority | ADMIN | Reorder |
| DELETE /api/v1/policies/{id} | ADMIN | Delete (disable is the preferred soft-off) |
| GET/POST /api/v1/quotas | ADMIN | List / create |
| PUT /api/v1/quotas/{id} | ADMIN | Update capacity (+ optional admin seat correction) |
| GET /api/v1/approvals/pending | APPROVER/ADMIN | Pending queue, paged |
| POST /api/v1/approvals/{id}/approve | APPROVER/ADMIN | Re-evaluate + reserve + confirm |
| POST /api/v1/approvals/{id}/reject | APPROVER/ADMIN | Mark REJECTED (no seat) |

Role matrix: EMPLOYEE evaluates/books/cancels own bookings only. ADMIN manages
employees, quotas, policies, and can cancel any booking. APPROVER sees the pending
queue and approves/rejects; ADMIN can do approver operations too. Unlisted routes
are denied by default.

## Demo flow

```bash
BASE=http://localhost:8080

# 1. Register (creates EMPLOYEE + profile)
curl -s -X POST $BASE/api/v1/auth/register -H 'Content-Type: application/json' -d '{
  "username":"priya","password":"password123","employeeCode":"E-101",
  "fullName":"Priya Sharma","businessUnit":"BU-BLR","costCentreCode":"CC-BLR-01",
  "gender":"WOMAN","shiftStart":"09:00","shiftEnd":"18:00"}' | tee /tmp/reg.json
TOKEN=$(python3 -c "import json;print(json.load(open('/tmp/reg.json'))['token'])")
EMP=$(python3 -c "import json;print(json.load(open('/tmp/reg.json'))['employeeId'])")
AUTH="Authorization: Bearer $TOKEN"

# 2-3. Admin: quota + default rules
ADMIN_TOKEN=<login-as-admin-and-paste-jwt>
curl -s -X POST $BASE/api/v1/quotas -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"costCentreCode":"CC-BLR-01","capacity":40}'
curl -s $BASE/api/v1/policies -H "Authorization: Bearer $ADMIN_TOKEN"

# 5. Valid evaluation -> ALLOW
curl -s -X POST "$BASE/api/v1/bookings/evaluate?explain=true" -H "$AUTH" \
  -H 'Content-Type: application/json' -d "{
  \"employeeId\":\"$EMP\",\"bookingDate\":\"2026-10-15\",\"direction\":\"LOGIN\",
  \"pickupLocation\":\"Koramangala\",\"dropLocation\":\"Whitefield\",
  \"requestedTime\":\"2026-10-15T10:00:00\",\"routePosition\":\"OTHER\",
  \"escortAvailable\":false}"

# 6. Out-of-shift -> DENY / OUTSIDE_SHIFT_WINDOW (same body, requestedTime 22:30)
# 7. Night safety -> DENY / NIGHT_SAFETY (routePosition FIRST_PICKUP, 23:00, no escort)

# 8. Create (quota 0 -> 1) then 9. cancel (quota 1 -> 0)
curl -s -X POST $BASE/api/v1/bookings -H "$AUTH" -H 'Content-Type: application/json' -d "{
  \"bookingDate\":\"2026-10-15\",\"direction\":\"LOGIN\",
  \"pickupLocation\":\"Koramangala\",\"dropLocation\":\"Whitefield\",
  \"requestedTime\":\"2026-10-15T10:00:00\",\"routePosition\":\"OTHER\"}" | tee /tmp/booking.json
BID=$(python3 -c "import json;print(json.load(open('/tmp/booking.json'))['id'])")
curl -s -X POST $BASE/api/v1/bookings/$BID/cancel -H "$AUTH"

# 10. Disable cutoff -> past booking evaluates ALLOW again
CUTOFF_ID=10000000-0000-0000-0000-000000000003
curl -s -X PATCH $BASE/api/v1/policies/$CUTOFF_ID/enabled \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"enabled":false}'

# 11. Soft rule (hardDeny:false BOOKING_CUTOFF) -> NEEDS_APPROVAL -> PENDING_APPROVAL,
#     then POST /api/v1/approvals/$BID/approve as approver -> CONFIRMED.

# 12. Concurrent last-seat proof: BookingConcurrencyIT#concurrentLastSeatRaceReservesExactlyOnce
#     (capacity 1, two threads, barrier start; asserts one CONFIRMED + one QUOTA_EXCEEDED DENY,
#     reservedSeats == 1, exactly one active booking).
```

Seed logins for local demos only (never production): set `APP_BOOTSTRAP_ENABLED=true`
plus `APP_BOOTSTRAP_ADMIN_USERNAME/PASSWORD` (and optionally the approver pair) —
the app creates the missing accounts once at startup.

## Concurrency design

- Last seat: one transaction runs evaluation → `reserveSeat` conditional UPDATE →
  booking insert + flush. If the update count is 0, the request is denied with
  `QUOTA_EXCEEDED` and nothing is persisted. Two racers cannot both get count 1.
- Duplicates: the read-only duplicate rule gives a fast answer; the partial unique
  index gives the guarantee. A lost race surfaces as 409, and the transaction rolls
  the seat reservation back.
- Approval: re-evaluation plus the same conditional UPDATE inside one transaction;
  double-approves hit the status check (`409`) and double-reserves are impossible.
- Cancellation: status flip + conditional release (`reserved_seats > 0`) in one
  transaction; already-cancelled is returned untouched, so double-cancels can't
  release twice or drive the count negative.

Last-seat test result (run 2026-10-10, `.\mvnw.cmd test` on Testcontainers PostgreSQL 16):
`Tests run: 56, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`, including
`BookingConcurrencyIT#concurrentLastSeatRaceReservesExactlyOnce`.

## Trade-offs and complexity

- Single Spring Boot monolith: simpler to build, test, deploy and explain than
  anything distributed; no brokers, locks, or event sourcing per the brief.
- Correctness over caching: no policy cache, so rule changes apply on the next
  evaluation with zero invalidation risk.
- O(R log R) sort + O(R) evaluation; O(R) snapshot storage; indexes on
  (employee, date, status), requested_time, and (business_unit, cost_centre,
  enabled, priority).
- JWT stateless auth (jjwt) with BCrypt hashes; dev bootstrap is env-gated and
  off by default.
- Route position is an explicit `RoutePosition` input, never inferred from
  free-text addresses — documented modelling assumption for the night-safety rule.
- Approval needs a soft-failure source: the five seeded rules are hard-deny by
  default, so `NEEDS_APPROVAL` appears when an admin creates a `hardDeny:false`
  rule (demonstrated in the approval tests).

## Known limitations / assumptions

- Company timezone is a single configured zone for all business times.
- `requestedTime` must fall on `bookingDate`; pickup/drop must differ (case-insensitive).
- `bookingDate` uses the service date in the company zone; cross-midnight trips
  keep the date of the service time.
- Pending-approval bookings hold no seat; rejection is terminal (`REJECTED`).
- Quota rows are per cost centre; a missing row denies with `QUOTA_NOT_CONFIGURED`.
- Tests need Docker for Testcontainers; otherwise run unit tests only (see above).

## Interview explanation

Problem: bookings must be judged against editable company policies with a full
explanation, and seat reservations must stay correct when many employees book at
once. Design: each policy is a small Strategy (`BookingRule`) loaded from the
database in a deterministic order; the engine runs all of them and applies a
three-line precedence (hard-deny → soft → allow). Correctness proof: unit tests
pin each rule and the precedence table; Testcontainers integration tests prove
the last-seat race, duplicate race, and cancel/rebook against real PostgreSQL.
Concurrency works because the seat is claimed with a single conditional UPDATE
inside the same transaction that inserts the booking — the database, not Java,
serializes the last seat. Policy changes are just rows, so they apply instantly
with nothing to invalidate.
