"""
Read-only Postgres client for the reconciliation check.

Loads clientreferenceid sets and full detail rows. Large id loads use
server-side cursors so they stream rather than load into memory.
"""

import gc
import os
import warnings

import pandas as pd
import psycopg2

from hcm_data_healer.core.utils.ids import pack

# read_sql works with a raw psycopg2 connection; silence pandas' SQLAlchemy warning.
warnings.filterwarnings(
    "ignore",
    message="pandas only supports SQLAlchemy connectable.*",
)


STATEMENT_TIMEOUT_MS = 30 * 60 * 1000     # 30 min per query


class DBClient:
    def __init__(self, db_config):
        self.db_config = dict(db_config)

    def _connect(self):
        # A hung query must fail rather than wait for the task timeout.
        return psycopg2.connect(**self.db_config,
                                options=f"-c statement_timeout={STATEMENT_TIMEOUT_MS}")

    def existing_ids(self, entity, ids, chunk=1000):
        """Ids with any row in the entity table, deleted or not, any date.

        Pre-CREATE safety check: a deleted or out-of-window record can look like
        a ghost, and creating it would resurrect or duplicate it."""
        found = set()
        ids = [str(i) for i in ids if i]
        conn = self._connect()
        try:
            cur = conn.cursor()
            for i in range(0, len(ids), chunk):
                cur.execute(f"SELECT {entity['db_id_col']} FROM {entity['db_table']} "
                            f"WHERE {entity['db_id_col']} = ANY(%s)", (ids[i:i + chunk],))
                found.update(str(r[0]) for r in cur.fetchall() if r[0])
        finally:
            conn.close()
        return found

    def load_ids(self, entity, gte_ms, lte_ms, progress=None):
        """Return the set of clientreferenceids for one entity in the date window."""
        # Date column is aligned per entity with the ES window (see entities.py).
        date_col = entity.get("db_date_col", "createdtime")
        query = f"""
            SELECT {entity['db_id_col']}
            FROM   {entity['db_table']}
            WHERE  isdeleted   = false
              AND  {date_col} >= {gte_ms}
              AND  {date_col} <= {lte_ms}
              {entity['extra_db_where']}
        """
        ids = set()
        conn = self._connect()
        try:
            cur = conn.cursor(name=f"cur_{entity['folder']}")
            cur.itersize = 100000
            cur.execute(query)
            total = 0
            while True:
                rows = cur.fetchmany(100000)
                if not rows:
                    break
                for row in rows:
                    if row[0]:
                        ids.add(pack(row[0]))   # compact key, see utils/ids.py
                total += len(rows)
                if progress:
                    progress(total, len(ids))
                # No per-batch gc.collect(); the caller collects once per entity.
            cur.close()
        finally:
            conn.close()
        return ids

    def fetch_details(self, entity, ids, batch=3000, progress=None):
        """Return a DataFrame of full DB rows for the given ids (isdeleted=false)."""
        ids = [i for i in ids if i]
        if not ids:
            return pd.DataFrame()

        frames = []
        conn = self._connect()
        try:
            for i in range(0, len(ids), batch):
                chunk = ids[i:i + batch]
                placeholders = ",".join(["%s"] * len(chunk))
                q = f"""
                    SELECT * FROM {entity['db_table']}
                    WHERE {entity['db_id_col']} IN ({placeholders})
                      AND isdeleted = false
                """
                frames.append(pd.read_sql(q, conn, params=chunk))
                if progress:
                    progress(min(i + batch, len(ids)), len(ids))
                gc.collect()
        finally:
            conn.close()
        return pd.concat(frames, ignore_index=True) if frames else pd.DataFrame()

    def fetch_details_to_csv(self, entity, ids, out_path, batch=3000, progress=None):
        """Stream full DB rows to CSV batch by batch; returns rows written."""
        ids = [i for i in ids if i]
        if not ids:
            return 0
        conn = self._connect()
        written = 0
        header = True
        try:
            for i in range(0, len(ids), batch):
                chunk = ids[i:i + batch]
                placeholders = ",".join(["%s"] * len(chunk))
                q = f"""
                    SELECT * FROM {entity['db_table']}
                    WHERE {entity['db_id_col']} IN ({placeholders})
                      AND isdeleted = false
                """
                dfb = pd.read_sql(q, conn, params=chunk)
                dfb.to_csv(out_path, mode="w" if header else "a", header=header, index=False)
                header = False
                written += len(dfb)
                del dfb
                if progress:
                    progress(min(i + batch, len(ids)), len(ids))
                gc.collect()
        finally:
            conn.close()
        return written

    def test_connection(self):
        """Cheap connectivity probe. Returns (ok, message)."""
        try:
            conn = self._connect()
            cur = conn.cursor()
            cur.execute("SELECT 1")
            cur.fetchone()
            cur.close()
            conn.close()
            return True, "DB connection OK"
        except Exception as exc:
            return False, f"DB connection failed: {exc}"
