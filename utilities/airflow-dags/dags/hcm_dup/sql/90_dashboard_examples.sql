-- Example dashboard queries. Every pair in hcm.dup_pairs already meets the base conditions
-- (pipeline/run.py MATCH); these queries only add stricter filters and group.
--   individuals        = distinct children on either side of the pairs (not pairs)
--   duplicate_records  = distinct LATER registrations (the extra, duplicated entries)
--   day_relation       = SAME_DAY (both registered that day) / PREVIOUS_DAYS (earlier record from any earlier day)
-- Replace the district code / date in the WHERE clauses. Filter on codes; show names.

-- 1) Scenario tiles: one row per scenario x district x day x same-day/previous-days x head gender,
--    with cumulative distance columns.
SELECT scenario_id, boundary_name, duplicate_date, day_relation, head_gender,
       pairs, duplicate_records, individuals,
       individuals_le_100m, individuals_le_200m, individuals_le_500m, individuals_le_1km, individuals_gt_1km,
       individuals_missing_coords
FROM hcm.v_scenario_summary
WHERE boundary_type = 'District' AND boundary_code = 'MZ_P1_D1' AND duplicate_date = '2026-10-04'
ORDER BY scenario_id, day_relation, head_gender;

-- 2) Your example (a): child AND head exact on name, gender, DOB, by distance (scenario B).
SELECT day_relation,
       uniqExactArray([id_a, id_b])                                  AS individuals,
       uniqExactArrayIf([id_a, id_b], distance_m <= 100)             AS le_100m,
       uniqExactArrayIf([id_a, id_b], distance_m <= 200)             AS le_200m,
       uniqExactArrayIf([id_a, id_b], distance_m > 1000)             AS gt_1km,
       uniqExactArrayIf([id_a, id_b], distance_m IS NULL)            AS missing_coords
FROM hcm.v_scenario_pairs
WHERE scenario_id = 'B_CHILD_PARENT_EXACT'
  AND boundary_codes_b['District'] = 'MZ_P1_D1' AND registered_date_b = '2026-10-04'
GROUP BY day_relation;

-- 3) Your example (b), written directly on dup_pairs (no scenario row needed): child exact on name,
--    gender, DOB; head name >= 0.95; head DOB within 1 year.
SELECT day_relation,
       uniqExactArray([id_a, id_b])                                  AS individuals,
       uniqExactArrayIf([id_a, id_b], distance_m <= 100)             AS le_100m,
       uniqExactArrayIf([id_a, id_b], distance_m <= 200)             AS le_200m,
       uniqExactArrayIf([id_a, id_b], distance_m > 1000)             AS gt_1km,
       uniqExactArrayIf([id_a, id_b], distance_m IS NULL)            AS missing_coords
FROM hcm.dup_pairs FINAL
WHERE c_name_exact = 1 AND c_gender_match = 1 AND c_dob_diff_days = 0
  AND p_name_sim >= 0.95 AND ifNull(p_dob_diff_days <= 365, 1)
  AND boundary_codes_b['District'] = 'MZ_P1_D1' AND registered_date_b = '2026-10-04'
GROUP BY day_relation;

-- 4) Your example (c): child >= 0.95, head >= 0.85, split by head gender same / different,
--    with distance splits <= 100, <= 200, >= 500, <= 1 km.
SELECT if(p_gender_match = 1, 'SAME', 'DIFFERENT') AS head_gender, day_relation,
       uniqExactArray([id_a, id_b])                                  AS individuals,
       uniqExactArrayIf([id_a, id_b], distance_m <= 100)             AS le_100m,
       uniqExactArrayIf([id_a, id_b], distance_m <= 200)             AS le_200m,
       uniqExactArrayIf([id_a, id_b], distance_m >= 500)             AS ge_500m,
       uniqExactArrayIf([id_a, id_b], distance_m <= 1000)            AS le_1km
FROM hcm.dup_pairs FINAL
WHERE c_name_sim >= 0.95 AND p_name_sim >= 0.85
  AND boundary_codes_b['District'] = 'MZ_P1_D1' AND registered_date_b = '2026-10-04'
GROUP BY head_gender, day_relation;

-- 5) Duplicate rate: duplicated registrations / child registrations, per district per day.
SELECT r.boundary_name, r.registered_date, r.child_registrations,
       ifNull(d.duplicate_records, 0) AS duplicate_records,
       round(100 * duplicate_records / r.child_registrations, 2) AS duplicate_rate_pct
FROM hcm.v_daily_registrations AS r
LEFT JOIN (
    SELECT boundary_codes_b['District'] AS code, registered_date_b AS day, uniqExact(id_b) AS duplicate_records
    FROM hcm.dup_pairs FINAL
    WHERE c_name_sim >= 0.95 AND p_name_sim >= 0.85 AND ifNull(distance_m <= 1000, 0)
    GROUP BY code, day
) AS d ON d.code = r.boundary_code AND d.day = r.registered_date
WHERE r.boundary_type = 'District'
ORDER BY r.registered_date, r.boundary_name
SETTINGS join_use_nulls = 1;

-- 6) Whole households registered twice: household pairs sharing 2+ duplicate children.
SELECT boundary_names_b['District'] AS district, registered_date_b AS day,
       count() AS household_pairs, sum(children) AS duplicated_children
FROM (
    SELECT household_crid_a, household_crid_b, any(boundary_names_b) AS boundary_names_b,
           max(registered_date_b) AS registered_date_b, count() AS children
    FROM hcm.dup_pairs FINAL
    WHERE c_name_sim >= 0.95 AND p_name_sim >= 0.85 AND same_household = 0
    GROUP BY household_crid_a, household_crid_b
    HAVING children >= 2
)
GROUP BY district, day ORDER BY day, district;

-- 7) Who registers duplicates: per user, later registrations that duplicate an earlier one,
--    split by whether the same user made the earlier registration.
SELECT created_by_b AS user, uniqExact(id_b) AS duplicate_records,
       uniqExactIf(id_b, same_user = 1) AS of_own_earlier_registration,
       uniqExactIf(id_b, same_user = 0) AS of_another_users_registration
FROM hcm.dup_pairs FINAL
WHERE c_name_exact = 1 AND c_dob_diff_days = 0 AND p_name_sim >= 0.95 AND ifNull(distance_m <= 1000, 0)
GROUP BY user ORDER BY duplicate_records DESC LIMIT 20;

-- 8) How the names matched: exact, typo / word order, or spacing ("james igoche" vs "jamesigoche").
SELECT c_name_method, p_name_method, count() AS pairs, uniqExact(id_b) AS duplicate_records
FROM hcm.dup_pairs FINAL
GROUP BY c_name_method, p_name_method ORDER BY pairs DESC;

-- 9) Strength profile: which criteria matched exactly, in ranking order
--    (L locality, CN/CG/CD child name/gender/DOB, PN/PG/PD head name/gender/DOB, R within 100 m).
SELECT match_signature, count() AS pairs, uniqExact(id_b) AS duplicate_records,
       round(median(distance_m)) AS median_distance_m
FROM hcm.dup_pairs FINAL
GROUP BY match_signature ORDER BY pairs DESC LIMIT 20;

-- 10) How long after the first registration duplicates happen.
SELECT days_apart, count() AS pairs, uniqExact(id_b) AS duplicate_records
FROM hcm.dup_pairs FINAL
WHERE c_name_sim >= 0.95 AND p_name_sim >= 0.85
GROUP BY days_apart ORDER BY days_apart;
