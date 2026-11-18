-- Duplicate matching of CHILD records (non-heads with a head of household) rebuilt in this run (the
-- "delta") against every active child (the "master", which includes the delta itself).
--
-- A pair is stored only if it meets ALL base conditions (params, set in config.py MATCH_DEFAULTS):
--   same {block_level} boundary (code)            -> join key
--   same child gender, if {child_gender_exact}    -> join key
--   child DOBs <= {child_dob_max_days} apart      -> join key (DOB buckets) + exact check
--   child name similarity >= {child_name_min}
--   head name similarity  >= {parent_name_min}
--   same head gender, if {parent_gender_exact}
--   head DOBs <= {parent_dob_max_days} apart (a missing head DOB passes)
--   distance <= {max_distance_m} (missing coordinates kept if {keep_missing_coords})
-- Because blocking uses only base conditions, no pair that meets them is missed.
--
-- Name similarity = best of
--   TOKEN_SORT  Levenshtein on the word-sorted key: typos, jumbled word order ("maria jose" = "jose maria")
--   NO_SPACE    Levenshtein without spaces, given+family and family+given: "james igoche" = "jamesigoche"
-- With {name_transpositions} = 1, two swapped letters count as one edit (Damerau-Levenshtein).
-- (EXACT when the normalised keys are equal.)
--
-- match_strength: bits in the ranking order locality > child name > child gender > child DOB >
-- head name > head gender > head DOB > radius ({strength_radius_m}). Higher = stronger.
--
-- Speed: almost all the time goes into the name similarity (four edit distances per pair), so it is computed
-- only for pairs that pass cheap exact filters first, in this order:
--   2a  one narrow row per child: numeric block key, DOB day, name lengths, letter set (work_match_input)
--   2b  blocking join on numbers only, then DOB gap, name-length bound and letter-set bound
--       (work_match_candidates). Letter-set bound: one edit adds or removes at most two letters of the set, so
--       names whose letter sets differ in more than 2 x (allowed edits) letters cannot reach {child_name_min}.
--       Allowed edits use the longer form (the no-space forms keep repeated words, the key does not).
--   2c  name similarity for the candidates only, then head of household and distance (work_match_pairs)
-- None of the filters drops a pair the full comparison would keep: same pairs, same scores as before.

-- work tables of this file (created here, so existing databases get them without a schema rebuild)
CREATE TABLE IF NOT EXISTS hcm.work_match_input
(
    run_version         UInt64,
    rid                 UInt32,                     -- rank of member_crid: rid order = member_crid order
    member_crid         String,
    is_delta            UInt8,                      -- rebuilt in this run
    bk                  UInt32,                     -- block code (+ gender if child_gender_exact) as a number
    dob_d               Int32,                      -- toDaysSinceYearZero(dob)
    bucket              Int32,                      -- dob_d / child_dob_max_days
    kl                  UInt32,                     -- length of name_key
    cl                  UInt32,                     -- length of name_gf
    letters             UInt32,                     -- bit per letter a..z of the name
    name_key            String,
    name_gf             String,
    name_fg             String,
    gender              LowCardinality(String),
    locality_code       String,
    head_name_key       String,
    head_name_gf        String,
    head_name_fg        String,
    head_gender         LowCardinality(String),
    head_dob            Nullable(Date32),
    latitude            Nullable(Float64),
    longitude           Nullable(Float64),
    registered_at       DateTime64(3, 'UTC')
)
ENGINE = MergeTree
ORDER BY (run_version, rid)
TTL toDateTime(intDiv(run_version, 1000)) + INTERVAL 7 DAY;

CREATE TABLE IF NOT EXISTS hcm.work_match_candidates
(
    run_version         UInt64,
    rid_a               UInt32,
    rid_b               UInt32
)
ENGINE = MergeTree
ORDER BY (run_version, rid_a, rid_b)
TTL toDateTime(intDiv(run_version, 1000)) + INTERVAL 7 DAY;

-- a retried task starts from a clean slate for this run
DELETE FROM hcm.work_match_input WHERE run_version = {run_version:UInt64};

DELETE FROM hcm.work_match_candidates WHERE run_version = {run_version:UInt64};

-- 1) drop old pairs of rows rebuilt in this run (they are recomputed below, or were deleted)
DELETE FROM hcm.dup_pairs
WHERE id_a IN (SELECT member_crid FROM hcm.beneficiary WHERE _version = {run_version:UInt64})
   OR id_b IN (SELECT member_crid FROM hcm.beneficiary WHERE _version = {run_version:UInt64});

