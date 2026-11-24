# airflow_dags

Airflow DAGs that populate the ClickHouse bronze tables from the raw event store, and the silver tables from bronze (see the root pipeline description in [`CLAUDE.md`](CLAUDE.md)).

**Raw → bronze**

- `dags/raw_event_to_bronze_orchestrator.py` — the only DAG in this stage (`dag_id=raw_event_to_bronze`). Computes a time window, then fans out one dynamically-mapped task per configured table (see [Raw event → bronze](#raw-event--bronze)).
- `dags/raw_event_bronze_config.py` — the table list: one entry per raw-event-store table → bronze table, with its Debezium-key → bronze-column mapping. Adding a table here adds a mapped task; no DAG code changes.
- `dags/raw_event_bronze_processor.py` — the actual per-table work: keyset-paginated raw read, Debezium `after` extraction, mapping/validation, type coercion, page-by-page bronze insert.

**Bronze → silver**

- `dags/sql_based_dags/bronze_to_silver_task_orchestrator.py` — the bronze→silver DAG (`dag_id=bronze_to_silver_task_orchestrator`), hourly by default. Each run works out one time window, then runs every entity's transformation as **its own task inside this DAG** (`transform_<entity>`), calling that entity's logic directly — no other DAG is triggered. Any number of entities can be listed; up to `task_orchestrator_max_active_tasks` (default 3) run at the same time, and one entity failing doesn't stop the others (see [Bronze → silver](#bronze--silver)).
- `dags/sql_based_dags/<entity>_transformation.py` — one file per entity (e.g. `project_task_transformation.py`): what that entity's silver rows are (its bronze joins, eGov lookups and silver column expressions). The orchestrator's task for the entity runs this. The file also registers a standalone `<entity>_transformation` DAG on the same code path, used only to run one entity by hand.
- `dags/sql_based_dags/sql_flatten_common.py` — how every entity run works: slicing the window, the eGov lookups, the `INSERT … SELECT` into silver and the campaign-field child tables.
- `python_reference/` — the original Python DAGs and the orchestrator that triggered them, kept for reference only (not deployed).

**Shared**

- `dags/clickhouse_utils.py` — shared helper for connecting to ClickHouse (see [Connecting the DAGs to ClickHouse](#connecting-the-dags-to-clickhouse)). Used by both stages.
- `dags/egov_api_utils.py` — shared helpers that call the eGov DIGIT services (boundary-service, user-service, MDMS, workflow-service) for the bronze→silver lookups (see [Calling external eGov services](#calling-external-egov-services)).
- `dags/.airflowignore` — excludes the four importable-but-not-DAG modules above (`clickhouse_utils.py`, `egov_api_utils.py`, `raw_event_bronze_config.py`, `raw_event_bronze_processor.py`) from Airflow's DAG discovery, so they don't show up as spurious DAG import errors. They stay importable from the DAGs as normal. Entries are matched as **globs** (`core.dag_ignore_file_syntax = glob`), and edits only take effect once the dag-processor restarts.

## First-time setup: what you must configure

Everything below is needed once per Airflow deployment. Service URLs go in the **Helm chart**; the ClickHouse Connection, the pool and
any Variables are created in the **Airflow UI**. Anything not listed here has a working default.

### 1. Helm chart: eGov service URLs

Add these under the Airflow chart's `.Values.env` (that list also reaches the KubernetesExecutor worker pods that run the
tasks; `extraEnv` does not). The DAGs read them when they start, so restart Airflow after changing one.

| Env var | What it does for you | If it's missing |
|---|---|---|
| `EGOV_BOUNDARY_SERVICE_BASE_URL` | Boundary-service host. Fills every silver row's boundary columns (`level_one_code` … `level_nine_code`), which every dashboard filters and groups by | Boundary columns stay blank |
| `EGOV_USER_SERVICE_BASE_URL` | User-service host. Fills `user_name`, `name_of_user`, `role` and `user_address` (who did the work) | `user_name` shows the user id; name, role and address stay blank |
| `EGOV_MDMS_SERVICE_BASE_URL` | MDMS host. Picks the one role to show when a user has several | Users with more than one role get a blank `role` |
| `EGOV_WORKFLOW_SERVICE_BASE_URL` | Workflow-service host. Fills the workflow status of expense bills | Bill workflow status stays blank |

Each value is `scheme://host:port` only, e.g. `http://boundary-service.egov:8080`. A missing URL never fails a run:
that enrichment is skipped and one warning is logged. Details: [Configuring eGov service URLs via environment
variables](#configuring-egov-service-urls-via-environment-variables).

### 2. Airflow UI: Connection (Admin → Connections)

| Connection Id | Type | What it does for you | Fields |
|---|---|---|---|
| `clickhouse_default` | HTTP | The ClickHouse every DAG reads from and writes to | Host, Port `8123`, Login, Password, and **Schema = the database** the bronze and silver tables live in (e.g. `analytics`) |

Details: [Connecting the DAGs to ClickHouse](#connecting-the-dags-to-clickhouse).

### 3. Airflow UI: Pool (Admin → Pools)

| Pool | Slots | What it does for you |
|---|---|---|
| `clickhouse_bronze_extraction` | `5` | Caps how many raw→bronze table copies hit ClickHouse at once. The raw→bronze DAG doesn't run until it exists |

### 4. Airflow UI: Variables (Admin → Variables) — optional

No Variable is required: with none set, raw→bronze and bronze→silver both run hourly for every entity. Set one
only to change its default.

| Variable | Default | What it does for you |
|---|---|---|
| `raw_to_bronze_schedule` | `0 * * * *` (hourly) | How often new raw events are copied into bronze |
| `task_orchestrator_schedule` | `@hourly` | How often silver is updated from bronze. `"None"` = only when triggered by hand |
| `task_orchestrator_entity_order` | every entity in `dags/sql_based_dags/`, alphabetical | Which entities a run updates, and in what order they start (JSON list, e.g. `["project", "project_staff", "project_task"]`). Entities left out are not updated. To run them strictly one after another in this order, also set `task_orchestrator_max_active_tasks` to `1` |
| `task_orchestrator_max_active_tasks` | `3` | How many entities are processed at the same time. Lower it if ClickHouse is short of CPU; `1` = one at a time, each finishing before the next in `task_orchestrator_entity_order` starts |
| `task_orchestrator_task_retries` | `1` | How many times Airflow re-runs a failed entity task (each retry redoes that entity's whole window) |
| `task_orchestrator_task_timeout_minutes` | `120` | Time limit for one entity's task; a task still running then is stopped and counts as failed. Raise it for large backfills (a big window of a big entity can take hours) |
| `bronze_to_silver_chunk_size` | `5000` | Bronze rows per ClickHouse statement. Lower it if a run fails on ClickHouse memory or query-size limits; higher means fewer, larger statements |
| `bronze_to_silver_window_start_override` and `bronze_to_silver_window_end_override` | unset | Reprocess a specific past period: set both (ISO datetimes) and trigger `bronze_to_silver_task_orchestrator` by hand. Ignored on scheduled runs; delete both when done |

Details and caveats: [Bronze → silver](#bronze--silver).

**Don't** put Variables or Connections in the Helm chart (`AIRFLOW_VAR_*` / `AIRFLOW_CONN_*` env vars): Airflow reads
those before the UI, so a value set there silently overrides the UI and edits in **Admin → Variables / Connections**
appear to save but do nothing.

## Raw event → bronze

`raw_event_to_bronze` is a single DAG: one task per configured table, all inside that one DAG run. Per run it:

1. **Computes the processing window** (`get_processing_window` task), in this precedence order:
   - explicit backfill window from `dag_run.conf` — `{"window_start": "2026-09-06T00:00:00+00:00", "window_end": "2026-09-07T00:00:00+00:00"}` — used verbatim, with no overlap applied;
   - otherwise Airflow's own `data_interval_start`/`data_interval_end`, with the start pulled back by `OVERLAP_MINUTES` (1 minute) so records landing exactly on a window boundary aren't missed;
   - otherwise (a manual run, which has no real data interval) `end − lookback_minutes`, where `lookback_minutes` comes from `dag_run.conf` and defaults to 60. So `{"lookback_minutes": 1440}` on a manual trigger reprocesses the previous 24 hours.
2. **Fans out** one mapped `process_table_task` per entry in `RAW_EVENT_BRONZE_TABLES`. Each mapped task runs in the `clickhouse_bronze_extraction` pool, and `max_active_tasks=5` caps how many hit ClickHouse at once. Tasks are independent — one table failing doesn't stop the others. `map_index_template` labels them by table name in the UI (`process_table_task[household]`, not `[0]`), so a failure points at a table without opening the log.
3. **Per table** (`process_table` in `raw_event_bronze_processor.py`): reads the bronze schema from `system.columns` and validates the configured mapping against it *before* reading any data; then reads the raw table in keyset-paginated pages of `page_size` (default 10 000) ordered by `(event_time, id)`; extracts the Debezium `after` object from each event; maps source keys to bronze columns; coerces each value to the bronze column's ClickHouse type; and inserts that page before fetching the next.

Overlapping windows and re-runs are safe by design: the bronze tables are `ReplacingMergeTree`, so a re-inserted row dedupes on merge rather than duplicating.

### Configuration is data, not code

`RAW_EVENT_BRONZE_TABLES` in `raw_event_bronze_config.py` currently covers 26 tables (household, individual, address, project and its target/address/staff/facility/beneficiary/task tables, product/product-variant, facility, stock, stock reconciliation, task resource, PGR service/address, WMS attendance register/staff/attendee/log, and the expense bill/bill-detail/line-item/party tables). To add one:

1. create the raw event store table, 2. create the bronze `stg_*` table, 3. add an entry here. The DAG picks it up as a new mapped task on the next parse.

Keys per entry:

| Key | Meaning |
|---|---|
| `name` | Table key; also the map index shown in the Airflow UI |
| `raw_table` | Fully-qualified raw event store table, e.g. `analytics.household_events_raw` |
| `bronze_table` | Fully-qualified bronze table, e.g. `analytics.stg_household` |
| `page_size` | Raw events read per keyset page (10 000 throughout today) |
| `columns` | Debezium `after` key → bronze column name. Note the source keys are the lowercased Postgres column names Debezium emits (`clientreferenceid`), not the bronze names (`client_reference_id`) |
| `allow_null_after` | Optional, default `False`. See [Failure modes](#failure-modes-and-what-they-mean) |

### Validation is deliberately loud

Three checks exist specifically so a mapping typo fails visibly instead of quietly loading `''`/`0`/`False` into bronze:

- **Bronze side, before reading any data** (`validate_table_mapping`) — every bronze column in the mapping must exist in `system.columns`, and no bronze column may be mapped twice. A missing bronze table (or one with no columns) also fails here.
- **Source side, on the first page of real events** (`validate_source_keys`) — the configured source keys are checked against the keys actually present in up to 100 sampled `after` payloads. Configured-but-absent keys **fail the task**; keys present in the event but not mapped to bronze are only **logged as a warning**, which is how upstream schema drift becomes visible without breaking a run that doesn't care about the new column.
- **Null `after` reconciliation, at the end of the run** — see below.

### Failure modes and what they mean

- **`Configured source columns are absent from the Debezium after payload`** — the mapping in `raw_event_bronze_config.py` names a source key the events don't carry. Fix the key's spelling, or drop the column if the source table genuinely doesn't have it.
- **`N of M raw events had a null after payload and were dropped`** — only CREATE and UPDATE events are expected in this stage, and both carry a full row image, so a null `after` is silent data loss (typically a DELETE or a tombstone reaching a topic that shouldn't have one). The task fails *after* logging the full counts, so the numbers are in the log either way. Investigate the topic before re-running; to deliberately accept the loss and let that table proceed, set `"allow_null_after": True` on its entry.
- **A single mapped task failing** affects only its table. Clear and re-run that map index — the window is derived from the run, so the retry reprocesses the same window.

### Airflow objects it needs

The pool is not created automatically — create it once:

```bash
airflow pools set clickhouse_bronze_extraction 5 "Raw -> Bronze ClickHouse extraction"
```

It also uses the same `clickhouse_default` Airflow Connection as the bronze→silver DAGs (see [Connecting the DAGs to ClickHouse](#connecting-the-dags-to-clickhouse)).

### Schedule

| Variable | What it controls | Valid values | Default if unset |
|---|---|---|---|
| `raw_to_bronze_schedule` | How often `raw_event_to_bronze` runs | A cron expression (`"0 * * * *"`), an Airflow schedule preset (`"@hourly"`, `"@daily"`, ...), or `"None"` for manual-only | `0 * * * *` (hourly) |

```bash
airflow variables set raw_to_bronze_schedule "0 * * * *"
```

Read at DAG-parse time, so a change lands on the next file parse and affects only future runs. The DAG also sets `catchup=False` and `max_active_runs=1` — a paused-then-unpaused DAG won't stampede through missed intervals, and two runs never process overlapping windows concurrently. To recover a gap, trigger manually with an explicit `window_start`/`window_end`.

## Bronze → silver

### How a run works

`bronze_to_silver_task_orchestrator` (`dags/sql_based_dags/bronze_to_silver_task_orchestrator.py`) is the only DAG
that writes silver. Each run:

1. **Resolves one time window** (`resolve_time_window` task), in this order:
   - `start_time`/`end_time` from `dag_run.conf`, if given (a manual run);
   - otherwise, on a manual or backfill run (or an `"@once"` schedule), the two `bronze_to_silver_window_*_override`
     Variables — only if **both** are set; one alone is ignored with a warning;
   - otherwise this run's own data interval: the previous hour for `@hourly`. A recurring scheduled run never reads
     the overrides, so a forgotten override can't freeze every later run on the same window.

   A zero-width window fails the run instead of silently processing nothing. The window selects bronze rows by when
   they were ingested (`_ingested_at`), not by their own modification time.
2. **Runs one task per entity** (`transform_<entity>`), each calling that entity's transformation directly. The
   entities come from `task_orchestrator_entity_order` (default: every `<entity>_transformation.py` in the folder; any
   number can be listed; entities left out are not run). Up to `task_orchestrator_max_active_tasks` run at the same
   time, started in list order; when one finishes the next starts. With `task_orchestrator_max_active_tasks = 1` they
   run strictly one after another in list order. Entities share no tables, so running them together is safe — the limit is
   ClickHouse CPU.
3. **Inside an entity task** (`sql_flatten_common.run_transformation`): the window is split into slices of at most
   `bronze_to_silver_chunk_size` bronze rows (per tenant, by id range). For each slice the eGov lookups are resolved
   (see [Calling external eGov services](#calling-external-egov-services)) and one `INSERT … SELECT` inside ClickHouse
   writes the slice's silver rows — and, for household, household_member, project_beneficiary, stock and
   project_task, their `<entity>_campaign_field` rows. A slice too large for the query-size guards is split in half
   until it fits.
4. **`all_entities_succeeded`** marks the run failed if any entity task failed, so alerting on the DAG catches it.

**Failures.** One entity failing never stops the others. Within an entity, a failed slice is retried once straight
away; if it fails again the remaining slices still run and the task then fails, listing the failed slices. Airflow
then retries the task `task_orchestrator_task_retries` times (each retry redoes that entity's whole window) — except
when every failed slice hit the size guard, which fails without retry (lower `bronze_to_silver_chunk_size`). Each
entity task has a time limit, `task_orchestrator_task_timeout_minutes` (default 2 hours).

**Re-running is safe.** Silver tables are `ReplacingMergeTree`, so writing the same rows again collapses on merge.
To reprocess a period, trigger the DAG by hand with a window:

```json
{"start_time": "2026-09-09T00:00:00+00:00", "end_time": "2026-09-23T00:00:00+00:00"}
```

To redo a single entity, trigger its standalone `<entity>_transformation` DAG with the same conf. `truncate_target`
is refused: these DAGs write the main silver tables.

**Where it writes.** The main silver tables in the `clickhouse_default` Connection's database (its schema, default
`analytics`). An entity file only declares its joins, helper expressions and `(silver column, expression)` pairs;
`sql_flatten_common.py` owns everything else. Window reads and join sides looked up by a non-primary key go through
the projections in `ClickHouse_ddl/14_bronze_projections.sql` (a query with `FINAL` never uses a projection, so those
sides are `argMax` subqueries; extend the projection before reading a new column through it).

The original Python DAGs and their trigger-based orchestrator are kept in `python_reference/` for reference only;
each entity file's docstring lists its deliberate differences from the Python file of the same name. Measurements:
`PROJECTION_COMPARISON_REPORT.md`.

### Variables

All optional; the defaults are in [First-time setup](#4-airflow-ui-variables-admin--variables--optional).

| Variable | Read when | Effect of a change |
|---|---|---|
| `task_orchestrator_schedule`, `task_orchestrator_entity_order`, `task_orchestrator_max_active_tasks`, `task_orchestrator_task_retries`, `task_orchestrator_task_timeout_minutes` | When Airflow parses the DAG file (a few seconds after saving) | Runs created after the reparse; a schedule change never alters a run already in progress |
| `bronze_to_silver_chunk_size`, `bronze_to_silver_window_start_override`, `bronze_to_silver_window_end_override` | When a task runs | The next run, no reparse needed |

Schedule values: a cron expression (`"0 */2 * * *"`), an Airflow preset (`"@hourly"`, `"@daily"`, `"@once"`), or
`"None"` for manual only. A cron or preset schedule gets a real `[previous tick, this tick)` data interval.

Via the CLI (activate the local dev environment first, see below):

```bash
airflow variables set task_orchestrator_schedule "@hourly"
# only these entities, strictly one after another in this order
airflow variables set task_orchestrator_entity_order '["project", "project_staff", "project_task"]'
airflow variables set task_orchestrator_max_active_tasks "1"
airflow variables set bronze_to_silver_chunk_size "5000"

# longer time limit per entity task, e.g. for a large backfill
airflow variables set task_orchestrator_task_timeout_minutes "300"

# reprocess a past window on the next MANUAL run; both are required together
airflow variables set bronze_to_silver_window_start_override "2026-08-01T00:00:00+00:00"
airflow variables set bronze_to_silver_window_end_override "2026-08-02T00:00:00+00:00"
airflow dags trigger bronze_to_silver_task_orchestrator

# back to the defaults
airflow variables delete bronze_to_silver_window_start_override
airflow variables delete bronze_to_silver_window_end_override
```

Or in the UI: **Admin → Variables → +**, `Key` = the Variable name, `Val` = the value.

## Connecting the DAGs to ClickHouse

Every DAG here — `raw_event_to_bronze` and `bronze_to_silver_task_orchestrator` (and the standalone per-entity DAGs) — talks to ClickHouse via [`clickhouse-connect`](https://github.com/ClickHouse/clickhouse-connect) (installed in the local venv — see below), using a shared helper (`dags/clickhouse_utils.py`) that looks up connection details from a standard Airflow Connection, `conn_id="clickhouse_default"`.

Create it once, via the CLI:

```bash
airflow connections add clickhouse_default \
  --conn-type http \
  --conn-host <clickhouse-host> \
  --conn-port 8123 \
  --conn-login <user> \
  --conn-password <password> \
  --conn-schema analytics
```

or via the UI: **Admin → Connections → +**, `Connection Id` = `clickhouse_default`, `Connection Type` = `HTTP`, then fill in host/port/login/password/schema (database name) as above.

On a deployed Airflow, create it the same way, in **Admin → Connections**. The schema is the database the DAGs read bronze from and write silver to.

## Configuring eGov service URLs via environment variables

Unlike the Airflow Variables above, the base URLs *and search paths* for the DIGIT services `dags/egov_api_utils.py` calls are **plain environment variables**, read via `os.getenv()`, not Airflow Variables:

| Env var                                    | What it controls                                                                         | Valid values                                | Default if unset                                                        |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- | ------------------------------------------- | ----------------------------------------------------------------------- |
| `EGOV_BOUNDARY_SERVICE_BASE_URL`         | Base URL for boundary-service calls                                                      | A host URL, e.g.`"http://localhost:8081"` | unset — boundary lookups are skipped (logged once, not per row)        |
| `EGOV_USER_SERVICE_BASE_URL`             | Base URL for user-service calls                                                          | A host URL, e.g.`"http://localhost:8284"` | unset — user lookups are skipped (logged once, not per row)            |
| `EGOV_MDMS_SERVICE_BASE_URL`             | Base URL for MDMS calls (used only when a user has more than one role, to rank/pick one) | A host URL                                  | unset — role-ranking lookups are skipped (logged once, not per row)    |
| `EGOV_WORKFLOW_SERVICE_BASE_URL`         | Base URL for workflow-service calls (expense-bill workflow-status enrichment)                | A host URL                                  | unset — workflow-status lookups are skipped (logged once, not per row) |
| `EGOV_BOUNDARY_RELATIONSHIP_SEARCH_PATH` | Path appended to the boundary-service base URL                                           | A path starting with`/`                   | `/boundary-service/boundary-relationships/_search`                    |
| `EGOV_USER_SEARCH_PATH`                  | Path appended to the user-service base URL                                               | A path starting with`/`                   | `/user/_search`                                                       |
| `EGOV_MDMS_SEARCH_PATH`                  | Path appended to the MDMS base URL                                                       | A path starting with`/`                   | `/egov-mdms-service/v1/_search`                                       |
| `EGOV_WORKFLOW_PROCESS_SEARCH_PATH`      | Path appended to the workflow-service base URL                                           | A path starting with`/`                   | `/egov-workflow-v2/egov-wf/process/_search`                           |

The four `*_PATH` vars exist so a DIGIT deployment that routes these differently — behind a gateway, or with a non-default `SERVER_CONTEXT_PATH` — can be re-pointed from the Helm chart without a code change. **They behave differently from the base URLs when unset**: an unset base URL disables that service's calls outright, whereas an unset path just falls back to the default above — a path never disables anything. Each default is the full path from the host root *including* the service's own context path, because the paired `*_BASE_URL` is only `scheme://host:port`.

**Why env vars instead of Airflow Variables**: these are read on every eGov lookup, in every slice of every entity task. `Variable.get()` is a network round-trip to the Airflow webserver/metadata DB on every call — at that call volume it noticeably balloons task time and memory. `os.getenv()` is a zero-cost in-process read, so `egov_api_utils.py` reads each of these once at import time into a module-level constant (`BOUNDARY_SERVICE_BASE_URL`, etc.) and every call site just uses that constant directly — no per-call lookup at all. If a base URL is unset, that service's calls are skipped outright (not attempted-then-failed) and a single warning is logged the first time, not once per slice — these constants can't change mid-process, so there's nothing to gain from retrying.

In production, these are set in the Helm chart's `.Values.env` (see [First-time setup](#first-time-setup-what-you-must-configure)). For local dev, `egov_api_utils.py` automatically loads `airflow_dags/.env` on import (via `python-dotenv`, already part of this venv's dependency tree — no new install needed) — just fill in the values there and restart the scheduler/webserver (or trigger a fresh task run); no manual `export`/`source` needed, and it never overrides a real env var that's already set (so it's harmless to leave in place even if you *also* export these manually). `airflow_dags/.env` is gitignored — per-developer, never committed — and production doesn't use it at all (there's no such file in the deployed image).

Unlike an Airflow Variable, these aren't settable via the Airflow UI/CLI at runtime — changing one requires restarting the Airflow process(es) that read it.

## Calling external eGov services

Silver rows need values that live in DIGIT services, not in bronze: boundary levels (boundary-service), user name /
role / address (user-service, with MDMS to rank a user's roles) and bill workflow status (workflow-service). An
entity file declares which of these it needs as **lookups** (`boundary_lookup`, `user_lookup`, `workflow_lookup` in
`sql_flatten_common.py`). For each slice, each lookup:

1. runs one ClickHouse query that returns the slice's distinct keys (e.g. `(tenant_id, hierarchy_type,
   boundary_code)`);
2. resolves them in Python with `egov_api_utils.py` — `resolve_boundary_levels`, `resolve_user_info`,
   `resolve_workflow_summaries` — which batch the HTTP calls and cache results for the rest of the task;
3. embeds the answers in the slice's single `INSERT … SELECT` as an inline `VALUES` table, `LEFT JOIN`ed onto the
   rows. Nothing is stored in ClickHouse.

Boundary resolution tries one bulk `codes=` call per batch of uncached codes and falls back to fetching the whole
hierarchy tree once if that fails. User role: one role → used as is; several → the lowest MDMS rank wins.

**Failure handling**: every HTTP call is wrapped in try/except — on a failure it logs and returns an empty result,
never raises. A service outage leaves that slice's boundary/user/workflow columns blank instead of failing the task;
re-running the window later (see [Bronze → silver](#bronze--silver)) fills them in.

## Local dev environment

Airflow isn't installed system-wide; it lives in a project-local virtualenv (gitignored) so it doesn't touch system Python.

```bash
cd airflow_dags

# one-time setup
python3 -m venv .venv
./.venv/bin/pip install --upgrade pip
curl -sfL -o /tmp/airflow-constraints.txt \
  "https://raw.githubusercontent.com/apache/airflow/constraints-3.0.6/constraints-3.12.txt"
./.venv/bin/pip install "apache-airflow==3.0.6" apache-airflow-providers-standard \
  --constraint /tmp/airflow-constraints.txt
./.venv/bin/pip install clickhouse-connect

# every session
export AIRFLOW_HOME="$(pwd)/.airflow"
export PATH="$(pwd)/.venv/bin:$PATH"
```

> Always install with `--constraint` pinned to the matching Airflow release/Python version. Installing without it lets pip resolve dependency versions (e.g. `starlette`/`fastapi`) newer than what that Airflow release was tested against, which breaks the webserver at runtime.

First-time initialization:

```bash
# point dags_folder at this repo's dags/ and disable example DAGs
airflow config list >/dev/null   # generates airflow.cfg on first run
sed -i "s|^dags_folder = .*|dags_folder = $(pwd)/dags|" "$AIRFLOW_HOME/airflow.cfg"
sed -i "s|^load_examples = .*|load_examples = False|" "$AIRFLOW_HOME/airflow.cfg"

airflow db migrate
```

Run it:

```bash
airflow standalone
```

This starts the webserver, scheduler, dag-processor, and triggerer together, and prints/generates admin login credentials (see `$AIRFLOW_HOME/simple_auth_manager_passwords.json.generated` if you miss them in the log). The UI is at http://localhost:8080. All DAGs start paused — unpause them from the UI or with `airflow dags unpause <dag_id>` (`raw_event_to_bronze`, `bronze_to_silver_task_orchestrator`) before triggering.

`.venv/` and `.airflow/` are gitignored — safe to delete and rebuild at any time.
