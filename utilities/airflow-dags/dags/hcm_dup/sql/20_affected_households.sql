-- Households whose beneficiary rows must be rebuilt: anything synced at/after {since}
-- in household, household_member, individual, individual_address or address.
-- {since} = ClickHouse server time captured just before the sync (epoch 0 = rebuild everything).

INSERT INTO hcm.work_affected_household (run_version, household_crid)
SELECT {run_version:UInt64}, crid
FROM
(
    SELECT clientreferenceid AS crid
    FROM hcm_raw.household WHERE _synced_at >= {since:DateTime64(3)}

    UNION DISTINCT
    SELECT householdclientreferenceid
    FROM hcm_raw.household_member WHERE _synced_at >= {since:DateTime64(3)}

    UNION DISTINCT
    SELECT householdclientreferenceid
    FROM hcm_raw.household_member
    WHERE individualclientreferenceid IN (
        SELECT clientreferenceid FROM hcm_raw.individual WHERE _synced_at >= {since:DateTime64(3)})

    UNION DISTINCT
    SELECT householdclientreferenceid
    FROM hcm_raw.household_member
    WHERE individualid IN (
        SELECT individualid FROM hcm_raw.individual_address WHERE _synced_at >= {since:DateTime64(3)})

    UNION DISTINCT
    SELECT clientreferenceid
    FROM hcm_raw.household
    WHERE addressid IN (
        SELECT id FROM hcm_raw.address WHERE _synced_at >= {since:DateTime64(3)})
)
WHERE crid != '';
