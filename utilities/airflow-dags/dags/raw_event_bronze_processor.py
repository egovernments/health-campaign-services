"""
Raw Event -> Bronze processor.

Responsibilities:

1. Read Raw Event data in the configured hourly window.
2. Use keyset pagination on (event_time, id).
3. Extract the Debezium `after` payload.
4. Ignore CREATE/UPDATE branching.
5. Apply explicit source -> Bronze column mappings.
6. Validate the Bronze schema against ClickHouse system.columns.
7. Coerce values according to Bronze ClickHouse types.
8. Insert one page at a time.
9. Continue until the complete window is processed.

Only CREATE and UPDATE events are expected.
No DELETE processing is required.
"""

from __future__ import annotations

import json
import logging
import os
import sys
from datetime import date, datetime, timezone
from decimal import Decimal
from time import monotonic
from typing import Any, Optional

# Add dags/ to PYTHONPATH so clickhouse_utils can be imported.
sys.path.insert(
    0,
    os.path.dirname(os.path.abspath(__file__)),
)

from clickhouse_utils import get_clickhouse_client  # noqa: E402


logger = logging.getLogger(__name__)


# Lineage column carrying the raw event's own id, so a Bronze row can be traced
# back to the exact event that produced it. Populated by the processor rather
# than the config mapping -- it comes from the raw table, not the Debezium
# payload. Never part of the Bronze ORDER BY: it is unique per event, so
# including it would stop ReplacingMergeTree ever collapsing versions.
EVENT_ID_COLUMN = "event_id"

# Column order returned by fetch_raw_events: raw, event_time, id, et_ms
RAW_COL_EVENT_ID = 2


# ----------------------------------------------------------------------
# Debezium envelope
# ----------------------------------------------------------------------

def extract_after_payload(raw_value: Any) -> Optional[dict]:
    """
    Extract the `after` object from a Debezium event.

    Supports both:
      {"payload": {"after": {...}}}
      {"after": {...}}

    CREATE and UPDATE are processed the same way.
    """

    if raw_value is None:
        return None

    if isinstance(raw_value, bytes):
        raw_value = raw_value.decode("utf-8")

    if isinstance(raw_value, str):
        event = json.loads(raw_value)
    elif isinstance(raw_value, dict):
        event = raw_value
    else:
        raise TypeError(
            f"Unsupported Raw event type: {type(raw_value)}"
        )

    payload = event.get("payload", event)

    if not isinstance(payload, dict):
        raise ValueError(
            "Debezium payload is not a JSON object"
        )

    return payload.get("after")


# ----------------------------------------------------------------------
# ClickHouse schema
# ----------------------------------------------------------------------

def get_bronze_schema(
    client,
    bronze_table: str,
) -> dict[str, str]:
    """
    Read Bronze column names and ClickHouse types from system.columns.
    """

    if "." not in bronze_table:
        raise ValueError(
            f"Bronze table must be fully qualified: {bronze_table}"
        )

    database, table = bronze_table.split(".", 1)

    query = """
        SELECT
            name,
            type
        FROM system.columns
        WHERE database = {database:String}
          AND table = {table:String}
        ORDER BY position
    """

    result = client.query(
        query,
        parameters={
            "database": database,
            "table": table,
        },
    )

    return {
        row[0]: row[1]
        for row in result.result_rows
    }


def validate_table_mapping(
    table_config: dict,
    bronze_schema: dict[str, str],
):
    """
    Validate configured source -> Bronze column mappings.

    Fails if:
      - A Bronze column is duplicated.
      - A configured Bronze column does not exist.
    """

    mapping = table_config["columns"]

    bronze_columns = list(mapping.values())

    duplicates = {
        column
        for column in bronze_columns
        if bronze_columns.count(column) > 1
    }

    if duplicates:
        raise ValueError(
            f"[{table_config['name']}] Duplicate Bronze columns "
            f"in mapping: {sorted(duplicates)}"
        )

    missing = [
        column
        for column in bronze_columns
        if column not in bronze_schema
    ]

    if missing:
        raise ValueError(
            f"[{table_config['name']}] Configured Bronze columns "
            f"do not exist in {table_config['bronze_table']}: "
            f"{missing}"
        )

    # Not config-driven, so it is checked separately -- without it the insert
    # would fail later with an opaque ClickHouse "no such column" error.
    if EVENT_ID_COLUMN not in bronze_schema:
        raise ValueError(
            f"[{table_config['name']}] {table_config['bronze_table']} has no "
            f"{EVENT_ID_COLUMN} column. Add it with: ALTER TABLE "
            f"{table_config['bronze_table']} ADD COLUMN {EVENT_ID_COLUMN} UUID "
            f"AFTER _ingested_at"
        )

    logger.info(
        "[%s] Bronze mapping validation successful. "
        "Configured columns=%d",
        table_config["name"],
        len(mapping),
    )


