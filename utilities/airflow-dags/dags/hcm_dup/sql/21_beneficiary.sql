-- Rebuild hcm.beneficiary rows for the households in work_affected_household for this run.
-- One row per household member; deleted members/households/individuals are written with
-- is_deleted = 1 so the matcher can drop their pairs.
-- Params: run_version, tz (campaign local timezone for registered_date).

INSERT INTO hcm.beneficiary
    (member_crid, individual_crid, individual_pk, individual_id, household_crid, household_id, tenantid,
     is_head, is_deleted, given_name, family_name, name_key, name_gf, name_fg, gender, dob,
     head_individual_crid, head_given_name, head_family_name, head_name_key, head_name_gf, head_name_fg,
     head_gender, head_dob,
     latitude, longitude, location_accuracy, location_source, geohash6,
     locality_code, boundary_path, boundary_path_names, boundary_codes, boundary_names,
     registered_at, registered_date, server_created_at, created_by, _version)
WITH
    aff AS (
        SELECT household_crid FROM hcm.work_affected_household
        WHERE run_version = {run_version:UInt64}
    ),
    hh AS (
        SELECT clientreferenceid, id, tenantid, addressid, isdeleted
        FROM hcm_raw.household FINAL
        WHERE clientreferenceid IN aff
    ),
    hm AS (
        SELECT clientreferenceid, householdclientreferenceid, individualclientreferenceid,
               isheadofhousehold, isdeleted
        FROM hcm_raw.household_member FINAL
        WHERE householdclientreferenceid IN aff
    ),
    ind AS (
        SELECT id, clientreferenceid, individualid, givenname, familyname, gender, dateofbirth,
               isdeleted, clientcreatedtime, createdtime, createdby
        FROM hcm_raw.individual FINAL
        WHERE clientreferenceid IN (SELECT individualclientreferenceid FROM hm)
    ),
    -- one head per household (if a household has several active heads, the latest created wins)
    head AS (
        SELECT hm.householdclientreferenceid AS household_crid,
               argMax(ind.clientreferenceid, ind.createdtime) AS head_crid,
               argMax(ind.givenname, ind.createdtime) AS head_given,
               argMax(ind.familyname, ind.createdtime) AS head_family,
               argMax(ind.gender, ind.createdtime) AS head_gender,
               argMax(ind.dateofbirth, ind.createdtime) AS head_dob
        FROM hm INNER JOIN ind ON ind.clientreferenceid = hm.individualclientreferenceid
        WHERE hm.isheadofhousehold = 1 AND hm.isdeleted = 0 AND ind.isdeleted = 0
        GROUP BY hm.householdclientreferenceid
    ),
    hh_addr AS (
        SELECT id, latitude, longitude, locationaccuracy, localitycode
        FROM hcm_raw.address FINAL
        WHERE id IN (SELECT addressid FROM hh)
    ),
    -- latest active address per individual
    ind_addr AS (
        SELECT ia.individualid AS individual_pk,
               argMax(a.latitude, ia.lastmodifiedtime) AS latitude,
               argMax(a.longitude, ia.lastmodifiedtime) AS longitude,
               argMax(a.locationaccuracy, ia.lastmodifiedtime) AS locationaccuracy,
               argMax(a.localitycode, ia.lastmodifiedtime) AS localitycode
        FROM (SELECT * FROM hcm_raw.individual_address FINAL
              WHERE isdeleted = 0 AND individualid IN (SELECT id FROM ind)) AS ia
        INNER JOIN (SELECT * FROM hcm_raw.address FINAL) AS a ON a.id = ia.addressid
        GROUP BY ia.individualid
    ),
    joined AS (
        SELECT
            hm.clientreferenceid AS member_crid,
            ind.clientreferenceid AS individual_crid,
            ind.id AS individual_pk,
            ind.individualid AS individual_id,
            hh.clientreferenceid AS household_crid,
            hh.id AS household_id,
            hh.tenantid AS tenantid,
            hm.isheadofhousehold AS is_head,
            toUInt8(hm.isdeleted OR hh.isdeleted OR ind.isdeleted) AS is_deleted,
            ind.givenname AS given_name,
            ind.familyname AS family_name,
            ind.gender AS gender,
            ind.dateofbirth AS dob,
            ind.clientcreatedtime AS clientcreatedtime,
            ind.createdtime AS createdtime,
            ind.createdby AS created_by,
            head.head_crid AS head_crid,
            head.head_given AS head_given,
            head.head_family AS head_family,
            head.head_gender AS head_gender,
            head.head_dob AS head_dob,
            hcm_valid_coord(hh_addr.latitude, hh_addr.longitude) AS hh_loc_ok,
            hcm_valid_coord(ind_addr.latitude, ind_addr.longitude) AS ind_loc_ok,
            hh_addr.latitude AS hh_lat, hh_addr.longitude AS hh_lon, hh_addr.locationaccuracy AS hh_acc,
            ind_addr.latitude AS ind_lat, ind_addr.longitude AS ind_lon, ind_addr.locationaccuracy AS ind_acc,
            if(ifNull(hh_addr.localitycode, '') != '', hh_addr.localitycode, ifNull(ind_addr.localitycode, '')) AS locality_code
        FROM hm
        INNER JOIN hh ON hh.clientreferenceid = hm.householdclientreferenceid
        INNER JOIN ind ON ind.clientreferenceid = hm.individualclientreferenceid
        LEFT JOIN head ON head.household_crid = hm.householdclientreferenceid
        LEFT JOIN hh_addr ON hh_addr.id = hh.addressid
        LEFT JOIN ind_addr ON ind_addr.individual_pk = ind.id
    )
