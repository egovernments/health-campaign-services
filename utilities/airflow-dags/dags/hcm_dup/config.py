"""
hcm_dup/config.py

Settings of the HCM duplicate pipeline (Postgres -> ClickHouse -> duplicate pairs), shared by every tenant DAG.

One DAG per entry in TENANTS: hcm_dup_pipeline_<schema>. Each Postgres schema gets its own ClickHouse databases:
    raw_<schema>   copies of the Postgres tables (synced by the DAG)
    hcm_<schema>   boundaries, beneficiaries, duplicate pairs, scenarios and dashboard views
To add a schema: append an entry to TENANTS (and run the Postgres grants + indexes for it). Nothing else.

Connections come from environment variables (Secret "hcm-dup-sync" + airflow values):
    HCM_CH_HOST (default clickhouse.clickhouse.svc.cluster.local), HCM_CH_PORT (default 8123),
    HCM_CH_USER, HCM_CH_PASSWORD
    HCM_PG_HOST, HCM_PG_PORT (default 5432), HCM_PG_DB, HCM_PG_USER, HCM_PG_PASSWORD,
    HCM_PG_SSLMODE (default "require", Azure Flexible Server needs TLS)
"""
from __future__ import annotations

import copy

# -----------------------------------------------------------------------------
# Tenants (Postgres schemas)
# -----------------------------------------------------------------------------
# VERIFY per schema before enabling, in Postgres:
#   SELECT table_schema, table_name FROM information_schema.tables
#   WHERE table_name IN ('boundary_relationship', 'message');
#       -> boundary_schema / message_schema: the schema whose table has rows (public on central-dev)
#   SELECT tenantid, hierarchytype, boundarytype, count(*) FROM <boundary_schema>.boundary_relationship
#   WHERE tenantid = '<schema>' GROUP BY 1, 2, 3;
#       -> boundary_tenant, boundary_hierarchy, match.block_level (one of the boundarytype values)
#   SELECT tenantid, module, locale, count(*) FROM <message_schema>.message
#   WHERE module LIKE 'hcm-boundary-%' AND tenantid = '<schema>' GROUP BY 1, 2, 3;
#       -> message_module, boundary_locales (rows must exist for boundary_tenant)
TENANTS = [
    {
        "schema": "ba",
        "enabled": True,
        "schedule": "*/15 * * * *",             # UTC; nothing runs while the cluster is stopped (catchup off)
        "timezone": "Africa/Lagos",             # decides what "same day" means
        "boundary_tenant": "ba",                # tenantid of this schema's rows in boundary_relationship / message
        "boundary_hierarchy": "NIGERIAUAT",     # boundary_relationship.hierarchytype (the one address localitycodes use)
        "boundary_schema": "public",            # schema of boundary_relationship (shared by all tenants)
        "message_schema": "public",             # schema of the localization table "message" (shared by all tenants)
        "message_module": "hcm-boundary-nigeriauat",  # boundary names module: hcm-boundary-<hierarchy, lower case>
        "boundary_locales": ["en_NG"],          # preferred name locales, first wins; others are fallback
        "match": {
            "block_level": "LGA",               # boundary type both children must share (falls back to locality)
        },
    },
]

# -----------------------------------------------------------------------------
# Matching: base conditions. A pair of children is stored only if it meets ALL of them; stricter combinations
# are rows in hcm_<schema>.dup_scenarios. A tenant's "match" entry overrides single keys.
# Changing any of these needs a full rebuild (trigger the DAG with full = true).
# -----------------------------------------------------------------------------
MATCH_DEFAULTS = {
    "block_level": "District",              # both children in the same boundary of this type (by code)
    "child_name_min": 0.85,                 # child name similarity (0..1)
    "child_gender_exact": 1,                # 1 = child genders must be equal
    "child_dob_max_days": 90,               # child DOBs at most ~3 months apart
    "parent_name_min": 0.85,                # head-of-household name similarity (0..1)
    "parent_gender_exact": 0,               # 1 = head genders must be equal
    "parent_dob_max_days": 3 * 365,         # head DOBs at most 3 years apart (a missing head DOB passes)
    "max_distance_m": 10000,                # household GPS at most 10 km apart
    "keep_missing_coords": 1,               # 1 = keep pairs where a record has no GPS (band MISSING_COORDS)
    "strength_radius_m": 100,               # last criterion of the ranking (match_strength): within this distance
    "name_transpositions": 1,               # 1 = two swapped letters ("pualo"/"paulo") count as one edit
}

# -----------------------------------------------------------------------------
# Sync (query-based, no CDC: wal_level is "replica")
# -----------------------------------------------------------------------------
# Rows per Postgres chunk. One chunk is held in the worker as CSV bytes (~10 MB at 20k rows), written to
# ClickHouse and released before the next one is read, so worker memory stays flat (pods have 768Mi).
SYNC_CHUNK_ROWS = 20_000
ADDRESS_ID_BATCH = 10_000           # address ids per primary-key lookup
SYNC_OVERLAP_MS = 2 * 60 * 60 * 1000  # re-read this far behind the watermark: the HCM persister commits rows
                                    # asynchronously, so a row can land after newer rows were copied
SYNC_RETRIES = 3                    # attempts per chunk (reconnects in between)
REFERENCE_REFRESH_HOURS = 24        # reload boundary_relationship + message at most this often
FULL_RELOAD_DAYS = 7                # re-copy everything (and drop rows hard-deleted in Postgres) every N days;
                                    # 0 = only when triggered with full = true
WM_EXPR = "coalesce(lastmodifiedtime, createdtime, 0)"  # watermark; must match the Postgres index expression