# Number of events used to validate source columns.
SOURCE_KEY_SAMPLE_SIZE = 100


def validate_source_keys(
    table_config: dict,
    rows,
):
    """
    Validate configured source columns against sampled Debezium events.

    Example:
        columns = {
            "id": "id",
            "name": "name",
        }

    Missing source columns fail the task.
    Unmapped source columns are logged as warnings.
    """

    source_columns = set(table_config["columns"])

    seen_keys: set[str] = set()
    payloads = 0

    for raw_row in rows[:SOURCE_KEY_SAMPLE_SIZE]:

        after = extract_after_payload(raw_row[0])

        if after is None:
            continue

        payloads += 1
        seen_keys.update(after)

    if not payloads:
        logger.warning(
            "[%s] Source key validation skipped: no event in the sample "
            "carried an `after` payload",
            table_config["name"],
        )
        return

    missing = sorted(source_columns - seen_keys)

    if missing:
        raise ValueError(
            f"[{table_config['name']}] Configured source columns are absent "
            f"from the Debezium `after` payload: {missing}. "
            f"These would silently load as empty/zero values. "
            f"Fix the mapping in raw_event_bronze_config.py, or remove "
            f"the column if the source table genuinely does not have it."
        )

    # Not a failure -- a source column simply may not be wanted in Bronze. But
    # it is how schema drift becomes visible instead of silently ignored.
    unmapped = sorted(seen_keys - source_columns)

    if unmapped:
        logger.warning(
            "[%s] Source columns present in the event but not mapped to "
            "Bronze: %s",
            table_config["name"],
            unmapped,
        )

    logger.info(
        "[%s] Source key validation successful against %d sampled event(s)",
        table_config["name"],
        payloads,
    )


# ----------------------------------------------------------------------
# Raw extraction
# ----------------------------------------------------------------------

def fetch_raw_events(
    client,
    raw_table: str,
    window_start: datetime,
    window_end: datetime,
    limit: int,
    last_ms: Optional[int] = None,
    last_id: Optional[str] = None,
):
    """
    Fetch one keyset page.

    Ordering/cursor:

        (event_time, id)

    This guarantees forward progress even when multiple events
    have the same event_time.
    """

    query = f"""
        SELECT
            raw,
            event_time,
            id,
            toUnixTimestamp64Milli(event_time) AS et_ms
        FROM {raw_table}
        WHERE event_time >= {{start:DateTime64(3)}}
          AND event_time < {{end:DateTime64(3)}}
    """

    params = {
        "start": window_start,
        "end": window_end,
        "limit": limit,
    }

    if last_ms is not None and last_id is not None:

        query += """
            AND (
                event_time,
                id
            ) >
            (
                fromUnixTimestamp64Milli({last_ms:Int64}),
                {last_id:UUID}
            )
        """

        params["last_ms"] = last_ms
        params["last_id"] = str(last_id)

    query += """
        ORDER BY event_time, id
        LIMIT {limit:UInt64}
    """

    result = client.query(
        query,
        parameters=params,
    )

    return result.result_rows


# ----------------------------------------------------------------------
# Type helpers
# ----------------------------------------------------------------------

def unwrap_clickhouse_type(ch_type: str) -> str:
    """
    Remove LowCardinality/Nullable wrappers.

    Examples:

        LowCardinality(String) -> String
        Nullable(Int64)        -> Int64
    """

    value = ch_type.strip()

    changed = True

    while changed:

        changed = False

        for wrapper in (
            "LowCardinality(",
            "Nullable(",
        ):
            if value.startswith(wrapper) and value.endswith(")"):
                value = value[len(wrapper):-1].strip()
                changed = True

    return value


def is_string_type(ch_type: str) -> bool:
    base_type = unwrap_clickhouse_type(ch_type)

    return (
        base_type == "String"
        or base_type.startswith("FixedString(")
    )