-- 2a) matchable children (master; the delta are flagged), one narrow row each
INSERT INTO hcm.work_match_input
SELECT
    {run_version:UInt64},
    toUInt32(row_number() OVER (ORDER BY member_crid)),
    member_crid,
    toUInt8(_version = {run_version:UInt64}),
    toUInt32(dense_rank() OVER (ORDER BY block_code, gender_key)),
    dob_d,
    toInt32(intDiv(dob_d, greatest({child_dob_max_days:Int32}, 1))),
    toUInt32(lengthUTF8(name_key)),
    toUInt32(lengthUTF8(name_gf)),
    arrayReduce('groupBitOr', arrayMap(x -> bitShiftLeft(toUInt32(1), reinterpretAsUInt8(x) - 97),
                                       extractAll(name_key, '[a-z]'))),
    name_key, name_gf, name_fg, gender, locality_code,
    head_name_key, head_name_gf, head_name_fg, head_gender, head_dob,
    latitude, longitude, registered_at
FROM
(
    SELECT *,
           if(boundary_codes[{block_level:String}] != '', boundary_codes[{block_level:String}], locality_code) AS block_code,
           if({child_gender_exact:UInt8}, toString(gender), '') AS gender_key,
           toInt32(toDaysSinceYearZero(assumeNotNull(dob))) AS dob_d
    FROM hcm.beneficiary FINAL
    WHERE is_deleted = 0 AND is_head = 0 AND name_key != '' AND head_name_key != '' AND dob IS NOT NULL
      AND block_code != ''      -- no locality at all: cannot apply the same-boundary condition
);

-- 2b) candidate pairs: same block (+ gender), neighbouring DOB buckets, then the exact cheap filters.
-- Each delta row probes its own DOB bucket and both neighbours; both rows new in this run: one orientation.
INSERT INTO hcm.work_match_candidates (run_version, rid_a, rid_b)
SELECT {run_version:UInt64}, a.rid, b.rid
FROM
(
    SELECT rid, is_delta, bk, dob_d, bucket, kl, cl, letters
    FROM hcm.work_match_input WHERE run_version = {run_version:UInt64}
) AS a
INNER JOIN
(
    SELECT rid, bk, dob_d, kl, cl, letters, arrayJoin([bucket - 1, bucket, bucket + 1]) AS probe
    FROM hcm.work_match_input WHERE run_version = {run_version:UInt64} AND is_delta = 1
) AS b
    ON a.bk = b.bk AND a.bucket = b.probe
WHERE a.rid != b.rid
  AND (a.is_delta = 0 OR a.rid < b.rid)
  AND abs(a.dob_d - b.dob_d) <= {child_dob_max_days:Int32}
  AND (abs(toInt64(a.kl) - b.kl) <= (1 - {child_name_min:Float64}) * greatest(a.kl, b.kl)
       OR abs(toInt64(a.cl) - b.cl) <= (1 - {child_name_min:Float64}) * greatest(a.cl, b.cl))
  AND bitCount(bitXor(a.letters, b.letters))
      <= 2 * floor((1 - {child_name_min:Float64}) * greatest(a.kl, b.kl, a.cl, b.cl) + 1e-9);

-- 2c) name similarity of the candidates, then head of household and distance; scored pairs
INSERT INTO hcm.work_match_pairs
    (run_version, id_a, id_b, same_locality,
     c_name_exact, c_name_sim, c_name_method, c_gender_match, c_dob_diff_days,
     p_name_exact, p_name_sim, p_name_method, p_gender_match, p_dob_diff_days,
     distance_m, match_strength, match_signature)
