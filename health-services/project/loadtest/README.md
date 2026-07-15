# Project Service — Load Test Suite (JMeter)

Benchmarks every Project API against **Dev/QA**, with a **smooth ramp 0→50** concurrent users,
reporting **per-API latency (p50/p90/p95/p99)**, **throughput**, **read vs write split**, and
**end-to-end persistence lag** (time from write `202` until the row is searchable).

## Why the numbers mean what they mean (read this first)

The Project service is **async on writes**: `_create` / `_update` / `_delete` validate the request,
publish to Kafka, and return **before** the row is written to Postgres.

- **Search (`_search`) latency = real DB-under-load latency.** These are your headline read numbers.
- **Create/update latency = validation + Kafka publish only** — NOT the DB write. A fast `202`
  here does not mean the row is persisted.
- **Persistence lag** (the setup thread's poll loop) is what tells you how long after the `202`
  the record actually becomes searchable — that's the true end-to-end write time.

## Files

| File | What it is |
|---|---|
| `loadtest.properties` | **The only file you edit.** Token, tenantId, mapped userId/facilityId, load profile. |
| `project-loadtest.jmx` | The JMeter test plan. Setup thread + ramped load thread. |
| `run.sh` | Runs JMeter headless, writes JTL + HTML report, prints the summary table. |
| `summarize.sh` | Per-API benchmark table (p50/p90/p95/p99/throughput/err%) from a JTL. |
| `results/` | One timestamped folder per run. |

## Setup

1. **Install JMeter** (not currently on this machine):
   ```bash
   sudo apt install jmeter        # Debian/Ubuntu
   # or download from https://jmeter.apache.org/download_jmeter.cgi and add bin/ to PATH
   jmeter --version               # verify
   ```

2. **Fill in `loadtest.properties`** — every `<<< FILL >>>`:
   - `host` — your Dev/QA base URL (e.g. `https://health-dev.digit.org`)
   - `authToken`, `tenantId` — you have these
   - `projectType`, `department`, `natureOfWork`, `boundaryType`, `boundary` — must be values
     that exist in the dev environment, or the setup project-create fails.
   - `userId`, `facilityId` — the user & facility you're mapping to the project (for staff/facility create).

3. **Run:**
   ```bash
   ./run.sh
   ```

## How it works (test flow)

1. **SETUP thread (1 user, once):**
   - `POST /project/v1/_create` → extracts the returned `Project[0].id` into `projectId`.
   - Polls `/project/v1/_search` for that id up to `persistencePollTries` times → measures
     **persistence lag** (look for `PERSISTENCE_LAG_MS=` in `results/run-*/jmeter.log`).
   - Publishes `projectId` so the load thread reuses the same project. **This is why you don't
     need to supply a projectId — the suite creates one and chains it everywhere.**

2. **LOAD thread (ramp 0→`peakUsers` over `rampSeconds`, hold `holdSeconds`):** each virtual
   user loops over all APIs — every `_search` (project v1/v2, task, beneficiary, staff, facility,
   resource), the `staff`/`facility`/`task` creates (using your mapped refs), and `/check/bandwidth`.

## Reading the results

After a run:
- **Per-API table** prints to the console (also: `./summarize.sh results/run-XXXX/results.jtl`).
- **HTML dashboard**: open `results/run-XXXX/html/index.html` — has the response-time-over-time
  and percentile charts that show *where in the ramp* latency degrades.
- **Persistence lag**: `grep PERSISTENCE_LAG_MS results/run-XXXX/jmeter.log`

Suggested benchmark-doc table (one row per API):

| API | Type | Samples | Err% | p50 | p90 | p95 | p99 | Throughput (req/s) |
|-----|------|--------:|-----:|----:|----:|----:|----:|-------------------:|

Add a separate line for **write persistence lag (median / p95)** from the log.

## Tuning the load

All in `loadtest.properties` — no need to touch the `.jmx`:
- `peakUsers` (approved ceiling: **50** for shared Dev/QA — don't exceed without coordinating)
- `rampSeconds`, `holdSeconds`

## Caveats / things to flag in the report

- **Writes that need extra references** (beneficiary → household/individual, resource → product)
  are **not** in the default write mix because they need valid dev references. Their `_search`
  is benchmarked. Add creates later via a CSV if you get valid refs.
- **Shared env:** stop at 50 concurrent. Run off-peak. A create-heavy run leaves test rows
  (`name` = `loadtest-*`) in dev — clean up or note them.
- **Auth token expiry:** a long run can outlive the token; re-issue if you see 401s mid-run.
- **First run warms caches** (MDMS, Redis) — discard or annotate the first ramp if comparing runs.