def is_integer_type(ch_type: str) -> bool:
    base_type = unwrap_clickhouse_type(ch_type)

    return base_type in {
        "Int8",
        "Int16",
        "Int32",
        "Int64",
        "Int128",
        "Int256",
        "UInt8",
        "UInt16",
        "UInt32",
        "UInt64",
        "UInt128",
        "UInt256",
    }


def is_float_type(ch_type: str) -> bool:
    base_type = unwrap_clickhouse_type(ch_type)

    return base_type in {
        "Float32",
        "Float64",
    }


def is_decimal_type(ch_type: str) -> bool:
    base_type = unwrap_clickhouse_type(ch_type)

    return base_type.startswith("Decimal")


def is_bool_type(ch_type: str) -> bool:
    return unwrap_clickhouse_type(ch_type) == "Bool"


def is_date32_type(ch_type: str) -> bool:
    return unwrap_clickhouse_type(ch_type) == "Date32"


def is_date_type(ch_type: str) -> bool:
    return unwrap_clickhouse_type(ch_type) == "Date"


def is_datetime_type(ch_type: str) -> bool:
    base_type = unwrap_clickhouse_type(ch_type)

    return (
        base_type == "DateTime"
        or base_type.startswith("DateTime64")
    )


# ----------------------------------------------------------------------
# Value conversion
# ----------------------------------------------------------------------

def json_to_string(value: Any) -> str:
    """
    Convert JSON/object values to a String suitable for ClickHouse.
    """

    if isinstance(value, str):
        return value

    return json.dumps(
        value,
        separators=(",", ":"),
        ensure_ascii=False,
        default=str,
    )


def debezium_date_to_date32(value: Any) -> Optional[date]:
    """
    Convert Debezium DATE values to Python date.

    Debezium DATE is typically days since 1970-01-01.
    """

    if value is None:
        return None

    if isinstance(value, date) and not isinstance(value, datetime):
        return value

    if isinstance(value, int):
        return date(1970, 1, 1).fromordinal(
            date(1970, 1, 1).toordinal() + value
        )

    if isinstance(value, str):

        try:
            return date.fromisoformat(value)
        except ValueError:
            return date(1970, 1, 1).fromordinal(
                date(1970, 1, 1).toordinal()
                + int(value)
            )

    raise ValueError(
        f"Cannot convert {value!r} to Date32"
    )


def coerce_value(
    value: Any,
    clickhouse_type: str,
) -> Any:
    """
    Convert a Debezium value to the target Bronze ClickHouse type.

    Examples:
        None + String  -> ""
        None + Int64   -> 0
        None + Bool    -> False
        dict + String  -> JSON string
        DATE           -> Python date
        timestamp      -> UTC datetime
    """

    base_type = unwrap_clickhouse_type(clickhouse_type)

    # --------------------------------------------------------------
    # NULL
    # --------------------------------------------------------------

    if value is None:

        if is_string_type(base_type):
            return ""

        if is_integer_type(base_type):
            return 0

        if is_float_type(base_type):
            return 0.0

        if is_decimal_type(base_type):
            return Decimal("0")

        if is_bool_type(base_type):
            return False

        return None

    # --------------------------------------------------------------
    # String
    # --------------------------------------------------------------

    if is_string_type(base_type):

        if isinstance(value, (dict, list)):
            return json_to_string(value)

        if isinstance(value, bool):
            return "true" if value else "false"

        return str(value)

    # --------------------------------------------------------------
    # Boolean
    # --------------------------------------------------------------

    if is_bool_type(base_type):

        if isinstance(value, bool):
            return value

        if isinstance(value, str):

            normalized = value.strip().lower()

            if normalized in {"true", "1", "yes"}:
                return True

            if normalized in {"false", "0", "no"}:
                return False

        return bool(value)

    # --------------------------------------------------------------
    # Integer
    # --------------------------------------------------------------

    if is_integer_type(base_type):

        if isinstance(value, bool):
            return int(value)

        return int(value)

    # --------------------------------------------------------------
    # Float
    # --------------------------------------------------------------

    if is_float_type(base_type):
        return float(value)

    # --------------------------------------------------------------
    # Decimal
    # --------------------------------------------------------------

    if is_decimal_type(base_type):
        return Decimal(str(value))

    # --------------------------------------------------------------
    # Date32
    # --------------------------------------------------------------

    if is_date32_type(base_type):
        return debezium_date_to_date32(value)

    # --------------------------------------------------------------
    # Date
    # --------------------------------------------------------------

    if is_date_type(base_type):

        if isinstance(value, date):
            return value

        if isinstance(value, int):
            return debezium_date_to_date32(value)

        return date.fromisoformat(str(value))

    # --------------------------------------------------------------
    # DateTime / DateTime64
    # --------------------------------------------------------------

    if is_datetime_type(base_type):

        if isinstance(value, datetime):
            return value

        if isinstance(value, str):

            return datetime.fromisoformat(
                value.replace("Z", "+00:00")
            )

        if isinstance(value, int):

            # Debezium timestamp values are commonly milliseconds, and they are
            # always UTC. tz must be passed explicitly: a bare
            # datetime.fromtimestamp() interprets the epoch value in the
            # worker's LOCAL timezone, which silently shifts every CDC
            # timestamp by the host's UTC offset.
            #
            # timezone.utc rather than datetime.UTC -- identical object, but
            # the alias only exists on Python 3.11+.
            return datetime.fromtimestamp(
                value / 1000,
                tz=timezone.utc,
            )

    # --------------------------------------------------------------
    # Unknown type
    # --------------------------------------------------------------

    return value


