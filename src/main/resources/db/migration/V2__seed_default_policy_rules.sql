-- Default policy rules: apply to every business unit / cost centre (null scope = wildcard).
-- All parameters are editable through the policy API and take effect on the next evaluation.
-- Fixed UUIDs keep the README curl examples copy-pasteable.

insert into policy_rule (id, business_unit, cost_centre_code, rule_type, enabled, priority, hard_deny, parameters_json)
values
    ('10000000-0000-0000-0000-000000000001', null, null, 'SHIFT_WINDOW',        true, 10, true, '{}'),
    ('10000000-0000-0000-0000-000000000002', null, null, 'NIGHT_SAFETY',        true, 20, true,
        '{"nightStart":"22:00","nightEnd":"05:00","requireEscort":true}'),
    ('10000000-0000-0000-0000-000000000003', null, null, 'BOOKING_CUTOFF',      true, 30, true,
        '{"cutoffMinutes":60}'),
    ('10000000-0000-0000-0000-000000000004', null, null, 'DUPLICATE_BOOKING',   true, 40, true, '{}'),
    ('10000000-0000-0000-0000-000000000005', null, null, 'COST_CENTRE_QUOTA',   true, 50, true, '{}');
