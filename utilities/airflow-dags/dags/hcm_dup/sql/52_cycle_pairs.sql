-- Duplicate pairs (hcm.dup_pairs) labelled with the cycle of both records, plus the cycle dashboard views.
--   cycle_relation  SAME_CYCLE              both registrations in one cycle
--                   CROSS_CYCLE_SAME_YEAR   different cycles of one year
--                   CROSS_YEAR              cycles of different years
--                   UNASSIGNED              a record is in no cycle window
--   duplicate_id    the registration counted as the duplicate: the one in the later cycle
--                   (within one cycle: id_b, the one registered later)

DROP TABLE IF EXISTS hcm.dup_pairs_cycle;

CREATE TABLE hcm.dup_pairs_cycle
ENGINE = MergeTree
ORDER BY (cycle_relation, block_code, id_a, id_b)
AS
SELECT
    p.*,
    cb.block_code AS block_code,
    cb.block_name AS block_name,
    toUInt8(p.individual_crid_a = p.individual_crid_b) AS same_individual,
    ca.cycle_id AS cycle_a,
    cb.cycle_id AS cycle_b,
    ca.cycle_year AS cycle_year_a,
    cb.cycle_year AS cycle_year_b,
    ca.cycle_no AS cycle_no_a,
    cb.cycle_no AS cycle_no_b,
    ca.date_source AS date_source_a,
    cb.date_source AS date_source_b,
    multiIf(ca.cycle_ordinal = 0 OR cb.cycle_ordinal = 0, 'UNASSIGNED',
            ca.cycle_ordinal = cb.cycle_ordinal, 'SAME_CYCLE',
            ca.cycle_year = cb.cycle_year, 'CROSS_CYCLE_SAME_YEAR',
            'CROSS_YEAR') AS cycle_relation,
    if(ca.cycle_ordinal <= cb.cycle_ordinal, ca.cycle_id, cb.cycle_id) AS earlier_cycle,
    if(ca.cycle_ordinal <= cb.cycle_ordinal, cb.cycle_id, ca.cycle_id) AS later_cycle,
    toUInt16(abs(toInt32(cb.cycle_ordinal) - toInt32(ca.cycle_ordinal))) AS cycles_apart,
    if(ca.cycle_ordinal > cb.cycle_ordinal, p.id_a, p.id_b) AS duplicate_id
FROM (SELECT * FROM hcm.dup_pairs FINAL) AS p
INNER JOIN (SELECT * FROM hcm.beneficiary_cycle
            WHERE member_crid IN (SELECT id_a FROM hcm.dup_pairs UNION ALL SELECT id_b FROM hcm.dup_pairs)) AS ca
    ON ca.member_crid = p.id_a
INNER JOIN (SELECT * FROM hcm.beneficiary_cycle
            WHERE member_crid IN (SELECT id_a FROM hcm.dup_pairs UNION ALL SELECT id_b FROM hcm.dup_pairs)) AS cb
    ON cb.member_crid = p.id_b;

-- One row per (scenario, pair) for every enabled scenario the pair satisfies (as hcm.v_scenario_pairs).
CREATE OR REPLACE VIEW hcm.v_scenario_pairs_cycle AS
SELECT s.scenario_id, s.description AS scenario_description, s.sort_order, p.*
FROM hcm.dup_pairs_cycle AS p
CROSS JOIN (SELECT * FROM hcm.dup_scenarios FINAL WHERE enabled = 1) AS s
WHERE if(s.child_name_min >= 1, p.c_name_exact = 1, p.c_name_sim >= s.child_name_min)
  AND (s.child_gender = -1 OR p.c_gender_match = s.child_gender)
  AND p.c_dob_diff_days <= s.child_dob_max_days
  AND if(s.parent_name_min >= 1, p.p_name_exact = 1, p.p_name_sim >= s.parent_name_min)
  AND (s.parent_gender = -1 OR p.p_gender_match = s.parent_gender)
  AND ifNull(p.p_dob_diff_days <= s.parent_dob_max_days, 1)
  AND if(p.distance_m IS NULL, s.include_missing_coords = 1, p.distance_m <= s.max_distance_m);

-- Earlier cycle x later cycle per scenario: how many registrations of the later cycle duplicate a record of
-- the earlier one. A record can duplicate records of several cycles, so rows do not add up to a total.
CREATE OR REPLACE VIEW hcm.v_cycle_matrix AS
SELECT
    scenario_id, any(scenario_description) AS scenario_description,
    earlier_cycle, later_cycle, any(cycle_relation) AS cycle_relation,
    uniqExact(id_a, id_b) AS pairs,
    uniqExact(duplicate_id) AS duplicate_records,
    uniqExactIf(duplicate_id, same_individual = 0) AS duplicate_records_other_individual
FROM hcm.v_scenario_pairs_cycle
GROUP BY scenario_id, earlier_cycle, later_cycle;

-- Per scenario x cycle x block (health facility): child registrations of the cycle and how many of them are
-- duplicates of a record in the same cycle, an earlier cycle of the same year, or an earlier year.
-- duplicate_records counts each record once (it can be in several of the three columns).
CREATE OR REPLACE VIEW hcm.v_cycle_duplicate_rate AS
WITH
    reg AS (
        SELECT cycle_id, block_code, any(block_name) AS block_name, count() AS child_registrations
        FROM hcm.beneficiary_cycle
        WHERE is_head = 0 AND is_deleted = 0
        GROUP BY cycle_id, block_code
    ),
    dup AS (
        SELECT scenario_id, later_cycle AS cycle_id, block_code,
               uniqExact(duplicate_id) AS duplicate_records,
               uniqExactIf(duplicate_id, cycle_relation = 'SAME_CYCLE') AS dup_same_cycle,
               uniqExactIf(duplicate_id, cycle_relation = 'CROSS_CYCLE_SAME_YEAR') AS dup_earlier_cycle_same_year,
               uniqExactIf(duplicate_id, cycle_relation = 'CROSS_YEAR') AS dup_previous_year
        FROM hcm.v_scenario_pairs_cycle
        GROUP BY scenario_id, cycle_id, block_code
    ),
    sc AS (
        SELECT scenario_id, description FROM hcm.dup_scenarios FINAL WHERE enabled = 1
    )
SELECT
    sc.scenario_id AS scenario_id,
    sc.description AS scenario_description,
    reg.cycle_id AS cycle_id,
    reg.block_code AS block_code,
    reg.block_name AS block_name,
    reg.child_registrations AS child_registrations,
    dup.duplicate_records AS duplicate_records,
    dup.dup_same_cycle AS dup_same_cycle,
    dup.dup_earlier_cycle_same_year AS dup_earlier_cycle_same_year,
    dup.dup_previous_year AS dup_previous_year
FROM reg
CROSS JOIN sc
LEFT JOIN dup ON dup.scenario_id = sc.scenario_id AND dup.cycle_id = reg.cycle_id AND dup.block_code = reg.block_code;
