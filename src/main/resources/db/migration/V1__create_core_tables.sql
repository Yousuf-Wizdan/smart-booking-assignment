-- Core schema for the Smart Booking Eligibility & Policy Rule Engine.
-- Flyway owns all schema changes; Hibernate only validates (ddl-auto=validate).

create table employee (
    id              uuid primary key,
    employee_code   varchar(32)  not null,
    full_name       varchar(120) not null,
    business_unit   varchar(64)  not null,
    cost_centre_code varchar(32) not null,
    gender          varchar(16)  not null,
    shift_start     time         not null,
    shift_end       time         not null,
    active          boolean      not null default true,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint uq_employee_code unique (employee_code)
);

create table app_user (
    id            uuid primary key,
    username      varchar(64)  not null,
    password_hash varchar(100) not null,
    role          varchar(16)  not null,
    employee_id   uuid         references employee (id),
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint uq_app_user_username unique (username),
    constraint ck_app_user_role check (role in ('EMPLOYEE', 'ADMIN', 'APPROVER'))
);

create table cost_centre_quota (
    id               uuid primary key,
    cost_centre_code varchar(32) not null,
    capacity         integer     not null,
    reserved_seats   integer     not null,
    version          bigint      not null default 0,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint uq_quota_cost_centre unique (cost_centre_code),
    constraint ck_quota_capacity check (capacity >= 0),
    constraint ck_quota_reserved check (reserved_seats >= 0 and reserved_seats <= capacity)
);

create table booking (
    id               uuid primary key,
    employee_id      uuid         not null references employee (id),
    booking_date     date         not null,
    direction        varchar(16)  not null,
    pickup_location  varchar(120) not null,
    drop_location    varchar(120) not null,
    requested_time   timestamp    not null,
    route_position   varchar(16)  not null,
    has_packed_lunch boolean      not null default false,
    requires_parking boolean      not null default false,
    escort_available boolean      not null default false,
    status           varchar(20)  not null,
    decision         varchar(20)  not null,
    deciding_reason  varchar(32),
    rule_results_json text,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    version          bigint       not null default 0,
    constraint ck_booking_direction check (direction in ('LOGIN', 'LOGOUT')),
    constraint ck_booking_status check (status in ('CONFIRMED', 'PENDING_APPROVAL', 'CANCELLED', 'REJECTED')),
    constraint ck_booking_route_position check (route_position in ('FIRST_PICKUP', 'LAST_DROP', 'OTHER'))
);

create table policy_rule (
    id               uuid primary key,
    business_unit    varchar(64),
    cost_centre_code varchar(32),
    rule_type        varchar(32)  not null,
    enabled          boolean      not null default true,
    priority         integer      not null,
    hard_deny        boolean      not null default true,
    parameters_json  text         not null,
    version          bigint       not null default 0,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint ck_policy_rule_type check (rule_type in
        ('SHIFT_WINDOW', 'BOOKING_CUTOFF', 'DUPLICATE_BOOKING', 'COST_CENTRE_QUOTA', 'NIGHT_SAFETY')),
    constraint ck_policy_priority check (priority >= 0)
);

create index ix_booking_employee_date_status on booking (employee_id, booking_date, status);
create index ix_booking_requested_time on booking (requested_time);
create index ix_policy_lookup on policy_rule (business_unit, cost_centre_code, enabled, priority);
create index ix_app_user_employee on app_user (employee_id);

-- One active (confirmed or pending-approval) booking per employee/service slot.
-- This is the database-level guard against concurrent duplicate submissions.
create unique index uq_booking_active_slot
    on booking (employee_id, requested_time, direction)
    where status in ('CONFIRMED', 'PENDING_APPROVAL');
