-- Assign every hcm.beneficiary row to a campaign cycle of hcm.cycles.
-- Client registration date first; if it is in no window (wrong device clock), the server date decides;
-- neither -> UNASSIGNED. Windows do not overlap; the earliest matching window wins.
-- Params: run_version, tz, block_level.

TRUNCATE TABLE hcm.beneficiary_cycle;

INSERT INTO hcm.beneficiary_cycle
    (member_crid, is_head, is_deleted, block_code, block_name, registered_date, server_date,
     cycle_id, cycle_year, cycle_no, cycle_ordinal, date_source, run_version)
SELECT
    member_crid, is_head, is_deleted, block_code, block_name, registered_date, server_date,
    if(ci > 0, tupleElement(cycle, 1), 'UNASSIGNED'),
    if(ci > 0, tupleElement(cycle, 2), 0),
    if(ci > 0, tupleElement(cycle, 3), 0),
    toUInt16(ci),
    multiIf(ci_client > 0, 'CLIENT', ci_server > 0, 'SERVER', 'NONE'),
    {run_version:UInt64}
FROM
(
    WITH
        -- (cycle_id, year, cycle_no, start_date, end_date) in time order; position = cycle_ordinal
        (SELECT groupArray((cycle_id, year, cycle_no, start_date, end_date))
         FROM (SELECT cycle_id, year, cycle_no, start_date, end_date FROM hcm.cycles ORDER BY start_date)) AS cyc
    SELECT
        member_crid, is_head, is_deleted, registered_date,
        toDate(server_created_at, {tz:String}) AS server_date,
        if(boundary_codes[{block_level:String}] != '', boundary_codes[{block_level:String}], locality_code) AS block_code,
        boundary_names[{block_level:String}] AS block_name,
        arrayFirstIndex(x -> registered_date BETWEEN tupleElement(x, 4) AND tupleElement(x, 5), cyc) AS ci_client,
        arrayFirstIndex(x -> server_date BETWEEN tupleElement(x, 4) AND tupleElement(x, 5), cyc) AS ci_server,
        if(ci_client > 0, ci_client, ci_server) AS ci,
        cyc[greatest(ci, 1)] AS cycle
    FROM hcm.beneficiary FINAL
);