WITH
    inp AS (
        SELECT * FROM hcm.work_match_input
        WHERE run_version = {run_version:UInt64}
          AND rid IN (SELECT rid_a FROM hcm.work_match_candidates WHERE run_version = {run_version:UInt64}
                      UNION ALL
                      SELECT rid_b FROM hcm.work_match_candidates WHERE run_version = {run_version:UInt64})
    ),
    child AS (
        SELECT
            a.member_crid AS id_a, b.member_crid AS id_b,
            a.registered_at AS reg_a, b.registered_at AS reg_b,
            toUInt8(a.locality_code = b.locality_code) AS same_locality,
            toUInt8(a.name_key = b.name_key) AS c_name_exact,
            if(c_name_exact, 1., hcm_name_sim(a.name_key, b.name_key, {name_transpositions:UInt8})) AS c_sim_token,
            if(c_name_exact, 1., greatest(hcm_name_sim(a.name_gf, b.name_gf, {name_transpositions:UInt8}), hcm_name_sim(a.name_fg, b.name_gf, {name_transpositions:UInt8}),
                                          hcm_name_sim(a.name_gf, b.name_fg, {name_transpositions:UInt8}))) AS c_sim_nospace,
            toUInt8(a.gender = b.gender) AS c_gender_match,
            toInt32(abs(a.dob_d - b.dob_d)) AS c_dob_diff_days,
            a.head_name_key AS hk_a, b.head_name_key AS hk_b,
            a.head_name_gf AS hgf_a, b.head_name_gf AS hgf_b, a.head_name_fg AS hfg_a, b.head_name_fg AS hfg_b,
            a.head_gender AS hgen_a, b.head_gender AS hgen_b, a.head_dob AS hdob_a, b.head_dob AS hdob_b,
            a.latitude AS lat_a, a.longitude AS lon_a, b.latitude AS lat_b, b.longitude AS lon_b
        FROM (SELECT rid_a, rid_b FROM hcm.work_match_candidates WHERE run_version = {run_version:UInt64}) AS c
        INNER JOIN inp AS a ON a.rid = c.rid_a
        INNER JOIN inp AS b ON b.rid = c.rid_b
        WHERE greatest(c_sim_token, c_sim_nospace) >= {child_name_min:Float64}
    ),
    scored AS (
        SELECT
            id_a, id_b, reg_a, reg_b, same_locality,
            c_name_exact,
            greatest(c_sim_token, c_sim_nospace) AS c_name_sim,
            multiIf(c_name_exact, 'EXACT', c_sim_token >= c_sim_nospace, 'TOKEN_SORT', 'NO_SPACE') AS c_name_method,
            c_gender_match, c_dob_diff_days,
            toUInt8(hk_a = hk_b) AS p_name_exact,
            if(p_name_exact, 1., hcm_name_sim(hk_a, hk_b, {name_transpositions:UInt8})) AS p_sim_token,
            if(p_name_exact, 1., greatest(hcm_name_sim(hgf_a, hgf_b, {name_transpositions:UInt8}), hcm_name_sim(hfg_a, hgf_b, {name_transpositions:UInt8}),
                                          hcm_name_sim(hgf_a, hfg_b, {name_transpositions:UInt8}))) AS p_sim_nospace,
            greatest(p_sim_token, p_sim_nospace) AS p_name_sim,
            multiIf(p_name_exact, 'EXACT', p_sim_token >= p_sim_nospace, 'TOKEN_SORT', 'NO_SPACE') AS p_name_method,
            toUInt8(hgen_a = hgen_b) AS p_gender_match,
            if(hdob_a IS NULL OR hdob_b IS NULL, NULL,
               toInt32(abs(dateDiff('day', assumeNotNull(hdob_a), assumeNotNull(hdob_b))))) AS p_dob_diff_days,
            if(lat_a IS NULL OR lat_b IS NULL, NULL,
               toFloat32(geoDistance(assumeNotNull(lon_a), assumeNotNull(lat_a),
                                     assumeNotNull(lon_b), assumeNotNull(lat_b)))) AS distance_m
        FROM child
    ),
    base AS (
        SELECT *,
            toUInt8(c_dob_diff_days = 0) AS c_dob_exact,
            toUInt8(ifNull(p_dob_diff_days = 0, 0)) AS p_dob_exact,
            toUInt8(ifNull(distance_m <= {strength_radius_m:Float32}, 0)) AS near
        FROM scored
        WHERE p_name_sim >= {parent_name_min:Float64}
          AND (NOT {parent_gender_exact:UInt8} OR p_gender_match = 1)
          AND ifNull(p_dob_diff_days <= {parent_dob_max_days:Int32}, 1)
          AND if(distance_m IS NULL, {keep_missing_coords:UInt8} = 1, distance_m <= {max_distance_m:Float32})
    )