# What to copy. "select" lists Postgres expressions in the order of "columns" (the ClickHouse columns);
# booleans become int and NULL strings '' to fit non-Nullable columns. "key" = unique key used for chunking
# (primary key, or the unique index for tables without one). {schema} / {message_schema} are filled per tenant.
SYNC_TABLES = [
    {
        "table": "household",
        "mode": "incremental",
        "key": ["id"],
        "columns": ["id", "tenantid", "clientreferenceid", "numberofmembers", "addressid", "householdtype",
                    "createdby", "createdtime", "lastmodifiedtime", "rowversion", "isdeleted",
                    "clientcreatedtime", "clientlastmodifiedtime", "clientcreatedby"],
        "select": f"""id, coalesce(tenantid, ''), coalesce(clientreferenceid, ''), numberofmembers,
                   coalesce(addressid, ''), coalesce(householdtype, ''), coalesce(createdby, ''),
                   coalesce(createdtime, 0), {WM_EXPR}, coalesce(rowversion, 0),
                   coalesce(isdeleted, false)::int, clientcreatedtime, clientlastmodifiedtime,
                   coalesce(clientcreatedby, '')""",
    },
    {
        "table": "household_member",
        "mode": "incremental",
        "key": ["clientreferenceid"],
        "columns": ["id", "tenantid", "clientreferenceid", "individualid", "individualclientreferenceid",
                    "householdid", "householdclientreferenceid", "isheadofhousehold", "createdby", "createdtime",
                    "lastmodifiedtime", "rowversion", "isdeleted", "clientcreatedtime", "clientlastmodifiedtime"],
        "select": f"""coalesce(id, ''), coalesce(tenantid, ''), clientreferenceid, coalesce(individualid, ''),
                   coalesce(individualclientreferenceid, ''), coalesce(householdid, ''),
                   coalesce(householdclientreferenceid, ''), coalesce(isheadofhousehold, false)::int,
                   coalesce(createdby, ''), coalesce(createdtime, 0), {WM_EXPR},
                   coalesce(rowversion, 0), coalesce(isdeleted, false)::int, clientcreatedtime, clientlastmodifiedtime""",
    },
    {
        "table": "individual",
        "mode": "incremental",
        "key": ["id"],
        "columns": ["id", "clientreferenceid", "individualid", "tenantid", "givenname", "familyname", "othernames",
                    "dateofbirth", "gender", "issystemuser", "createdby", "createdtime", "lastmodifiedtime",
                    "rowversion", "isdeleted", "clientcreatedtime", "clientlastmodifiedtime", "clientcreatedby"],
        "select": f"""id, coalesce(clientreferenceid, ''), coalesce(individualid, ''), coalesce(tenantid, ''),
                   coalesce(givenname, ''), coalesce(familyname, ''), coalesce(othernames, ''),
                   CASE WHEN dateofbirth BETWEEN DATE '1900-01-01' AND DATE '2299-12-31' THEN dateofbirth END,
                   coalesce(gender, ''), coalesce(issystemuser, false)::int, coalesce(createdby, ''),
                   coalesce(createdtime, 0), {WM_EXPR}, coalesce(rowversion, 0),
                   coalesce(isdeleted, false)::int, clientcreatedtime, clientlastmodifiedtime,
                   coalesce(clientcreatedby, '')""",
    },
    {
        "table": "individual_address",
        "mode": "incremental",
        "key": ["individualid", "addressid", "type", "isdeleted"],     # the table's unique index
        "columns": ["individualid", "addressid", "type", "createdtime", "lastmodifiedtime", "isdeleted"],
        "select": f"""coalesce(individualid, ''), coalesce(addressid, ''), coalesce(type, ''),
                   coalesce(createdtime, 0), {WM_EXPR}, coalesce(isdeleted, false)::int""",
    },
    {
        # no timestamp: first copy in full, afterwards by the address ids referenced from ClickHouse
        "table": "address",
        "mode": "address",
        "key": ["id"],
        "columns": ["id", "tenantid", "clientreferenceid", "type", "latitude", "longitude", "locationaccuracy",
                    "localitycode", "wardcode"],
        "select": """id, coalesce(tenantid, ''), coalesce(clientreferenceid, ''), coalesce(type, ''),
                   latitude, longitude, locationaccuracy, coalesce(localitycode, ''), coalesce(wardcode, '')""",
    },
    {
        "table": "boundary_relationship",
        "mode": "reference",
        "source_schema": "boundary_schema",     # read from the tenant's boundary_schema (default: its schema)
        "key": ["id"],
        "where": "tenantid = {tenant}",         # only this tenant's rows of a table shared by all tenants
        "columns": ["id", "tenantid", "code", "hierarchytype", "boundarytype", "parent", "createdtime",
                    "lastmodifiedtime"],
        "select": f"""id, tenantid, code, hierarchytype, boundarytype, coalesce(parent, ''),
                   coalesce(createdtime, 0), {WM_EXPR}""",
    },
    {
        "table": "message",
        "mode": "reference",
        "source_schema": "message_schema",      # read from the tenant's message_schema (default: its schema)
        "key": ["id"],
        "where": "module = {message_module} AND tenantid = {tenant}",
        "columns": ["id", "tenantid", "module", "locale", "code", "message"],
        "select": "id, tenantid, module, locale, code, message",
    },
]


def enabled_tenants():
    """TENANTS entries that are enabled, with MATCH_DEFAULTS merged under each tenant's "match" overrides."""
    tenants = []
    for entry in TENANTS:
        if not entry.get("enabled", True):
            continue
        tenant = copy.deepcopy(entry)
        tenant["match"] = {**MATCH_DEFAULTS, **entry.get("match", {})}
        tenants.append(tenant)
    return tenants