# ----------------------------------------------------------------------
# Transform
# ----------------------------------------------------------------------

def transform_page(
    rows,
    table_config: dict,
    bronze_schema: dict[str, str],
):
    """
    Transform one Raw Event page into Bronze rows.
    Uses table_config["columns"] for source -> Bronze mapping.
    """

    mapping = table_config["columns"]

    bronze_columns = [EVENT_ID_COLUMN] + list(mapping.values())

    bronze_rows = []

    skipped_events = 0

    for raw_row in rows:

        raw_value = raw_row[0]

        after = extract_after_payload(raw_value)

        if after is None:
            skipped_events += 1
            continue

        bronze_row = [raw_row[RAW_COL_EVENT_ID]]

        for source_column, bronze_column in mapping.items():

            value = after.get(source_column)

            target_type = bronze_schema[bronze_column]

            converted_value = coerce_value(
                value=value,
                clickhouse_type=target_type,
            )

            bronze_row.append(converted_value)

        bronze_rows.append(bronze_row)

    if skipped_events:
        logger.warning(
            "[%s] Skipped %d Raw events because "
            "`after` was null",
            table_config["name"],
            skipped_events,
        )

    return bronze_columns, bronze_rows, skipped_events


# ----------------------------------------------------------------------
# Insert
# ----------------------------------------------------------------------

def insert_bronze_page(
    client,
    bronze_table: str,
    bronze_columns: list[str],
    bronze_rows: list[list[Any]],
):
    """
    Insert one transformed page into Bronze.
    """

    if not bronze_rows:
        return 0

    client.insert(
        bronze_table,
        bronze_rows,
        column_names=bronze_columns,
    )

    return len(bronze_rows)


# ----------------------------------------------------------------------
# Main processor
# ----------------------------------------------------------------------