SELECT
    {run_version:UInt64},
    -- orient every pair by registration time: a = registered first, b = registered later
    if((reg_a, id_a) <= (reg_b, id_b), id_a, id_b) AS first_id,
    if((reg_a, id_a) <= (reg_b, id_b), id_b, id_a) AS second_id,
    same_locality,
    c_name_exact, c_name_sim, c_name_method, c_gender_match, c_dob_diff_days,
    p_name_exact, p_name_sim, p_name_method, p_gender_match, p_dob_diff_days,
    distance_m,
    toUInt8(same_locality * 128 + c_name_exact * 64 + c_gender_match * 32 + c_dob_exact * 16
            + p_name_exact * 8 + p_gender_match * 4 + p_dob_exact * 2 + near) AS match_strength,
    concat('L', toString(same_locality), '-CN', toString(c_name_exact), '-CG', toString(c_gender_match),
           '-CD', toString(c_dob_exact), '-PN', toString(p_name_exact), '-PG', toString(p_gender_match),
           '-PD', toString(p_dob_exact), '-R', toString(near)) AS match_signature
FROM base;

-- 3) attach names, dates, locations and boundaries, and publish
INSERT INTO hcm.dup_pairs
    (tenantid, id_a, id_b, individual_id_a, individual_id_b, individual_crid_a, individual_crid_b,
     household_crid_a, household_crid_b,
     given_name_a, family_name_a, gender_a, dob_a, head_given_name_a, head_family_name_a, head_gender_a, head_dob_a,
     given_name_b, family_name_b, gender_b, dob_b, head_given_name_b, head_family_name_b, head_gender_b, head_dob_b,
     latitude_a, longitude_a, latitude_b, longitude_b, created_by_a, created_by_b,
     same_locality, c_name_exact, c_name_sim, c_name_method, c_gender_match, c_dob_diff_days,
     p_name_exact, p_name_sim, p_name_method, p_gender_match, p_dob_diff_days,
     distance_m, distance_band, match_strength, match_signature,
     same_household, same_user, registered_date_a, registered_date_b, days_apart, day_relation,
     boundary_path_a, boundary_path_b, boundary_path_names_a, boundary_path_names_b,
     boundary_codes_a, boundary_codes_b, boundary_names_a, boundary_names_b, _version)
WITH
    w AS (
        SELECT * FROM hcm.work_match_pairs WHERE run_version = {run_version:UInt64}
    ),
    wide AS (
        SELECT member_crid, tenantid, individual_id, individual_crid, household_crid,
               given_name, family_name, gender, dob, head_given_name, head_family_name, head_gender, head_dob,
               latitude, longitude, created_by, registered_date,
               boundary_path, boundary_path_names, boundary_codes, boundary_names
        FROM hcm.beneficiary FINAL
        WHERE member_crid IN (SELECT id_a FROM w UNION ALL SELECT id_b FROM w)
    )
SELECT
    a.tenantid, w.id_a, w.id_b, a.individual_id, b.individual_id, a.individual_crid, b.individual_crid,
    a.household_crid, b.household_crid,
    a.given_name, a.family_name, a.gender, a.dob, a.head_given_name, a.head_family_name, a.head_gender, a.head_dob,
    b.given_name, b.family_name, b.gender, b.dob, b.head_given_name, b.head_family_name, b.head_gender, b.head_dob,
    a.latitude, a.longitude, b.latitude, b.longitude, a.created_by, b.created_by,
    w.same_locality, w.c_name_exact, w.c_name_sim, w.c_name_method, w.c_gender_match, w.c_dob_diff_days,
    w.p_name_exact, w.p_name_sim, w.p_name_method, w.p_gender_match, w.p_dob_diff_days,
    w.distance_m, hcm_distance_band(w.distance_m), w.match_strength, w.match_signature,
    toUInt8(a.household_crid = b.household_crid),
    toUInt8(a.created_by = b.created_by),
    a.registered_date, b.registered_date,
    toInt32(dateDiff('day', a.registered_date, b.registered_date)) AS days_apart,
    if(days_apart = 0, 'SAME_DAY', 'PREVIOUS_DAYS'),
    a.boundary_path, b.boundary_path, a.boundary_path_names, b.boundary_path_names,
    a.boundary_codes, b.boundary_codes, a.boundary_names, b.boundary_names,
    {run_version:UInt64}
FROM w
INNER JOIN wide AS a ON a.member_crid = w.id_a
INNER JOIN wide AS b ON b.member_crid = w.id_b;
