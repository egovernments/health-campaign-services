-- Size of the matching problem (same blocking as sql/30_match.sql), for extrapolating its run time:
-- matchable children, blocks, largest block, and join_rows = rows the blocking join produces before the
-- name / DOB filters (each child x every child of its block, gender and neighbouring DOB buckets).
-- Params: block_level, child_gender_exact, child_dob_max_days.

WITH
    m AS (
        SELECT if(boundary_codes[{block_level:String}] != '', boundary_codes[{block_level:String}], locality_code) AS block_code,
               if({child_gender_exact:UInt8}, toString(gender), '') AS gender_key,
               intDiv(toDaysSinceYearZero(assumeNotNull(dob)), greatest({child_dob_max_days:Int32}, 1)) AS dob_bucket
        FROM hcm.beneficiary FINAL
        WHERE is_deleted = 0 AND is_head = 0 AND name_key != '' AND head_name_key != '' AND dob IS NOT NULL
          AND block_code != ''
    ),
    cells AS (
        SELECT block_code, gender_key, dob_bucket, count() AS n FROM m GROUP BY block_code, gender_key, dob_bucket
    ),
    blocks AS (
        SELECT block_code, sum(n) AS n FROM cells GROUP BY block_code
    )
SELECT
    (SELECT sum(n) FROM blocks) AS children,
    (SELECT count() FROM blocks) AS blocks,
    (SELECT max(n) FROM blocks) AS max_block_children,
    (SELECT sum(a.n * b.n)
     FROM cells AS a
     INNER JOIN (SELECT block_code, gender_key, arrayJoin([dob_bucket - 1, dob_bucket, dob_bucket + 1]) AS probe, n
                 FROM cells) AS b
         ON a.block_code = b.block_code AND a.gender_key = b.gender_key AND a.dob_bucket = b.probe) AS join_rows
