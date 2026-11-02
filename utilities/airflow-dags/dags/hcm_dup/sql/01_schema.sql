-- =============================================================================
-- HCM analytics schema for ClickHouse
--
--   hcm_raw.*  1:1 copies of the Postgres tables (only the columns analytics needs).
--              ReplacingMergeTree keyed on the Postgres key; the latest sync of a row wins.
--              Read with FINAL (or argMax) to see one version per key.
--   hcm.*      modelled tables built from hcm_raw by the pipeline (pipeline/run.py):
--              dim_boundary -> beneficiary -> dup_pairs, plus dashboard views.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS hcm_raw;
CREATE DATABASE IF NOT EXISTS hcm;

-- -----------------------------------------------------------------------------
-- Functions
-- -----------------------------------------------------------------------------

-- Name cleaning: NFKD, drop non-ASCII (accents: "José" -> "jose"), lowercase, any punctuation
-- (- ' . ,) becomes a space. Shared by the name functions below.
CREATE OR REPLACE FUNCTION hcm_name_clean AS (s) ->
    trimBoth(replaceRegexpAll(
        lower(replaceRegexpAll(normalizeUTF8NFKD(ifNull(s, '')), '[^\\x{00}-\\x{7F}]', '')),
        '[^a-z0-9]+', ' '));

-- Word-order-insensitive key: unique words sorted ("maria jose" = "jose maria").
CREATE OR REPLACE FUNCTION hcm_name_key AS (s) ->
    arrayStringConcat(arraySort(arrayDistinct(splitByWhitespace(hcm_name_clean(s)))), ' ');

-- Compact form without spaces ("james igoche" = "jamesigoche").
CREATE OR REPLACE FUNCTION hcm_name_compact AS (s) ->
    replaceAll(hcm_name_clean(s), ' ', '');

-- Levenshtein similarity, same formula as the Python reports: 1 - dist / max(len).
CREATE OR REPLACE FUNCTION hcm_lev_sim AS (a, b) ->
    if(a = '' OR b = '', 0., 1. - editDistanceUTF8(a, b) / greatest(lengthUTF8(a), lengthUTF8(b)));

-- Name similarity used by the matcher. transpose = 1 counts two swapped letters ("pualo" / "paulo") as one
-- edit (Damerau-Levenshtein); transpose = 0 is plain Levenshtein, identical to the Python reports.
-- Names are ASCII after hcm_name_clean, so the byte-based Damerau distance is exact.
CREATE OR REPLACE FUNCTION hcm_name_sim AS (a, b, transpose) ->
    if(a = '' OR b = '', 0.,
       1. - if(transpose, damerauLevenshteinDistance(a, b), editDistanceUTF8(a, b)) / greatest(lengthUTF8(a), lengthUTF8(b)));

-- (0,0), NULL and out-of-range coordinates are treated as "no location".
CREATE OR REPLACE FUNCTION hcm_valid_coord AS (lat, lon) ->
    lat IS NOT NULL AND lon IS NOT NULL AND NOT (lat = 0 AND lon = 0)
    AND abs(lat) <= 90 AND abs(lon) <= 180;

-- Distance bands used in hcm.dup_pairs.distance_band (exact distances are kept in distance_m).
CREATE OR REPLACE FUNCTION hcm_distance_band AS (d) ->
    multiIf(d IS NULL, 'MISSING_COORDS', d <= 100, '0-100m', d <= 200, '100-200m', d <= 500, '200-500m',
            d <= 1000, '500m-1km', d <= 2000, '1-2km', d <= 5000, '2-5km', d <= 10000, '5-10km', '>10km');

-- -----------------------------------------------------------------------------
-- hcm_raw: copies of Postgres tables
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS hcm_raw.household
(
    id                      String,
    tenantid                LowCardinality(String),
    clientreferenceid       String,
    numberofmembers         Nullable(Int32),
    addressid               String,
    householdtype           LowCardinality(String),
    createdby               String,
    createdtime             Int64,
    lastmodifiedtime        Int64,
    rowversion              Int64,
    isdeleted               UInt8,
    clientcreatedtime       Nullable(Int64),
    clientlastmodifiedtime  Nullable(Int64),
    clientcreatedby         String,
    _synced_at              DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(_synced_at)
ORDER BY clientreferenceid;

CREATE TABLE IF NOT EXISTS hcm_raw.household_member
(
    id                          String,
    tenantid                    LowCardinality(String),
    clientreferenceid           String,
    individualid                String,
    individualclientreferenceid String,
    householdid                 String,
    householdclientreferenceid  String,
    isheadofhousehold           UInt8,
    createdby                   String,
    createdtime                 Int64,
    lastmodifiedtime            Int64,
    rowversion                  Int64,
    isdeleted                   UInt8,
    clientcreatedtime           Nullable(Int64),
    clientlastmodifiedtime      Nullable(Int64),
    _synced_at                  DateTime64(3) DEFAULT now64(3),
    INDEX idx_hh_crid householdclientreferenceid TYPE bloom_filter GRANULARITY 4,
    INDEX idx_ind_crid individualclientreferenceid TYPE bloom_filter GRANULARITY 4
)
ENGINE = ReplacingMergeTree(_synced_at)
ORDER BY clientreferenceid;

-- Contact details, photo, email, roles, username etc. are deliberately not copied.
CREATE TABLE IF NOT EXISTS hcm_raw.individual
(
    id                      String,
    clientreferenceid       String,
    individualid            String,
    tenantid                LowCardinality(String),
    givenname               String,
    familyname              String,
    othernames              String,
    dateofbirth             Nullable(Date32),        -- Date32: adults can be born before 1970
    gender                  LowCardinality(String),
    issystemuser            UInt8,
    createdby               String,
    createdtime             Int64,
    lastmodifiedtime        Int64,
    rowversion              Int64,
    isdeleted               UInt8,
    clientcreatedtime       Nullable(Int64),
    clientlastmodifiedtime  Nullable(Int64),
    clientcreatedby         String,
    _synced_at              DateTime64(3) DEFAULT now64(3),
    INDEX idx_id id TYPE bloom_filter GRANULARITY 4
)
ENGINE = ReplacingMergeTree(_synced_at)
ORDER BY clientreferenceid;

-- address has no timestamps in Postgres; it is pulled for households / individual_address rows
-- that changed since the last sync.
CREATE TABLE IF NOT EXISTS hcm_raw.address
(
    id                  String,
    tenantid            LowCardinality(String),
    clientreferenceid   String,
    type                LowCardinality(String),
    latitude            Nullable(Float64),
    longitude           Nullable(Float64),
    locationaccuracy    Nullable(Int32),
    localitycode        String,
    wardcode            String,
    _synced_at          DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(_synced_at)
ORDER BY id;

CREATE TABLE IF NOT EXISTS hcm_raw.individual_address
(
    individualid        String,
    addressid           String,
    type                LowCardinality(String),
    createdtime         Int64,
    lastmodifiedtime    Int64,
    isdeleted           UInt8,
    _synced_at          DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(_synced_at)
ORDER BY (individualid, addressid, type, isdeleted);

-- Small reference tables: fully reloaded on every run.
CREATE TABLE IF NOT EXISTS hcm_raw.boundary_relationship
(
    id                  String,
    tenantid            LowCardinality(String),
    code                String,
    hierarchytype       LowCardinality(String),
    boundarytype        LowCardinality(String),
    parent              String,
    createdtime         Int64,
    lastmodifiedtime    Int64,
    _synced_at          DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(_synced_at)
ORDER BY (tenantid, hierarchytype, code);

CREATE TABLE IF NOT EXISTS hcm_raw.message
(
    id                  String,
    tenantid            LowCardinality(String),
    module              LowCardinality(String),
    locale              LowCardinality(String),
    code                String,
    message             String,
    _synced_at          DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(_synced_at)
ORDER BY (module, code, locale, tenantid);

-- -----------------------------------------------------------------------------
-- hcm: modelled tables
-- -----------------------------------------------------------------------------

-- One row per boundary code with its full ancestry (root first) and localised names.
--   codes_by_type['District'] -> district code of this boundary, names_by_type['District'] -> its name.
CREATE TABLE IF NOT EXISTS hcm.dim_boundary
(
    tenantid        LowCardinality(String),
    hierarchytype   LowCardinality(String),
    code            String,
    name            String,
    boundarytype    LowCardinality(String),
    parent          String,
    depth           UInt8,
    path_codes      Array(String),
    path_types      Array(String),
    path_names      Array(String),
    codes_by_type   Map(String, String),
    names_by_type   Map(String, String),
    _built_at       DateTime DEFAULT now()
)
ENGINE = MergeTree
ORDER BY code;

-- One row per household member (child or head), joined with the individual, the household's head,
-- the location and the boundary ancestry, with matching keys precomputed.
-- Rebuilt for every household touched by a sync; _version = the run that built the row.
CREATE TABLE IF NOT EXISTS hcm.beneficiary
(
    member_crid             String,                     -- household_member.clientreferenceid (row key)
    individual_crid         String,
    individual_pk           String,                     -- individual.id
    individual_id           String,                     -- individual.individualid (IND-...)
    household_crid          String,
    household_id            String,
    tenantid                LowCardinality(String),
    is_head                 UInt8,
    is_deleted              UInt8,                      -- member, household or individual soft-deleted

    given_name              String,
    family_name             String,
    name_key                String,                     -- given + family, words sorted ("jose maria cossa")
    name_gf                 String,                     -- given + family without spaces ("josemariacossa")
    name_fg                 String,                     -- family + given without spaces ("cossajosemaria")
    gender                  LowCardinality(String),
    dob                     Nullable(Date32),

    head_individual_crid    String,
    head_given_name         String,
    head_family_name        String,
    head_name_key           String,
    head_name_gf            String,
    head_name_fg            String,
    head_gender             LowCardinality(String),
    head_dob                Nullable(Date32),

    latitude                Nullable(Float64),
    longitude               Nullable(Float64),
    location_accuracy       Nullable(Int32),
    location_source         LowCardinality(String),     -- HOUSEHOLD / INDIVIDUAL / NONE
    geohash6                String,

    locality_code           String,
    boundary_path           Array(String),              -- codes root -> locality (use codes to filter)
    boundary_path_names     Array(String),              -- names root -> locality (same positions as codes)
    boundary_codes          Map(String, String),        -- boundarytype -> code, e.g. boundary_codes['District']
    boundary_names          Map(String, String),        -- boundarytype -> name (display only; names can repeat)

    registered_at           DateTime64(3, 'UTC'),       -- client created time of the individual
    registered_date         Date,                       -- in the campaign's local timezone
    server_created_at       DateTime64(3, 'UTC'),
    created_by              String,

    _version                UInt64,
    INDEX idx_name_key name_key TYPE bloom_filter GRANULARITY 4,
    INDEX idx_hh household_crid TYPE bloom_filter GRANULARITY 4
)
ENGINE = ReplacingMergeTree(_version)
ORDER BY member_crid;

-- Households touched by a run (input to the beneficiary rebuild).
CREATE TABLE IF NOT EXISTS hcm.work_affected_household
(
    run_version     UInt64,
    household_crid  String
)
ENGINE = MergeTree
ORDER BY (run_version, household_crid)
TTL toDateTime(intDiv(run_version, 1000)) + INTERVAL 7 DAY;

-- Scored pairs of one match run, before the wide columns are attached (input to dup_pairs).
CREATE TABLE IF NOT EXISTS hcm.work_match_pairs
(
    run_version         UInt64,
    id_a                String,
    id_b                String,
    same_locality       UInt8,
    c_name_exact        UInt8,
    c_name_sim          Float64,
    c_name_method       LowCardinality(String),
    c_gender_match      UInt8,
    c_dob_diff_days     Int32,
    p_name_exact        UInt8,
    p_name_sim          Float64,
    p_name_method       LowCardinality(String),
    p_gender_match      UInt8,
    p_dob_diff_days     Nullable(Int32),
    distance_m          Nullable(Float32),
    match_strength      UInt8,
    match_signature     LowCardinality(String)
)
ENGINE = MergeTree
ORDER BY (run_version, id_a, id_b)
TTL toDateTime(intDiv(run_version, 1000)) + INTERVAL 7 DAY;

-- One row per pair of CHILD records that meets the base conditions (pipeline/run.py MATCH).
-- Every criterion is stored, so any stricter combination is a filter (see hcm.dup_scenarios).
-- a = the record registered first, b = the one registered later (registered_date_b = day of the duplicate).
CREATE TABLE IF NOT EXISTS hcm.dup_pairs
(
    tenantid            LowCardinality(String),
    id_a                String,                     -- member_crid
    id_b                String,
    individual_id_a     String,                     -- IND-...
    individual_id_b     String,
    individual_crid_a   String,
    individual_crid_b   String,
    household_crid_a    String,
    household_crid_b    String,

    -- the two children and their heads of household
    given_name_a        String,
    family_name_a       String,
    gender_a            LowCardinality(String),
    dob_a               Nullable(Date32),
    head_given_name_a   String,
    head_family_name_a  String,
    head_gender_a       LowCardinality(String),
    head_dob_a          Nullable(Date32),
    given_name_b        String,
    family_name_b       String,
    gender_b            LowCardinality(String),
    dob_b               Nullable(Date32),
    head_given_name_b   String,
    head_family_name_b  String,
    head_gender_b       LowCardinality(String),
    head_dob_b          Nullable(Date32),
    latitude_a          Nullable(Float64),
    longitude_a         Nullable(Float64),
    latitude_b          Nullable(Float64),
    longitude_b         Nullable(Float64),
    created_by_a        String,
    created_by_b        String,

    -- criteria, in ranking order
    same_locality       UInt8,
    c_name_exact        UInt8,                      -- normalised child names equal (accents, case, word order ignored)
    c_name_sim          Float64,                    -- best of the methods below, 0..1
    c_name_method       LowCardinality(String),     -- EXACT / TOKEN_SORT (typos, word order) / NO_SPACE (spacing)
    c_gender_match      UInt8,
    c_dob_diff_days     Int32,
    p_name_exact        UInt8,                      -- same, for the heads of household
    p_name_sim          Float64,
    p_name_method       LowCardinality(String),
    p_gender_match      UInt8,
    p_dob_diff_days     Nullable(Int32),            -- NULL = a head has no DOB
    distance_m          Nullable(Float32),          -- household GPS; NULL = missing coordinates
    distance_band       LowCardinality(String),     -- 0-100m ... >10km, MISSING_COORDS
    match_strength      UInt8,                      -- bitmask in ranking order, higher = stronger (see match_signature)
    match_signature     LowCardinality(String),     -- e.g. L1-CN1-CG1-CD1-PN0-PG1-PD0-R1

    -- context
    same_household      UInt8,
    same_user           UInt8,                      -- both registered by the same user
    registered_date_a   Date,
    registered_date_b   Date,
    days_apart          Int32,
    day_relation        LowCardinality(String),     -- SAME_DAY / PREVIOUS_DAYS

    boundary_path_a         Array(String),          -- codes root -> locality (use codes to filter)
    boundary_path_b         Array(String),
    boundary_path_names_a   Array(String),          -- names, same positions as the codes
    boundary_path_names_b   Array(String),
    boundary_codes_a        Map(String, String),    -- boundarytype -> code
    boundary_codes_b        Map(String, String),
    boundary_names_a        Map(String, String),    -- boundarytype -> name
    boundary_names_b        Map(String, String),

    detected_at         DateTime DEFAULT now(),
    _version            UInt64,
    INDEX idx_bpa boundary_path_a TYPE bloom_filter GRANULARITY 4,
    INDEX idx_bpb boundary_path_b TYPE bloom_filter GRANULARITY 4
)
ENGINE = ReplacingMergeTree(_version)
ORDER BY (id_a, id_b);

-- Named combinations of criteria for dashboard numbers, applied on top of the base conditions.
-- Edit by inserting a row with the same scenario_id (the newest updated_at wins).
--   *_name_min: 1 = exact (normalised); *_gender: 1 same, 0 different, -1 any
CREATE TABLE IF NOT EXISTS hcm.dup_scenarios
(
    scenario_id             String,
    description             String,
    sort_order              UInt16,
    enabled                 UInt8,
    child_name_min          Float64,
    child_gender            Int8,
    child_dob_max_days      Int32,
    parent_name_min         Float64,
    parent_gender           Int8,
    parent_dob_max_days     Int32,
    max_distance_m          Float64,
    include_missing_coords  UInt8,
    updated_at              DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY scenario_id;

INSERT INTO hcm.dup_scenarios
    (scenario_id, description, sort_order, enabled, child_name_min, child_gender, child_dob_max_days,
     parent_name_min, parent_gender, parent_dob_max_days, max_distance_m, include_missing_coords)
SELECT * FROM values(
    'scenario_id String, description String, sort_order UInt16, enabled UInt8, child_name_min Float64,
     child_gender Int8, child_dob_max_days Int32, parent_name_min Float64, parent_gender Int8,
     parent_dob_max_days Int32, max_distance_m Float64, include_missing_coords UInt8',
    ('A_CHILD_EXACT',          'Child name, gender and DOB exact',                               10, 1, 1,    1, 0,    0,    -1, 100000, 1e9, 1),
    ('B_CHILD_PARENT_EXACT',   'Child and head: name, gender and DOB exact',                     20, 1, 1,    1, 0,    1,     1, 0,      1e9, 1),
    ('C_CHILD_EXACT_PARENT_95','Child exact; head name >= 0.95 and DOB within 1 year',           30, 1, 1,    1, 0,    0.95, -1, 365,    1e9, 1),
    ('D_CHILD_95_PARENT_85',   'Child name >= 0.95; head name >= 0.85',                          40, 1, 0.95, 1, 100000, 0.85, -1, 100000, 1e9, 1),
    ('Z_BASE',                 'All pairs meeting the base conditions',                          90, 1, 0,   -1, 100000, 0,    -1, 100000, 1e9, 1)
)
WHERE (SELECT count() FROM hcm.dup_scenarios) = 0;

CREATE TABLE IF NOT EXISTS hcm.etl_run_log
(
    run_version     UInt64,
    step            LowCardinality(String),
    started_at      DateTime64(3),
    finished_at     DateTime64(3),
    rows            Int64,
    note            String
)
ENGINE = MergeTree
ORDER BY (run_version, started_at);

-- Sync progress per Postgres table (written by pipeline/run.py):
--   initial      first full copy, chunked by primary key; last_key = resume point after a failure
--   incremental  next run reads rows with lastmodifiedtime > wm - overlap, chunked by (lastmodifiedtime, key)
--   reference    small table reloaded on a schedule; updated_at = last reload
CREATE TABLE IF NOT EXISTS hcm.etl_sync_state
(
    table_name      String,
    phase           LowCardinality(String),
    wm              Int64,
    last_key        Array(String),
    updated_at      DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY table_name;

-- -----------------------------------------------------------------------------
-- Dashboard views
-- -----------------------------------------------------------------------------

-- One row per (scenario, pair) for every enabled scenario the pair satisfies.
CREATE OR REPLACE VIEW hcm.v_scenario_pairs AS
SELECT s.scenario_id, s.description AS scenario_description, s.sort_order, p.*
FROM (SELECT * FROM hcm.dup_pairs FINAL) AS p
CROSS JOIN (SELECT * FROM hcm.dup_scenarios FINAL WHERE enabled = 1) AS s
WHERE if(s.child_name_min >= 1, p.c_name_exact = 1, p.c_name_sim >= s.child_name_min)
  AND (s.child_gender = -1 OR p.c_gender_match = s.child_gender)
  AND p.c_dob_diff_days <= s.child_dob_max_days
  AND if(s.parent_name_min >= 1, p.p_name_exact = 1, p.p_name_sim >= s.parent_name_min)
  AND (s.parent_gender = -1 OR p.p_gender_match = s.parent_gender)
  AND ifNull(p.p_dob_diff_days <= s.parent_dob_max_days, 1)
  AND if(p.distance_m IS NULL, s.include_missing_coords = 1, p.distance_m <= s.max_distance_m);

-- Dashboard numbers per scenario x boundary (every level of the later record's boundary) x day x
-- same-day / previous-days x head gender same / different. "individuals" counts distinct children
-- involved (both sides of every pair), not pairs; duplicate_records counts the later registrations.
CREATE OR REPLACE VIEW hcm.v_scenario_summary AS
SELECT
    scenario_id,
    any(scenario_description) AS scenario_description,
    btype AS boundary_type,
    boundary_codes_b[btype] AS boundary_code,
    any(boundary_names_b[btype]) AS boundary_name,
    registered_date_b AS duplicate_date,
    day_relation,
    if(p_gender_match = 1, 'SAME', 'DIFFERENT') AS head_gender,
    uniqExact(id_a, id_b) AS pairs,
    uniqExact(id_b) AS duplicate_records,
    uniqExact(member) AS individuals,
    uniqExactIf(member, distance_m <= 100) AS individuals_le_100m,
    uniqExactIf(member, distance_m <= 200) AS individuals_le_200m,
    uniqExactIf(member, distance_m <= 500) AS individuals_le_500m,
    uniqExactIf(member, distance_m <= 1000) AS individuals_le_1km,
    uniqExactIf(member, distance_m <= 2000) AS individuals_le_2km,
    uniqExactIf(member, distance_m <= 5000) AS individuals_le_5km,
    uniqExactIf(member, distance_m <= 10000) AS individuals_le_10km,
    uniqExactIf(member, distance_m > 1000) AS individuals_gt_1km,
    uniqExactIf(member, distance_m IS NULL) AS individuals_missing_coords
FROM hcm.v_scenario_pairs
ARRAY JOIN mapKeys(boundary_codes_b) AS btype
ARRAY JOIN [id_a, id_b] AS member
GROUP BY scenario_id, boundary_type, boundary_code, duplicate_date, day_relation, head_gender;

-- The strongest match of every later registration (ranking: match_strength, then distance).
CREATE OR REPLACE VIEW hcm.v_best_match_per_record AS
SELECT *
FROM hcm.dup_pairs FINAL
ORDER BY id_b, match_strength DESC, distance_m ASC NULLS LAST
LIMIT 1 BY id_b;

-- Registrations per boundary per day, as the denominator for duplicate rates.
CREATE OR REPLACE VIEW hcm.v_daily_registrations AS
SELECT
    btype AS boundary_type,
    boundary_codes[btype] AS boundary_code,
    any(boundary_names[btype]) AS boundary_name,
    registered_date,
    count() AS registrations,
    countIf(is_head = 0) AS child_registrations
FROM hcm.beneficiary FINAL
ARRAY JOIN mapKeys(boundary_codes) AS btype
WHERE is_deleted = 0
GROUP BY boundary_type, boundary_code, registered_date;

-- Records lost or weakened by missing joins. Run after a sync; non-zero rows are worth a look.
-- "not in ClickHouse" rows usually heal on the next run (late arrivals); persistent ones are broken
-- references in Postgres.
CREATE OR REPLACE VIEW hcm.v_data_quality AS
SELECT 'members whose household is missing (member dropped)' AS check, count() AS records
FROM (SELECT householdclientreferenceid FROM hcm_raw.household_member FINAL WHERE isdeleted = 0)
WHERE householdclientreferenceid NOT IN (SELECT clientreferenceid FROM hcm_raw.household)
UNION ALL
SELECT 'members whose individual is missing (member dropped)', count()
FROM (SELECT individualclientreferenceid FROM hcm_raw.household_member FINAL WHERE isdeleted = 0)
WHERE individualclientreferenceid NOT IN (SELECT clientreferenceid FROM hcm_raw.individual)
UNION ALL
SELECT 'households whose address is missing (no GPS / locality)', count()
FROM (SELECT addressid FROM hcm_raw.household FINAL WHERE isdeleted = 0)
WHERE addressid NOT IN (SELECT id FROM hcm_raw.address)
UNION ALL
SELECT 'individual addresses missing', count()
FROM (SELECT addressid FROM hcm_raw.individual_address FINAL WHERE isdeleted = 0)
WHERE addressid NOT IN (SELECT id FROM hcm_raw.address)
UNION ALL
SELECT tupleElement(x, 1), tupleElement(x, 2)
FROM (
    SELECT arrayJoin([
        ('children without a head of household (not matched)', countIf(head_name_key = '')),
        ('children without DOB (not matched)', countIf(dob IS NULL)),
        ('children without any locality (not matched)', countIf(locality_code = '')),
        ('children whose locality is not in the boundary hierarchy (matched within locality only)',
            countIf(locality_code != '' AND empty(boundary_codes))),
        ('children without GPS (distance = MISSING_COORDS)', countIf(latitude IS NULL))
    ]) AS x
    FROM hcm.beneficiary FINAL
    WHERE is_head = 0 AND is_deleted = 0
);
