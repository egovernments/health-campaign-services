-- Rebuild hcm.dim_boundary from boundary_relationship (parent links) + message (names).
-- Small table (thousands of rows), so it is rebuilt in full every run.
-- Params: tenant, hierarchy, message_module, locales (preferred first; any other locale is a fallback).

TRUNCATE TABLE hcm.dim_boundary;

INSERT INTO hcm.dim_boundary
    (tenantid, hierarchytype, code, name, boundarytype, parent, depth,
     path_codes, path_types, path_names, codes_by_type, names_by_type)
WITH
    rel AS (
        SELECT code, parent, boundarytype
        FROM hcm_raw.boundary_relationship FINAL
        WHERE tenantid = {tenant:String} AND hierarchytype = {hierarchy:String}
    ),
    names AS (
        SELECT code,
               argMax(message, if(indexOf({locales:Array(String)}, locale) = 0,
                                  0, 1000 - indexOf({locales:Array(String)}, locale))) AS name
        FROM hcm_raw.message FINAL
        WHERE module = {message_module:String}
        GROUP BY code
    ),
    -- (code, ancestor, distance) for every ancestor including itself.
    -- The recursive CTE is kept self-contained: ClickHouse cannot join other CTEs inside WITH RECURSIVE.
    anc AS (
        SELECT * FROM (
            WITH RECURSIVE r AS (
                SELECT code, code AS anc_code, toUInt8(0) AS dist
                FROM hcm_raw.boundary_relationship FINAL
                WHERE tenantid = {tenant:String} AND hierarchytype = {hierarchy:String}
                UNION ALL
                SELECT r.code, p.parent AS anc_code, toUInt8(r.dist + 1) AS dist
                FROM r
                INNER JOIN (SELECT code, parent FROM hcm_raw.boundary_relationship FINAL
                            WHERE tenantid = {tenant:String} AND hierarchytype = {hierarchy:String}) AS p
                    ON p.code = r.anc_code
                WHERE p.parent != '' AND r.dist < 30
            )
            SELECT code, anc_code, dist FROM r
        )
    ),
    paths AS (
        SELECT anc.code AS code,
               -- root first, self last
               arraySort(x -> -x.1, groupArray((anc.dist, anc.anc_code, toString(r2.boundarytype),
                                                if(ifNull(n.name, '') = '', anc.anc_code, n.name)))) AS chain
        FROM anc
        INNER JOIN rel AS r2 ON r2.code = anc.anc_code
        LEFT JOIN names AS n ON n.code = anc.anc_code
        GROUP BY anc.code
        SETTINGS join_use_nulls = 1
    )
SELECT
    {tenant:String},
    {hierarchy:String},
    rel.code,
    if(ifNull(n.name, '') = '', rel.code, n.name),
    rel.boundarytype,
    rel.parent,
    toUInt8(length(paths.chain) - 1),
    arrayMap(x -> x.2, paths.chain),
    arrayMap(x -> x.3, paths.chain),
    arrayMap(x -> x.4, paths.chain),
    mapFromArrays(arrayMap(x -> x.3, paths.chain), arrayMap(x -> x.2, paths.chain)),
    mapFromArrays(arrayMap(x -> x.3, paths.chain), arrayMap(x -> x.4, paths.chain))
FROM rel
INNER JOIN paths ON paths.code = rel.code
LEFT JOIN names AS n ON n.code = rel.code
SETTINGS join_use_nulls = 1;