SELECT
    j.member_crid,
    j.individual_crid,
    j.individual_pk,
    j.individual_id,
    j.household_crid,
    j.household_id,
    j.tenantid,
    j.is_head,
    j.is_deleted,
    j.given_name,
    j.family_name,
    hcm_name_key(concat(j.given_name, ' ', j.family_name)) AS name_key,
    hcm_name_compact(concat(j.given_name, ' ', j.family_name)) AS name_gf,
    hcm_name_compact(concat(j.family_name, ' ', j.given_name)) AS name_fg,
    j.gender,
    j.dob,
    ifNull(j.head_crid, ''),
    ifNull(j.head_given, '') AS hg,
    ifNull(j.head_family, '') AS hf,
    hcm_name_key(concat(hg, ' ', hf)) AS head_name_key,
    hcm_name_compact(concat(hg, ' ', hf)) AS head_name_gf,
    hcm_name_compact(concat(hf, ' ', hg)) AS head_name_fg,
    ifNull(j.head_gender, ''),
    j.head_dob,
    multiIf(j.hh_loc_ok, j.hh_lat, j.ind_loc_ok, j.ind_lat, NULL) AS latitude,
    multiIf(j.hh_loc_ok, j.hh_lon, j.ind_loc_ok, j.ind_lon, NULL) AS longitude,
    multiIf(j.hh_loc_ok, j.hh_acc, j.ind_loc_ok, j.ind_acc, NULL) AS location_accuracy,
    multiIf(j.hh_loc_ok, 'HOUSEHOLD', j.ind_loc_ok, 'INDIVIDUAL', 'NONE') AS location_source,
    if(latitude IS NULL, '', geohashEncode(assumeNotNull(longitude), assumeNotNull(latitude), 6)) AS geohash6,
    j.locality_code,
    ifNull(b.path_codes, []),
    ifNull(b.path_names, []),
    ifNull(b.codes_by_type, map()),
    ifNull(b.names_by_type, map()),
    fromUnixTimestamp64Milli(ifNull(j.clientcreatedtime, j.createdtime), 'UTC') AS registered_at,
    toDate(registered_at, {tz:String}) AS registered_date,
    fromUnixTimestamp64Milli(j.createdtime, 'UTC') AS server_created_at,
    j.created_by,
    {run_version:UInt64} AS _version
FROM joined AS j
LEFT JOIN hcm.dim_boundary AS b ON b.code = j.locality_code
SETTINGS join_use_nulls = 1;