def process_table(
    table_config: dict,
    window_start: str,
    window_end: str,
):
    """
    Process one Raw -> Bronze table for the given time window.

    Example:
        process_table(
            table_config=table_config,
            window_start="2026-09-07T10:00:00+00:00",
            window_end="2026-09-07T11:00:00+00:00",
        )

    Flow:
        Raw events
            -> keyset pages
            -> Debezium `after`
            -> source/Bronze mapping
            -> type conversion
            -> Bronze insert
    """

    start_time = monotonic()

    table_name = table_config["name"]
    raw_table = table_config["raw_table"]
    bronze_table = table_config["bronze_table"]
    page_size = table_config.get("page_size", 10000)

    window_start_dt = datetime.fromisoformat(
        window_start
    )

    window_end_dt = datetime.fromisoformat(
        window_end
    )

    logger.info("=" * 80)
    logger.info("Starting Raw -> Bronze processing")
    logger.info("Table        : %s", table_name)
    logger.info("Raw table    : %s", raw_table)
    logger.info("Bronze table : %s", bronze_table)
    logger.info("Window start : %s", window_start_dt)
    logger.info("Window end   : %s", window_end_dt)
    logger.info("Page size    : %s", page_size)
    logger.info("=" * 80)

    client = get_clickhouse_client()

    # --------------------------------------------------------------
    # Validate Bronze schema before reading data
    # --------------------------------------------------------------

    logger.info(
        "[%s] Reading Bronze schema from system.columns",
        table_name,
    )

    bronze_schema = get_bronze_schema(
        client=client,
        bronze_table=bronze_table,
    )

    if not bronze_schema:
        raise ValueError(
            f"[{table_name}] Bronze table does not exist "
            f"or has no columns: {bronze_table}"
        )

    validate_table_mapping(
        table_config=table_config,
        bronze_schema=bronze_schema,
    )

    # --------------------------------------------------------------
    # Keyset cursor
    # --------------------------------------------------------------

    last_ms = None
    last_id = None

    page_number = 0
    total_raw_rows = 0
    total_bronze_rows = 0
    total_skipped = 0

    # --------------------------------------------------------------
    # Page loop
    # --------------------------------------------------------------

    while True:

        page_number += 1

        logger.info(
            "[%s] Fetching page %d",
            table_name,
            page_number,
        )

        rows = fetch_raw_events(
            client=client,
            raw_table=raw_table,
            window_start=window_start_dt,
            window_end=window_end_dt,
            limit=page_size,
            last_ms=last_ms,
            last_id=last_id,
        )

        if not rows:

            page_number -= 1

            logger.info(
                "[%s] No more Raw events in window",
                table_name,
            )

            break

        total_raw_rows += len(rows)

        logger.info(
            "[%s] Page %d fetched %d Raw events",
            table_name,
            page_number,
            len(rows),
        )

        # Once per run, on real event data -- the Bronze side was already
        # checked before the loop, but the source side needs an actual event
        # to check against.
        if page_number == 1:
            validate_source_keys(
                table_config=table_config,
                rows=rows,
            )

        bronze_columns, bronze_rows, skipped = transform_page(
            rows=rows,
            table_config=table_config,
            bronze_schema=bronze_schema,
        )

        total_skipped += skipped

        inserted = insert_bronze_page(
            client=client,
            bronze_table=bronze_table,
            bronze_columns=bronze_columns,
            bronze_rows=bronze_rows,
        )

        total_bronze_rows += inserted

        last_row = rows[-1]

        last_ms = last_row[3]
        last_id = last_row[2]

        logger.info(
            "[%s] Page %d completed | "
            "Raw=%d | Bronze=%d | "
            "last_event_time=%s | "
            "last_id=%s",
            table_name,
            page_number,
            len(rows),
            inserted,
            last_row[1],
            last_id,
        )

    duration = monotonic() - start_time

    # Logged before the reconciliation check below, so the counts are visible
    # in the task log even on the failure path.
    logger.info("=" * 80)
    logger.info("Raw -> Bronze processing completed")
    logger.info("Table              : %s", table_name)
    logger.info("Raw table          : %s", raw_table)
    logger.info("Bronze table       : %s", bronze_table)
    logger.info("Pages              : %d", page_number)
    logger.info("Raw events         : %d", total_raw_rows)
    logger.info("Events with after  : %d", total_raw_rows - total_skipped)
    logger.info("Bronze inserted    : %d", total_bronze_rows)
    logger.info("Skipped (no after) : %d", total_skipped)
    logger.info("Duration           : %.2f seconds", duration)
    logger.info("=" * 80)

    # Fail if any raw event has no `after` payload.
    # Allow with: "allow_null_after": True
    if total_skipped and not table_config.get("allow_null_after", False):
        raise ValueError(
            f"[{table_name}] {total_skipped} of {total_raw_rows} raw events had "
            f"a null `after` payload and were dropped. Only CREATE/UPDATE events "
            f"are expected here, and both carry a row image. Investigate the "
            f"topic before re-running; set \"allow_null_after\": True on this "
            f"table in raw_event_bronze_config.py to accept the loss and "
            f"proceed."
        )

    return {
        "table": table_name,
        "raw_table": raw_table,
        "bronze_table": bronze_table,
        "pages": page_number,
        "raw_rows": total_raw_rows,
        "events_with_after": total_raw_rows - total_skipped,
        "bronze_rows": total_bronze_rows,
        "skipped_no_after": total_skipped,
        "duration_seconds": round(duration, 2),
    }