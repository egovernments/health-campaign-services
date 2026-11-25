#!/usr/bin/env python3
"""
Individual service: migration of stored PII to encrypted form (backup, schema change, backfill, verify, rollback, clean-up).

EASIEST: one guided command does everything in order and stops at the first problem:

  python3 pii_migrate.py migrate --schema health --state-tenant dev \
      --enc-host http://egov-enc-service.egov:8080 --host <db host> --dbname <db name>

  It asks for the database user id and password (the password is not shown), the hash secret (twice, not shown),
  the database name to confirm, and whether the individual service is stopped. Then: preflight, backup, database
  script, backfill, verify of every row. If it stops (or the connection drops), fix the cause and run the same
  command again: it continues where it stopped (each step is skipped when already done).

The same steps as single commands, each one when the previous one has passed:

  preflight   read-only checks (connection, tables, hash secret, enc-service, every key id in the data decrypts)
  baseline    OLD schema, before anything changes: saves the counts to a file          (writes a local file only)
  backup      OLD schema: copies the individual's rows into a backup schema           (writes: backup schema)
  schema      applies the database script in ONE transaction                          (writes: tables)
  backfill    encrypts and hashes in batches; first with --dry-run (writes nothing)   (writes: tables)
  verify      compares with the baseline file, checks nothing plain is left, and decrypts a sample (or with
              --full every row) to compare it with the backup (values and hashes)       (read-only)
  rollback    only if a check fails before the new image takes traffic: puts the tables back exactly as in the
              backup, old column types included; three steps, each skipped when already done, so a
              rollback that stopped can be run again                                   (writes: tables)
  cleanup     after the rollback period: drops the 8 old indexes and the backup schema (writes)

The individual service (API and its Kafka consumers) must be stopped from "backup" to the release: the old
code cannot read encrypted rows, and rows that change while the backfill runs are skipped (run it again).
Every command that writes asks for --confirm <database name> so it cannot run against the wrong database.

Connection: --host --port --dbname (or PGHOST PGPORT PGDATABASE). On a terminal the database user id and
password are always asked. Without a terminal (automated runs) they come from PGUSER PGPASSWORD or ~/.pgpass.
Hash secret: asked on a terminal (or PII_HASH_SECRET): the same value as the service's Kubernetes secret. It is
never printed; a fingerprint is shown to compare. The database script (individual_pii_schema.sql) must be next to
this script, or be given with --ddl. It is not a Flyway migration: every environment runs it through this script.
Needs: Python 3.8+, psycopg2 (pip install psycopg2-binary). enc-service must be reachable (--enc-host).

Example (dev): --schema health --state-tenant dev --enc-host http://egov-enc-service.egov:8080
  pii_migrate.py preflight --schema health --state-tenant dev --enc-host http://egov-enc-service.egov:8080
  pii_migrate.py baseline  --schema health --out baseline-dev.json
  pii_migrate.py backup    --schema health --confirm <database-name>
  pii_migrate.py schema    --schema health --confirm <database-name>
  pii_migrate.py backfill  --schema health --state-tenant dev --enc-host ... --dry-run
  pii_migrate.py backfill  --schema health --state-tenant dev --enc-host ... --confirm <database-name>
  pii_migrate.py verify    --schema health --state-tenant dev --enc-host ... --baseline baseline-dev.json
Exit code 0 = passed, anything else = stop and read the message. Every command also appends to --log.

Household service (--entity household; the default is individual): the same tool encrypts the 8 address text fields
(doorno, addressline1, addressline2, landmark, city, pincode, buildingname, street) of the address rows that
health.household points to. latitude and longitude stay plain. Run it AFTER the individual migration: the address
table is shared, and the individual 'schema' step makes its columns TEXT (household preflight/backup/backfill stop
with "run the individual migration first" otherwise). The commands are the same, with these differences: no hash
secret is asked, no database script ('schema' only checks the column types), no 'baseline' (the backup keeps the
counts), no --drop-old-indexes, the backup schema is pii_backup_household, and the service to stop is the household
service. Rollback puts the 8 address columns of the backed-up rows back; it never changes column types or the
individual's rows (address rows that an individual also uses are not restored). After a backfill and after a
rollback the tool refreshes the materialized view household_address_mv (a copy of the household address fields).
The INDIVIDUAL rollback is refused once the household migration has changed the address table: roll back the
household first (and drop its backup), or fix forward.
  pii_migrate.py migrate --entity household --schema health --state-tenant dev --enc-host ... --host ... --dbname ...

Encrypted form: enc-service cipher text "<keyId>|<base64>" in the same columns (they become TEXT; date of
birth becomes ISO text yyyy-MM-dd). Hash = lowercase hex HMAC-SHA256(secret, "<field>:<value>"), the value
trimmed like Java String.trim() and the given name lower-cased: the same rule as PiiHasher.java.
"""
import argparse
import base64
import datetime
import getpass
import hashlib
import hmac
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request

import psycopg2
import psycopg2.extras

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DDL = os.path.join(SCRIPT_DIR, "individual_pii_schema.sql")

CIPHER_RE = re.compile(r"^[A-Za-z0-9-]{1,64}\|[A-Za-z0-9+/]+={0,2}$")
# the same rule in SQL as is_cipher: key id, one '|', then a base64 part of 6 or more groups of 4 characters (length a
# multiple of 4 and at least 24), the last group possibly padded with '=' ('12|3456' is a plain value, not cipher text)
CIPHER_SQL = (r"'^[A-Za-z0-9-]{1,64}\|(?:[A-Za-z0-9+/]{4}){5,}"
              r"(?:[A-Za-z0-9+/]{4}|[A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)$'")

IND_TEXT = ["givenname", "familyname", "othernames", "fathername", "husbandname", "mobilenumber",
            "altcontactnumber", "email", "username", "gender", "bloodgroup", "dateofbirth"]
# hash column -> (source column, PiiHasher field name)
IND_HASH = {"mobilenumberhash": ("mobilenumber", "mobileNumber"),
            "givennamehash": ("givenname", "givenName"),
            "usernamehash": ("username", "username")}
# columns the database script changes to TEXT (mobilenumber is already varchar(256) and holds cipher text today)
IND_TO_TEXT = [c for c in IND_TEXT if c != "mobilenumber"]
ADDR_TEXT = ["doorno", "addressline1", "addressline2", "landmark", "city", "pincode", "buildingname", "street"]
HASH_INDEXES = ["idx_individual_mobilenumberhash", "idx_individual_givennamehash", "idx_individual_usernamehash",
                "idx_individual_identifier_identifieridhash"]
# created without quotes, so Postgres stores the names in lower case
OLD_INDEXES = ["idx_individual_givenname", "idx_individual_familyname", "idx_individual_othernames",
               "idx_individual_dateofbirth", "idx_individual_gender", "idx_individual_mobilenumber",
               "idx_individual_username", "idx_individual_identifier_identifierid"]
# the year may have more than 4 digits (to_char of the date 10000-01-01); ASCII digits only, as in Java
DOB_PREFIX = re.compile(r"^([0-9]{4,}-[0-9]{2}-[0-9]{2})")
LOWERCASE_FIELDS = {"givenName"}  # username matches exactly, as before
MV = "household_address_mv"
DEFAULT_BACKUP = {"individual": "pii_backup_individual", "household": "pii_backup_household"}


# ============================================================================ helpers
class Fail(Exception):
    pass


class RunAgain(Fail):
    """The step finished, but some rows must be done again (running the same step again does them)."""


INTERACTIVE = False  # set in main(): True when a person runs the script on a terminal


def ask(prompt, secret=False):
    """One answer from the person at the terminal; a secret is not shown while typed."""
    while True:
        value = getpass.getpass(prompt + ": ") if secret else input(prompt + ": ").strip()
        if value:
            return value
        print("an answer is needed")


def ask_yes(args, question):
    """A yes/no question; --yes answers it (automated runs only)."""
    if args.yes:
        return
    if not INTERACTIVE:
        raise Fail(f"no terminal to ask: {question} (automated runs pass --yes)")
    if input(question + " Type yes to go on: ").strip().lower() != "yes":
        raise Fail("stopped: the answer was not yes")


def secret_fingerprint():
    return hashlib.sha256(os.environ.get("PII_HASH_SECRET", "").encode("utf-8")).hexdigest()[:12]


def ensure_hash_secret():
    """Asks for the hash secret on a terminal when PII_HASH_SECRET is not set (typed twice, not shown)."""
    if os.environ.get("PII_HASH_SECRET") or not INTERACTIVE:
        return
    while True:
        first = ask("Hash secret (Kubernetes secret health-individual-pii, key hash-secret)", secret=True)
        if getpass.getpass("Type the hash secret again: ") == first:
            os.environ["PII_HASH_SECRET"] = first  # this process only
            return
        print("the two values differ; try again")


def connect(args):
    """Host, port and database from the options (or PGHOST PGPORT PGDATABASE). On a terminal the database user id and
    password are always asked, three tries. Without a terminal they come from PGUSER PGPASSWORD or ~/.pgpass."""
    host = args.host or os.environ.get("PGHOST")
    port = args.port or os.environ.get("PGPORT") or "5432"
    dbname = args.dbname or os.environ.get("PGDATABASE")
    # keepalives: a long statement must not be cut by an idle timeout on the network path
    common = dict(port=port, application_name="pii_migrate", connect_timeout=15,
                  keepalives=1, keepalives_idle=30, keepalives_interval=10, keepalives_count=6)
    if not INTERACTIVE:
        if not (host and dbname):
            raise Fail("give --host and --dbname (or PGHOST and PGDATABASE)")
        return psycopg2.connect(host=host, dbname=dbname, **common)
    host = host or ask("Database host")
    dbname = dbname or ask("Database name")
    for attempt in range(1, 4):
        user = ask(f"Database user id for {dbname} on {host} (owner of the {args.entity} tables)")
        password = getpass.getpass(f"Password for {user}: ")
        try:
            return psycopg2.connect(host=host, dbname=dbname, user=user, password=password, **common)
        except psycopg2.OperationalError as e:
            print(f"could not connect ({attempt} of 3): {str(e).strip().splitlines()[-1]}")
    raise Fail("could not connect to the database")


def is_cipher(v):
    if not isinstance(v, str) or not CIPHER_RE.match(v):
        return False
    b64 = v.split("|", 1)[1]
    if len(b64) % 4:
        return False
    try:
        return len(base64.b64decode(b64)) >= 16
    except ValueError:
        return False


def java_trim(s):
    # Java String.trim() removes code points <= U+0020 only (Python strip() removes more)
    i, j = 0, len(s)
    while i < j and ord(s[i]) <= 32:
        i += 1
    while j > i and ord(s[j - 1]) <= 32:
        j -= 1
    return s[i:j]


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc)


JAVA_SPACE_CONTROLS = set("\t\n\x0b\x0c\r\x1c\x1d\x1e\x1f")
JAVA_NO_BREAK_SPACES = set("\u00a0\u2007\u202f")


def java_is_whitespace(ch):
    # Java Character.isWhitespace: Unicode space, line and paragraph separators except the no-break spaces, plus
    # tab, line feed, vertical tab, form feed, carriage return and U+001C..U+001F
    if ch in JAVA_SPACE_CONTROLS:
        return True
    return unicodedata.category(ch) in ("Zs", "Zl", "Zp") and ch not in JAVA_NO_BREAK_SPACES


def blank(v):
    # Java StringUtils.isBlank, which the service uses to leave a value plain and unhashed
    return v is None or (isinstance(v, str) and all(java_is_whitespace(c) for c in v))


# the same rule in SQL: a value matching this regex is blank for Java (used instead of btrim(x) <> '')
JAVA_BLANK_SQL = "'^[" + "".join(f"\\u{c:04x}" for c in range(0x10000) if java_is_whitespace(chr(c))) + "]*$'"


class Log:
    def __init__(self, path):
        self.path = path

    def __call__(self, msg, **data):
        line = {"time_utc": utc_now().strftime("%Y-%m-%dT%H:%M:%SZ"), "msg": msg}
        line.update(data)
        text = json.dumps(line, default=str)
        print(text if data else f"{line['time_utc']}  {msg}", flush=True)
        with open(self.path, "a") as f:
            f.write(text + "\n")


class Enc:
    """enc-service client: one call per batch, retried with back-off, then the command stops."""

    def __init__(self, host, tenant):
        if not host or not tenant:
            raise Fail("--enc-host and --state-tenant are required for this command")
        self.host = host.rstrip("/")
        self.tenant = tenant
        self.calls = 0

    def _post(self, path, body, retries=5):
        req = urllib.request.Request(self.host + path, data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
        for attempt in range(retries):
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    self.calls += 1
                    return json.loads(r.read())
            except Exception as e:
                if attempt == retries - 1:
                    raise Fail(f"enc-service call {path} failed: {type(e).__name__}: {getattr(e, 'code', '')}")
                time.sleep(2 ** attempt)

    def encrypt(self, values):
        if not values:
            return []
        res = self._post("/egov-enc-service/crypto/v1/_encrypt", {"encryptionRequests": [
            {"tenantId": self.tenant, "type": "Normal", "value": values}]})
        out = res[0]
        if len(out) != len(values) or not all(is_cipher(c) for c in out):
            raise Fail("enc-service returned something that is not cipher text")
        return out

    def decrypt(self, ciphers, retries=5):
        if not ciphers:
            return []
        if not all(is_cipher(c) for c in ciphers):  # only cipher text is sent to decrypt
            raise Fail("refusing to send a non-cipher value to enc-service decrypt")
        out = self._post("/egov-enc-service/crypto/v1/_decrypt", ciphers, retries)
        if len(out) != len(ciphers):
            raise Fail("enc-service decrypt returned a different number of values")
        return out


class Hasher:
    def __init__(self):
        secret = os.environ.get("PII_HASH_SECRET", "")
        if not secret:
            raise Fail("PII_HASH_SECRET is not set (use the same value as the service's secret)")
        self.secret = secret.encode("utf-8")

    def __call__(self, field, value):
        if blank(value):
            return None
        norm = java_trim(value)
        if field in LOWERCASE_FIELDS:
            norm = norm.lower()
        return hmac.new(self.secret, f"{field}:{norm}".encode("utf-8"), hashlib.sha256).hexdigest()


def q1(conn, sql, params=None):
    with conn.cursor() as cur:
        cur.execute(sql, params)
        row = cur.fetchone()
        return row[0] if row else None


def col_type(conn, schema, table, col):
    return q1(conn, "SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid "
                    "JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = %s AND c.relname = %s "
                    "AND a.attname = %s AND NOT a.attisdropped", (schema, table, col))


def schema_state(conn, schema):
    """'old' (before the database script), 'new' (after), or a message saying what is wrong."""
    dob = col_type(conn, schema, "individual", "dateofbirth")
    hashes = [col_type(conn, schema, "individual", h) for h in IND_HASH] + \
             [col_type(conn, schema, "individual_identifier", "identifieridhash")]
    if dob is None:
        return f"table {schema}.individual not found"
    if dob == "text" and all(hashes):
        return "new"
    if dob != "text" and not any(hashes):
        return "old"
    return f"half-changed (dateofbirth {dob}, hash columns {hashes}): apply the database script again"


def target(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT current_database(), current_user, inet_server_addr()::text, inet_server_port(), "
                    "current_setting('server_version')")
        return dict(zip(["database", "user", "server", "port", "version"], cur.fetchone()))


def require_confirm(args, conn):
    db = q1(conn, "SELECT current_database()")
    if args.confirm is None and INTERACTIVE:
        t = target(conn)
        args.confirm = input(f"This changes database '{db}' on server {t['server']} (schema {args.schema}). "
                             f"Type the database name to go on: ").strip()
    if args.confirm != db:
        raise Fail(f"this command changes database '{db}'. Re-run it with --confirm {db}")


def addr_rows(schema):
    return (f"FROM {schema}.address a WHERE EXISTS (SELECT 1 FROM {schema}.individual_address ia "
            f"WHERE ia.addressid = a.id)")


def health_values_sql(schema, alias="i"):
    """Non-empty values of the health keys inside individual.additionaldetails.fields (jsonb)."""
    return (f"FROM {schema}.individual {alias}, jsonb_array_elements(CASE WHEN jsonb_typeof({alias}.additionaldetails->'fields') "
            f"= 'array' THEN {alias}.additionaldetails->'fields' ELSE '[]'::jsonb END) f "
            f"WHERE f->>'key' = ANY(%(keys)s) AND f->>'value' IS NOT NULL AND f->>'value' !~ {JAVA_BLANK_SQL}")


# ============================================================================ counts
def snapshot(conn, schema, health_keys):
    """Counts that must be the same before and after: rows, and non-null values per PII column."""
    q = {"individual.rows": f"SELECT count(*) FROM {schema}.individual",
         "identifier.rows": f"SELECT count(*) FROM {schema}.individual_identifier",
         "individual_address.rows": f"SELECT count(*) FROM {schema}.individual_address",
         "address.individual_rows": f"SELECT count(*) {addr_rows(schema)}",
         "identifier.identifierid_present": f"SELECT count(*) FROM {schema}.individual_identifier WHERE identifierid IS NOT NULL"}
    for c in IND_TEXT:
        q[f"individual.{c}_present"] = f"SELECT count(*) FROM {schema}.individual WHERE {c} IS NOT NULL"
    for c in ADDR_TEXT + ["latitude", "longitude"]:
        q[f"address.{c}_present"] = f"SELECT count(*) {addr_rows(schema)} AND {c} IS NOT NULL"
    q["individual.health_values_present"] = f"SELECT count(*) {health_values_sql(schema)}"
    out = {}
    with conn.cursor() as cur:
        for k, sql in q.items():
            cur.execute(sql, {"keys": sorted(health_keys)})
            out[k] = cur.fetchone()[0]
    return out


def leftovers(conn, schema, health_keys, enc):
    """Must all be zero after a complete backfill. A cipher value whose plain value is blank (written by the
    old service when it encrypted an empty field) rightly has no hash; it is decrypted to tell it apart."""
    q = {}
    for c in IND_TEXT:
        q[f"individual.{c}_still_plain"] = (f"SELECT count(*) FROM {schema}.individual WHERE {c} IS NOT NULL "
                                            f"AND {c} !~ {JAVA_BLANK_SQL} AND {c} !~ {CIPHER_SQL}")
    q["identifier.identifierid_still_plain"] = (f"SELECT count(*) FROM {schema}.individual_identifier WHERE identifierid "
                                                f"IS NOT NULL AND identifierid !~ {JAVA_BLANK_SQL} AND identifierid !~ {CIPHER_SQL}")
    for c in ADDR_TEXT:
        q[f"address.{c}_still_plain"] = (f"SELECT count(*) {addr_rows(schema)} AND {c} IS NOT NULL AND {c} !~ {JAVA_BLANK_SQL} "
                                         f"AND {c} !~ {CIPHER_SQL}")
    q["individual.health_values_still_plain"] = f"SELECT count(*) {health_values_sql(schema)} AND f->>'value' !~ {CIPHER_SQL}"
    out = {}
    with conn.cursor() as cur:
        for k, sql in q.items():
            cur.execute(sql, {"keys": sorted(health_keys)})
            out[k] = cur.fetchone()[0]
        # hashes: plain source without hash is always missing; cipher source without hash is missing unless blank
        missing = [(h, "individual", src) for h, (src, _) in IND_HASH.items()] + \
                  [("identifieridhash", "individual_identifier", "identifierid")]
        for h, table, src in missing:
            cur.execute(f"SELECT {src} FROM {schema}.{table} WHERE {src} IS NOT NULL AND {src} !~ {JAVA_BLANK_SQL} "
                        f"AND {h} IS NULL")
            values = [r[0] for r in cur.fetchall()]
            plain = [v for v in values if not is_cipher(v)]
            ciphers = [v for v in values if is_cipher(v)]
            blank_inside = 0
            for i in range(0, len(ciphers), 500):
                blank_inside += sum(1 for p in enc.decrypt(ciphers[i:i + 500]) if blank(p))
            out[f"{table}.{h}_missing"] = len(plain) + len(ciphers) - blank_inside
            out[f"{table}.{h}_not_needed_cipher_of_blank"] = blank_inside
    return out


# ============================================================================ preflight
def cmd_preflight(args, conn, log):
    if args.entity == "household":
        return hh_preflight(args, conn, log)
    problems = []
    t = target(conn)
    log("target", **t, schema=args.schema)
    for table in ["individual", "individual_identifier", "individual_address", "address"]:
        if q1(conn, "SELECT to_regclass(%s)", (f"{args.schema}.{table}",)) is None:
            problems.append(f"table {args.schema}.{table} not found")
    if problems:
        raise Fail("; ".join(problems))
    if not os.path.isfile(args.ddl):
        problems.append(f"database script not found: {args.ddl} (copy it next to the script as in the repo, "
                        f"or give --ddl PATH)")
    state = schema_state(conn, args.schema)
    log("schema state", state=state)
    if state not in ("old", "new"):
        problems.append(state)
    # rights the later commands need: schema/rollback change the tables (owner), backup creates a schema
    with conn.cursor() as cur:
        for table in ["individual", "individual_identifier", "individual_address", "address"]:
            cur.execute("SELECT tableowner, pg_has_role(current_user, tableowner, 'USAGE') FROM pg_tables "
                        "WHERE schemaname = %s AND tablename = %s", (args.schema, table))
            owner, is_owner = cur.fetchone()
            if not is_owner:
                problems.append(f"user {t['user']} does not own {args.schema}.{table} (owner {owner}): "
                                f"'schema' and 'rollback' cannot change it - run as the owner")
        cur.execute("SELECT has_database_privilege(current_user, current_database(), 'CREATE')")
        if not cur.fetchone()[0]:
            problems.append(f"user {t['user']} cannot create a schema in this database: 'backup' needs that right")
        cur.execute("SELECT has_database_privilege(current_user, current_database(), 'TEMP')")
        if not cur.fetchone()[0]:
            problems.append(f"user {t['user']} cannot create temporary tables: the database script needs that right")
    try:
        Hasher()
        if len(os.environ["PII_HASH_SECRET"]) < 32:
            log("WARNING: PII_HASH_SECRET is shorter than 32 characters")
        log("hash secret fingerprint (first 12 hex of its SHA-256; compare with the service's secret)",
            fingerprint=secret_fingerprint())
    except Fail as e:
        problems.append(str(e))
    enc = Enc(args.enc_host, args.state_tenant)
    probe = "pii-migrate-preflight"
    back = enc.decrypt(enc.encrypt([probe]))
    if back != [probe]:
        problems.append("enc-service encrypt/decrypt round trip gave a different value")
    else:
        log("enc-service round trip OK", tenant=args.state_tenant)
    # every key id already in the data must decrypt, or the backfill stops half-way
    sources = [("individual", c) for c in IND_TEXT] + [("individual_identifier", "identifierid")] + \
              [("address", c) for c in ADDR_TEXT]
    keys = {}
    with conn.cursor() as cur:
        for table, c in sources:
            v = f"{c}::text"  # date of birth is still a date on the old schema
            cur.execute(f"SELECT DISTINCT ON (split_part({v}, '|', 1)) split_part({v}, '|', 1), {v} "
                        f"FROM {args.schema}.{table} WHERE {v} ~ {CIPHER_SQL}")
            for key_id, sample in cur.fetchall():
                keys.setdefault(key_id, sample)
    for key_id, sample in sorted(keys.items()):
        try:
            enc.decrypt([sample], retries=1)
            log("existing key id decrypts", key_id=key_id)
        except Fail as e:
            problems.append(f"values encrypted with key id {key_id} cannot be decrypted ({e})")
    if q1(conn, "SELECT to_regclass(%s)", (f"{args.schema}.{MV}",)) is not None:
        # the database script drops and recreates the view: that needs its owner's or the schema owner's rights, and
        # the new view belongs to this user with no grants (owner and grants are not copied)
        with conn.cursor() as cur:
            cur.execute("SELECT pg_get_userbyid(c.relowner), pg_get_userbyid(n.nspowner), c.relacl::text, "
                        "pg_has_role(current_user, c.relowner, 'USAGE') OR pg_has_role(current_user, n.nspowner, 'USAGE'), "
                        "has_schema_privilege(current_user, n.oid, 'CREATE') "
                        "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = %s AND c.relname = %s",
                        (args.schema, MV))
            mv_owner, schema_owner, grants, can_drop, can_create = cur.fetchone()
        log(f"{MV} exists: the database script drops and rebuilds it (it refreshes the view's data)",
            owner=mv_owner, grants=grants)
        if not can_drop:
            problems.append(f"user {t['user']} cannot drop {args.schema}.{MV} (owner {mv_owner}, schema owner "
                            f"{schema_owner}), which the database script rebuilds: run as {mv_owner} or the schema owner, "
                            f"or ask the database admin to make {t['user']} its owner")
        elif not can_create:
            problems.append(f"user {t['user']} cannot create objects in schema {args.schema}: the database script "
                            f"recreates {MV} there")
        elif mv_owner != t["user"] or grants:
            log(f"WARNING: after the database script {MV} belongs to {t['user']} with no grants (now: owner "
                f"{mv_owner}, grants {grants}). If another database user reads or refreshes it (referral), give it "
                f"the same rights again after the migration")
    sessions = []
    with conn.cursor() as cur:
        cur.execute("SELECT usename, coalesce(application_name, ''), count(*) FROM pg_stat_activity "
                    "WHERE datname = current_database() AND pid <> pg_backend_pid() GROUP BY 1, 2 ORDER BY 3 DESC")
        sessions = cur.fetchall()
    log("other sessions on this database (stop the individual service before 'backup')",
        sessions=[{"user": u, "application": a, "count": n} for u, a, n in sessions])
    # change data capture (for example Debezium) reads these tables from the WAL: the backfill sends it one change per row,
    # and the server keeps the WAL until every logical slot has read it
    with conn.cursor() as cur:
        cur.execute("SELECT pubname, tablename FROM pg_publication_tables WHERE schemaname = %s AND tablename = ANY(%s) "
                    "ORDER BY 1, 2", (args.schema, ["individual", "individual_identifier", "individual_address", "address"]))
        pubs = cur.fetchall()
        cur.execute("SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) "
                    "FROM pg_replication_slots WHERE slot_type = 'logical' AND database = current_database() ORDER BY 1")
        slots = cur.fetchall()
    if pubs or slots:
        log("WARNING: change data capture reads these tables. The backfill sends it one change per row (values become "
            "cipher text, dateofbirth becomes text): tell the team that reads it first, check free disk space, and "
            "check that every slot keeps up (small lag) before and during the backfill",
            publications=[f"{p}: {t}" for p, t in pubs],
            logical_slots=[{"slot": n, "active": a, "lag": lag} for n, a, lag in slots])
    with conn.cursor() as cur:
        cur.execute("SELECT nspname FROM pg_namespace WHERE nspname = %s", (args.backup_schema,))
        if cur.fetchone():
            log(f"backup schema {args.backup_schema} already exists")
    if problems:
        raise Fail("preflight found problems: " + " | ".join(problems))
    log("preflight OK")


# ============================================================================ baseline
def cmd_baseline(args, conn, log):
    if args.entity == "household":
        raise Fail("household has no 'baseline' command: its backup keeps the counts (run 'backup')")
    if schema_state(conn, args.schema) != "old":
        raise Fail("baseline must be taken on the OLD schema, before the database script")
    if os.path.exists(args.out):
        raise Fail(f"{args.out} already exists: keep it, or choose another --out")
    counts = snapshot(conn, args.schema, args.health_keys)
    data = {"taken_utc": utc_now().strftime("%Y-%m-%dT%H:%M:%SZ"), "target": target(conn), "schema": args.schema,
            "health_keys": sorted(args.health_keys), "counts": counts}
    with open(args.out, "w") as f:
        json.dump(data, f, indent=1, default=str)
    log("baseline saved", file=args.out, counts=counts)


# ============================================================================ backup
def cmd_backup(args, conn, log):
    if args.entity == "household":
        return hh_backup(args, conn, log)
    require_confirm(args, conn)
    s, b = args.schema, args.backup_schema
    if schema_state(conn, s) != "old":
        raise Fail("backup must be taken on the OLD schema, before the database script (rollback uses its column types)")
    if q1(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = %s", (b,)):
        raise Fail(f"schema {b} already exists. Keep it if it is the backup of this run; otherwise drop it "
                   f"yourself or choose another --backup-schema")
    baseline_counts = snapshot(conn, s, args.health_keys)
    started = time.time()
    with conn, conn.cursor() as cur:
        cur.execute(f"CREATE SCHEMA {b}")
        cur.execute(f"CREATE TABLE {b}.individual AS TABLE {s}.individual")
        cur.execute(f"CREATE TABLE {b}.individual_identifier AS TABLE {s}.individual_identifier")
        cur.execute(f"CREATE TABLE {b}.individual_address AS TABLE {s}.individual_address")
        cur.execute(f"CREATE TABLE {b}.address AS SELECT a.* {addr_rows(s)}")
        for table in ["individual", "individual_identifier", "address"]:
            cur.execute(f"CREATE INDEX ON {b}.{table} (id)")  # for the rollback join; not a key, so odd data cannot fail the backup
        # the counts at backup time are the baseline that 'verify' compares with (no local file needed)
        cur.execute(f"CREATE TABLE {b}.backup_info AS SELECT now() AS taken_at, %s::text AS source_schema, "
                    f"%s::text AS baseline", (s, json.dumps({"schema": s, "health_keys": sorted(args.health_keys),
                                                           "counts": baseline_counts}, default=str)))
    counts = {}
    with conn.cursor() as cur:
        for table, src in [("individual", f"{s}.individual"), ("individual_identifier", f"{s}.individual_identifier"),
                           ("individual_address", f"{s}.individual_address")]:
            cur.execute(f"SELECT (SELECT count(*) FROM {b}.{table}), (SELECT count(*) FROM {src})")
            counts[table] = cur.fetchone()
        cur.execute(f"SELECT (SELECT count(*) FROM {b}.address), (SELECT count(*) {addr_rows(s)})")
        counts["address"] = cur.fetchone()
    log("backup taken", schema=b, seconds=round(time.time() - started, 1),
        rows={k: v[0] for k, v in counts.items()})
    bad = {k: v for k, v in counts.items() if v[0] != v[1]}
    if bad:
        raise Fail(f"backup row counts differ from the tables (backup, table): {bad}")
    log("backup OK: row counts equal. It holds plain PII: drop it with 'cleanup --drop-backup' after the rollback period")


# ============================================================================ schema
def mv_index_count(conn, schema):
    return q1(conn, "SELECT count(*) FROM pg_indexes WHERE schemaname = %s AND tablename = %s", (schema, MV))


def schema_checks(conn, schema):
    state = schema_state(conn, schema)
    problems = [] if state == "new" else [state]
    for c in IND_TO_TEXT:
        if col_type(conn, schema, "individual", c) != "text":
            problems.append(f"individual.{c} is not text")
    for c in ADDR_TEXT:
        if col_type(conn, schema, "address", c) != "text":
            problems.append(f"address.{c} is not text")
    for idx in HASH_INDEXES:
        if q1(conn, "SELECT to_regclass(%s)", (f"{schema}.{idx}",)) is None:
            problems.append(f"index {idx} missing")
    return problems


def cmd_schema(args, conn, log):
    if args.entity == "household":
        return hh_schema(args, conn, log)
    require_confirm(args, conn)
    state = schema_state(conn, args.schema)
    if state == "new":
        problems = schema_checks(conn, args.schema)
        log("database script already applied; not run again", problems=problems)
        if problems:
            raise Fail("checks failed: " + "; ".join(problems))
        return
    if not args.without_backup and q1(conn, "SELECT to_regclass(%s)", (f"{args.backup_schema}.backup_info",)) is None:
        raise Fail(f"no backup found in schema {args.backup_schema}: run 'backup' first (the rollback needs it), "
                   f"or add --without-backup if you really mean to go on without one")
    ddl = open(args.ddl).read()
    mv_before = q1(conn, "SELECT to_regclass(%s)", (f"{args.schema}.{MV}",)) is not None
    mv_idx_before = mv_index_count(conn, args.schema)
    started = time.time()
    try:
        with conn, conn.cursor() as cur:
            cur.execute(f"SET LOCAL search_path TO {args.schema}")
            cur.execute("SET LOCAL lock_timeout = %s", (f"{args.lock_timeout}s",))
            cur.execute(ddl)
    except psycopg2.errors.LockNotAvailable:
        raise Fail(f"could not get the table locks within {args.lock_timeout}s: another session is using the tables "
                   f"(is the individual service or the persister still running?). Nothing was changed.")
    seconds = round(time.time() - started, 1)
    problems = schema_checks(conn, args.schema)
    mv_after = q1(conn, "SELECT to_regclass(%s)", (f"{args.schema}.{MV}",)) is not None
    if mv_before and (not mv_after or mv_index_count(conn, args.schema) != mv_idx_before):
        problems.append(f"{MV} or its indexes were not rebuilt")
    log("database script applied", file=os.path.basename(args.ddl), seconds=seconds,
        view_rebuilt=mv_before, problems=problems)
    if problems:
        raise Fail("checks after the database script failed: " + "; ".join(problems))
    log("schema OK (safe to run again: it then changes nothing, but rebuilds "
        f"{MV} again if it exists)")


# ============================================================================ backfill
class Batch:
    """Collects the values of one batch so enc-service is called once to decrypt and once to encrypt."""

    def __init__(self):
        self.to_enc, self.enc_set, self.to_dec, self.dec_set = [], [], [], []

    def enc(self, value, setter):
        self.to_enc.append(value)
        self.enc_set.append(setter)

    def dec(self, value, setter):
        self.to_dec.append(value)
        self.dec_set.append(setter)

    def run(self, enc):
        for v, s in zip(enc.decrypt(self.to_dec), self.dec_set):
            s(v)
        for v, s in zip(enc.encrypt(self.to_enc), self.enc_set):
            s(v)


def plain_then(batch, value, on_plain):
    """Calls on_plain(plain value): directly if plain, after decrypting if already cipher."""
    if is_cipher(value):
        batch.dec(value, on_plain)
    else:
        on_plain(value)


class Backfill:
    def __init__(self, args, conn, log, enc, hasher):
        self.args, self.conn, self.log, self.enc, self.hash = args, conn, log, enc, hasher
        self.s = args.schema
        self.stats = {}

    def bump(self, key, n=1):
        self.stats[key] = self.stats.get(key, 0) + n

    def pages(self, sql_after_id, label, total_sql):
        total = q1(self.conn, total_sql)
        last, done, started = "", 0, time.time()
        while True:
            with self.conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
                cur.execute(sql_after_id, {"last": last, "n": self.args.batch})
                rows = cur.fetchall()
            self.conn.commit()  # end the read transaction; each batch writes in its own transaction
            if not rows:
                break
            last = rows[-1]["id"]
            yield rows
            done += len(rows)
            if done // self.args.batch % self.args.progress_every == 0:
                rate = done / max(time.time() - started, 0.001)
                self.log(f"{label}: {done}/{total} rows, {rate:.0f} rows/s, about "
                         f"{(total - done) / max(rate, 0.001) / 60:.1f} min left")
        self.log(f"{label}: {done} rows read in {time.time() - started:.0f} s")

    def guard(self, row):
        """WHERE part (and its values) that makes sure the row is still as it was read."""
        if "lastmodifiedtime" in row:
            return "lastmodifiedtime IS NOT DISTINCT FROM %s", [row["lastmodifiedtime"]]
        return "TRUE", []

    def write(self, table, updates, label):
        changed = [(r, u) for r, u in updates if u]
        self.bump(f"{label}.rows_read", len(updates))
        if self.args.dry_run:
            self.bump(f"{label}.rows_to_change", len(changed))
            return
        for attempt in range(3):
            try:
                done = []
                with self.conn, self.conn.cursor() as cur:
                    for row, upd in changed:
                        sets = ", ".join(f"{k} = %s" for k in upd)
                        vals = [psycopg2.extras.Json(v) if isinstance(v, (dict, list)) else v for v in upd.values()]
                        guard, guard_vals = self.guard(row)
                        params = vals + [row["id"]] + guard_vals
                        cur.execute(f"UPDATE {self.s}.{table} SET {sets} WHERE id = %s AND {guard}", params)
                        done.append(cur.rowcount == 1)
                for ok in done:  # counted only once the batch is committed
                    self.bump(f"{label}.rows_changed" if ok else f"{label}.rows_skipped_changed_meanwhile")
                return
            except (psycopg2.errors.DeadlockDetected, psycopg2.errors.SerializationFailure, psycopg2.errors.LockNotAvailable):
                # another session held the same rows: the whole batch was rolled back (nothing of it written); try again
                self.bump(f"{label}.batch_retried_after_lock_conflict")
                time.sleep(1 + attempt)
        # still conflicting: leave these rows for the next run, which the end message asks for
        self.bump(f"{label}.rows_skipped_changed_meanwhile", len(changed))

    def individuals(self):
        cols = ", ".join(IND_TEXT + list(IND_HASH) + ["additionaldetails", "lastmodifiedtime"])
        for rows in self.pages(f"SELECT id, {cols} FROM {self.s}.individual WHERE id > %(last)s ORDER BY id LIMIT %(n)s",
                               "individual", f"SELECT count(*) FROM {self.s}.individual"):
            batch, updates = Batch(), []
            for row in rows:
                upd = {}
                dob = row["dateofbirth"]
                if dob is not None and not is_cipher(dob):
                    # a date column changed to text holds yyyy-MM-dd; old persister configs writing into the
                    # text column store a timestamp ("1983-04-04 00:00:00+05:30"): the date is its first 10 characters
                    m = DOB_PREFIX.match(java_trim(dob))
                    if m:
                        row["dateofbirth"] = m.group(1)
                    else:
                        self.bump("individual.dob_not_iso_left_as_is")
                for hcol, (src, field) in IND_HASH.items():
                    if row[hcol] is None and not blank(row[src]):
                        plain_then(batch, row[src], lambda p, u=upd, h=hcol, f=field: u.__setitem__(h, self.hash(f, p)))
                for col in IND_TEXT:
                    v = row[col]
                    if not blank(v) and not is_cipher(v):
                        batch.enc(v, lambda c, u=upd, k=col: u.__setitem__(k, c))
                details = row["additionaldetails"]
                if isinstance(details, dict) and isinstance(details.get("fields"), list):
                    touched = False
                    for f in details["fields"]:
                        if isinstance(f, dict) and f.get("key") in self.args.health_keys:
                            val = f.get("value")
                            if isinstance(val, str) and not blank(val) and not is_cipher(val):
                                batch.enc(val, lambda c, fld=f: fld.__setitem__("value", c))
                                touched = True
                            elif isinstance(val, (bool, int, float)):
                                # a JSON number or true/false: the service reads Field.value as text ("50", "true"),
                                # so that text is what gets encrypted and what the service returns
                                batch.enc(json.dumps(val), lambda c, fld=f: fld.__setitem__("value", c))
                                touched = True
                                self.bump("individual.health_value_number_encrypted_as_text")
                            elif val is not None and not isinstance(val, str):
                                self.bump("individual.health_value_not_text_left_as_is")
                    if touched:
                        upd["additionaldetails"] = details  # same dict object, values filled in by batch.run
                updates.append((row, upd))
            batch.run(self.enc)
            # a hash setter given a blank plain value (cipher of "") sets None: drop it, nothing to write
            for _, upd in updates:
                for h in list(upd):
                    if h in IND_HASH and upd[h] is None:
                        del upd[h]
                        self.bump("individual.cipher_of_blank_no_hash")
            self.write("individual", updates, "individual")

    def identifiers(self):
        for rows in self.pages(f"SELECT id, identifierid, identifieridhash, lastmodifiedtime FROM {self.s}.individual_identifier "
                               f"WHERE id > %(last)s ORDER BY id LIMIT %(n)s", "identifier",
                               f"SELECT count(*) FROM {self.s}.individual_identifier"):
            batch, updates = Batch(), []
            for row in rows:
                upd, v = {}, row["identifierid"]
                if not blank(v):
                    if row["identifieridhash"] is None:
                        plain_then(batch, v, lambda p, u=upd: u.__setitem__("identifieridhash", self.hash("identifierId", p)))
                    if not is_cipher(v):
                        batch.enc(v, lambda c, u=upd: u.__setitem__("identifierid", c))
                updates.append((row, upd))
            batch.run(self.enc)
            for _, upd in updates:
                if "identifieridhash" in upd and upd["identifieridhash"] is None:
                    del upd["identifieridhash"]
                    self.bump("identifier.cipher_of_blank_no_hash")
            self.write("individual_identifier", updates, "identifier")

    def addresses(self):
        cols = ", ".join(f"a.{c}" for c in ADDR_TEXT)
        # Only the individual's rows of the shared ADDRESS table. individual_address has
        # no index on addressid, so a page query with EXISTS would sort the whole link table for every page: the ids
        # are collected once into a session temp table with a key, and the pages walk that.
        with self.conn.cursor() as cur:
            cur.execute("DROP TABLE IF EXISTS pii_individual_address_ids")
            cur.execute(f"CREATE TEMP TABLE pii_individual_address_ids AS SELECT DISTINCT addressid AS id "
                        f"FROM {self.s}.individual_address WHERE addressid IS NOT NULL")
            cur.execute("ALTER TABLE pii_individual_address_ids ADD PRIMARY KEY (id)")
        self.conn.commit()
        for rows in self.pages(f"SELECT a.id, {cols} FROM pii_individual_address_ids t JOIN {self.s}.address a "
                               f"ON a.id = t.id WHERE t.id > %(last)s AND a.id > %(last)s ORDER BY t.id LIMIT %(n)s", "address",
                               "SELECT count(*) FROM pii_individual_address_ids"):
            batch, updates = Batch(), []
            for row in rows:
                upd = {}
                for col in ADDR_TEXT:
                    v = row[col]
                    if not blank(v) and not is_cipher(v):
                        batch.enc(v, lambda c, u=upd, k=col: u.__setitem__(k, c))
                updates.append((row, upd))
            batch.run(self.enc)
            self.write("address", updates, "address")


def cmd_backfill(args, conn, log):
    if args.entity == "household":
        return hh_backfill(args, conn, log)
    if not args.dry_run:
        require_confirm(args, conn)
    state = schema_state(conn, args.schema)
    if state != "new":
        raise Fail(f"the database script is not applied yet ({state}): run 'schema' first")
    enc, hasher = Enc(args.enc_host, args.state_tenant), Hasher()
    before = snapshot(conn, args.schema, args.health_keys)
    started = time.time()
    bf = Backfill(args, conn, log, enc, hasher)
    bf.individuals()
    bf.identifiers()
    bf.addresses()
    seconds = round(time.time() - started, 1)
    after = snapshot(conn, args.schema, args.health_keys)
    changed = {k: (before[k], after[k]) for k in before if before[k] != after[k]}
    log("backfill finished", dry_run=args.dry_run, seconds=seconds, enc_calls=enc.calls, stats=bf.stats)
    if changed:
        raise Fail(f"row or non-null counts changed during the backfill (before, after): {changed}")
    if args.dry_run:
        log("dry run: nothing was written")
        return
    started = time.time()
    conn.commit()  # VACUUM cannot run inside a transaction
    conn.autocommit = True
    with conn.cursor() as cur:
        for table in ["individual", "individual_identifier", "address"]:
            cur.execute(f"VACUUM ANALYZE {args.schema}.{table}")
    conn.autocommit = False
    log("VACUUM ANALYZE done", seconds=round(time.time() - started, 1))
    skipped = sum(v for k, v in bf.stats.items() if k.endswith("rows_skipped_changed_meanwhile"))
    if skipped:
        raise RunAgain(f"{skipped} rows changed while they were being encrypted and were skipped: run 'backfill' again "
                   f"(is something still writing to these tables?)")
    log("backfill OK: run 'verify' next")


# ============================================================================ verify
def cmd_verify(args, conn, log):
    if args.entity == "household":
        return hh_verify(args, conn, log)
    if schema_state(conn, args.schema) != "new":
        raise Fail("the database script is not applied: nothing to verify")
    enc, hasher = Enc(args.enc_host, args.state_tenant), Hasher()
    problems = []
    if args.baseline:
        base = json.load(open(args.baseline))
    elif col_type(conn, args.backup_schema, "backup_info", "baseline"):
        base = json.loads(q1(conn, f"SELECT baseline FROM {args.backup_schema}.backup_info"))
        args.baseline = f"{args.backup_schema}.backup_info"
    else:
        raise Fail("no baseline: give --baseline FILE (the backup was taken by an older version of this script)")
    if base.get("schema") != args.schema:
        problems.append(f"baseline is for schema {base.get('schema')}, not {args.schema}")
    now = snapshot(conn, args.schema, set(base.get("health_keys", args.health_keys)))
    diff = {k: (v, now.get(k)) for k, v in base["counts"].items() if now.get(k) != v}
    log("counts compared with the baseline", baseline=args.baseline, different=diff)
    if diff:
        problems.append(f"counts differ from the baseline (baseline, now): {diff}")
    left = leftovers(conn, args.schema, args.health_keys, enc)
    nonzero = {k: v for k, v in left.items() if v and "not_needed" not in k}
    log("values left plain or without hash", left=nonzero,
        cipher_of_blank={k: v for k, v in left.items() if "not_needed" in k})
    if nonzero:
        problems.append(f"plain values or missing hashes left: {nonzero} (run 'backfill' again)")
    problems += compare_with_backup(args, conn, log, enc, hasher)
    if problems:
        raise Fail("verify FAILED: " + " | ".join(problems) + " -> roll back if this cannot be fixed (see 'rollback')")
    log("verify OK")


def health_values(details, keys):
    """Values of the health keys in additionalDetails.fields, in order, as the text the service reads."""
    if not isinstance(details, dict) or not isinstance(details.get("fields"), list):
        return []
    out = []
    for f in details["fields"]:
        if isinstance(f, dict) and f.get("key") in keys:
            v = f.get("value")
            out.append(json.dumps(v) if isinstance(v, (bool, int, float)) else v)
    return out


def compare_with_backup(args, conn, log, enc, hasher):
    """Decrypts the stored values and compares them, and their hashes, with the backup taken before the change.
    Default: a random sample of --sample rows per table. --full: every row (one decrypt call per 500 rows)."""
    b, s = args.backup_schema, args.schema
    if q1(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = %s", (b,)) == 0:
        return [f"backup schema {b} not found: the values cannot be compared"]
    order = "ORDER BY t.id" if args.full else "ORDER BY random() LIMIT %d" % args.sample
    ind_cols = ", ".join(f"t.{c} AS t_{c}, b.{c}::text AS b_{c}" for c in IND_TEXT)
    addr_cols = ", ".join(f"t.{c} AS t_{c}, b.{c} AS b_{c}" for c in ADDR_TEXT)
    tables = [
        ("individual", f"SELECT t.id, {ind_cols}, {', '.join('t.' + h for h in IND_HASH)}, "
                       f"t.additionaldetails AS t_details, b.additionaldetails AS b_details FROM {s}.individual t "
                       f"JOIN {b}.individual b ON b.id = t.id {order}", IND_TEXT),
        ("identifier", f"SELECT t.id, t.identifierid AS t_identifierid, b.identifierid AS b_identifierid, t.identifieridhash "
                       f"FROM {s}.individual_identifier t JOIN {b}.individual_identifier b ON b.id = t.id {order}", ["identifierid"]),
        ("address", f"SELECT t.id, {addr_cols} FROM {s}.address t JOIN {b}.address b ON b.id = t.id {order}", ADDR_TEXT),
    ]
    problems, counts = [], {}
    for label, sql, cols in tables:
        n = 0
        with conn.cursor(name=f"cmp_{label}", cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.itersize = 500
            cur.execute(sql)
            while True:
                page = cur.fetchmany(500)
                if not page:
                    break
                # the backup holds what the OLD service stored: some values were already cipher (old security policy)
                ciphers = {r[k] for r in page for k in r if k.startswith(("t_", "b_")) and is_cipher(r[k])}
                ciphers |= {v for r in page for v in health_values(r.get("t_details"), args.health_keys) if is_cipher(v)}
                ciphers = sorted(ciphers)
                plain = dict(zip(ciphers, enc.decrypt(ciphers))) if ciphers else {}
                for r in page:
                    old_vals = {c: plain.get(r[f"b_{c}"], r[f"b_{c}"]) for c in cols}
                    for c in cols:
                        new, old = plain.get(r[f"t_{c}"], r[f"t_{c}"]), old_vals[c]
                        if c == "dateofbirth" and old is not None:
                            m = DOB_PREFIX.match(java_trim(old))
                            old = m.group(1) if m else old
                        if new != old and not (blank(new) and blank(old)):
                            problems.append(f"{label} {r['id']}: {c} differs from the backup")
                    if label == "individual":
                        old_h = health_values(r["b_details"], args.health_keys)
                        new_h = [plain.get(v, v) for v in health_values(r["t_details"], args.health_keys)]
                        if [None if blank(v) else v for v in old_h] != [None if blank(v) else v for v in new_h]:
                            problems.append(f"individual {r['id']}: additionalDetails health values differ from the backup")
                        for h, (src, field) in IND_HASH.items():
                            if r[h] != hasher(field, old_vals[src]):
                                problems.append(f"individual {r['id']}: {h} is not the hash of the backup value")
                    elif label == "identifier" and r["identifieridhash"] != hasher("identifierId", old_vals["identifierid"]):
                        problems.append(f"identifier {r['id']}: identifieridhash is not the hash of the backup value")
                    n += 1
        conn.commit()
        counts[label] = n
    log("values compared with the backup", full=args.full, rows=counts, problems=len(problems))
    return [f"{len(problems)} differences, first: " + "; ".join(problems[:10])] if problems else []


# ============================================================================ rollback
def saved_view(cur, schema):
    """Definition of the materialized view and its indexes (it blocks column type changes on address); drops it."""
    cur.execute("SELECT to_regclass(%s)", (f"{schema}.{MV}",))
    if cur.fetchone()[0] is None:
        return []
    cur.execute("SELECT pg_get_viewdef(%s::regclass, true)", (f"{schema}.{MV}",))
    ddl = [f"CREATE MATERIALIZED VIEW {schema}.{MV} AS {cur.fetchone()[0]}"]
    cur.execute("SELECT indexdef FROM pg_indexes WHERE schemaname = %s AND tablename = %s ORDER BY indexname", (schema, MV))
    ddl += [r[0] for r in cur.fetchall()]
    cur.execute(f"DROP MATERIALIZED VIEW {schema}.{MV}")
    return ddl


def plain_indexes(cur, table):
    """Indexes of a table that no constraint uses: dropped before a bulk reload and built again after it."""
    cur.execute("SELECT i.indexrelid::regclass::text, pg_get_indexdef(i.indexrelid) FROM pg_index i "
                "WHERE i.indrelid = %s::regclass AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = i.indexrelid)",
                (table,))
    return cur.fetchall()


def type_changes(conn, s, b, table, cols):
    """'ALTER COLUMN c TYPE old USING ...' for every column whose type differs from the backup (taken on the old schema)."""
    out = []
    for c in cols:
        old = col_type(conn, b, table, c)
        if old and old != col_type(conn, s, table, c):
            out.append(f"ALTER COLUMN {c} TYPE {old} USING {c}::{old}")
    return out


def too_long_for_old_types(conn, s, b, cols, skip_backup_rows):
    """Address columns holding a value longer than the limit of the column's old type (in the backup): 'ALTER COLUMN c
    TYPE character varying(n)' cuts such a value without an error. Returns {column: number of rows}, never a value.
    skip_backup_rows: leave out the rows the rollback puts back from the backup (their old values fit)."""
    limits = {}
    for c in cols:
        old = col_type(conn, b, "address", c)
        m = re.match(r"^(?:character varying|character)\((\d+)\)$", old or "")
        if m and old != col_type(conn, s, "address", c):
            limits[c] = int(m.group(1))
    if not limits:
        return {}
    where = f"WHERE NOT EXISTS (SELECT 1 FROM {b}.address b WHERE b.id = t.id)" if skip_backup_rows else ""
    counts = ", ".join(f"count(*) FILTER (WHERE length(t.{c}) > {n})" for c, n in limits.items())
    with conn.cursor() as cur:
        cur.execute(f"SELECT {counts} FROM {s}.address t {where}")
        row = cur.fetchone()
    return {c: n for c, n in zip(limits, row) if n}


def cut_message(cut):
    return (f"address values are longer than the old column limits (rows per column: {cut}): the old column types would "
            f"cut them, and a cut cipher text cannot be decrypted. The column types were not changed")


def cmd_rollback(args, conn, log):
    """Puts the tables back as they were in the backup, in three steps; each step is skipped when it is already done,
    so a rollback that stopped half-way can simply be run again.
      1 individual + individual_identifier (one transaction): emptied, old column types and no hash columns, reloaded
        from the backup (indexes that no constraint uses are dropped first and built once after the reload);
      2 the individual's rows of the shared address table: values put back in batches (other services' rows untouched);
      3 address column types back in one statement, household_address_mv rebuilt around it (one transaction).
    Then every restored row is compared with the backup.
    Refused (nothing changed) when the household migration has changed the shared address table, and when a value in
    the address table is longer than the old column limit (step 3 would cut it)."""
    if args.entity == "household":
        return hh_rollback(args, conn, log)
    require_confirm(args, conn)
    s, b = args.schema, args.backup_schema
    if q1(conn, "SELECT to_regclass(%s)", (f"{b}.backup_info",)) is None:
        raise Fail(f"backup {b} not found: nothing to roll back from")
    step1_done = schema_state(conn, s) == "old"
    addr_alters = type_changes(conn, s, b, "address", ADDR_TEXT)
    if step1_done and not addr_alters and rollback_differences(conn, s, b) == (0, 0, 0):
        log("the tables already equal the backup: nothing to roll back")
        return
    touched = hh_touched_shared_address(conn, s)
    if touched:
        raise Fail(f"the household migration has changed the shared address table ({touched}): step 3 would shrink the "
                   f"address columns again under the household's cipher text. Nothing was changed. Roll back the household "
                   f"first ('rollback --entity household'), then drop its backup ('cleanup --entity household "
                   f"--drop-backup') and run this again; or fix forward instead")
    new_rows = {
        "individual": q1(conn, f"SELECT count(*) FROM {s}.individual t WHERE NOT EXISTS "
                               f"(SELECT 1 FROM {b}.individual b WHERE b.id = t.id)"),
        "identifier": q1(conn, f"SELECT count(*) FROM {s}.individual_identifier t WHERE NOT EXISTS "
                               f"(SELECT 1 FROM {b}.individual_identifier b WHERE b.id = t.id)"),
        "address": q1(conn, f"SELECT count(*) {addr_rows(s)} AND NOT EXISTS (SELECT 1 FROM {b}.address b WHERE b.id = a.id)")}
    # the backfill keeps lastmodifiedtime, so a different value means the new service changed the row after the backup
    changed_rows = {
        "individual": q1(conn, f"SELECT count(*) FROM {s}.individual t JOIN {b}.individual b ON b.id = t.id "
                               f"WHERE t.lastmodifiedtime IS DISTINCT FROM b.lastmodifiedtime"),
        "identifier": q1(conn, f"SELECT count(*) FROM {s}.individual_identifier t JOIN {b}.individual_identifier b "
                               f"ON b.id = t.id WHERE t.lastmodifiedtime IS DISTINCT FROM b.lastmodifiedtime")}
    conn.commit()
    if any(new_rows.values()):
        raise Fail(f"rows were created after the backup {new_rows}; a rollback would leave them encrypted for the "
                   f"old service. After the release, fix forward instead")
    if any(changed_rows.values()):
        if not args.discard_later_updates:
            raise Fail(f"rows were updated after the backup {changed_rows}; a rollback would put back the older values "
                       f"and lose those updates. After the release, fix forward instead. Only if "
                       f"losing them is agreed, run again with --discard-later-updates")
        log("WARNING: rolling back although rows were updated after the backup; those updates are lost",
            **changed_rows)
    cut = too_long_for_old_types(conn, s, b, ADDR_TEXT, skip_backup_rows=True)
    conn.commit()
    if cut:
        raise Fail(cut_message(cut) + ". Nothing was changed. These rows belong to another service: fix forward instead")
    started = time.time()
    try:
        # ---- step 1: individual and identifier tables reloaded from the backup
        if not step1_done:
            t0 = time.time()
            with conn, conn.cursor() as cur:
                cur.execute("SET LOCAL lock_timeout = %s", (f"{args.lock_timeout}s",))
                cur.execute("SET LOCAL maintenance_work_mem = '512MB'")
                cur.execute(f"LOCK TABLE {s}.individual, {s}.individual_identifier IN ACCESS EXCLUSIVE MODE")
                rebuild = []
                for table in ("individual", "individual_identifier"):
                    for name, ddl in plain_indexes(cur, f"{s}.{table}"):
                        if name.split(".")[-1] not in HASH_INDEXES:
                            rebuild.append(ddl)
                        cur.execute(f"DROP INDEX {name}")
                cur.execute(f"TRUNCATE {s}.individual, {s}.individual_identifier")
                drops = [f"DROP COLUMN IF EXISTS {h}" for h in IND_HASH]
                cur.execute(f"ALTER TABLE {s}.individual " + ", ".join(drops + type_changes(conn, s, b, "individual", IND_TEXT)))
                cur.execute(f"ALTER TABLE {s}.individual_identifier DROP COLUMN IF EXISTS identifieridhash")
                for table in ("individual", "individual_identifier"):
                    cur.execute("SELECT string_agg(quote_ident(attname), ', ' ORDER BY attnum) FROM pg_attribute "
                                "WHERE attrelid = %s::regclass AND attnum > 0 AND NOT attisdropped", (f"{b}.{table}",))
                    cols = cur.fetchone()[0]
                    cur.execute(f"INSERT INTO {s}.{table} ({cols}) SELECT {cols} FROM {b}.{table}")
                for ddl in rebuild:
                    cur.execute(ddl)
            log("rollback step 1: individual and identifier tables reloaded from the backup",
                seconds=round(time.time() - t0, 1), indexes_rebuilt=len(rebuild))
            conn.autocommit = True
            with conn.cursor() as cur:
                cur.execute(f"ANALYZE {s}.individual")
                cur.execute(f"ANALYZE {s}.individual_identifier")
            conn.autocommit = False
        # ---- step 2: the individual's address rows, in batches
        t0, last, done = time.time(), "", 0
        sets = ", ".join(f"{c} = b.{c}" for c in ADDR_TEXT)
        differ = " OR ".join(f"t.{c} IS DISTINCT FROM b.{c}" for c in ADDR_TEXT)
        while True:
            with conn, conn.cursor() as cur:
                cur.execute(f"SELECT max(id) FROM (SELECT id FROM {b}.address WHERE id > %s ORDER BY id LIMIT %s) x",
                            (last, args.rollback_batch))
                upto = cur.fetchone()[0]
                if upto is None:
                    break
                cur.execute(f"UPDATE {s}.address t SET {sets} FROM {b}.address b WHERE b.id = t.id AND b.id > %s "
                            f"AND b.id <= %s AND ({differ})", (last, upto))
                done += cur.rowcount
                last = upto
        log("rollback step 2: individual's address rows put back", seconds=round(time.time() - t0, 1), rows_changed=done)
        # ---- step 3: address column types (one rewrite) and the view
        addr_alters = type_changes(conn, s, b, "address", ADDR_TEXT)
        if addr_alters:
            t0 = time.time()
            with conn, conn.cursor() as cur:
                cur.execute("SET LOCAL lock_timeout = %s", (f"{args.lock_timeout}s",))
                view = saved_view(cur, s)
                cur.execute(f"LOCK TABLE {s}.address IN ACCESS EXCLUSIVE MODE")
                cut = too_long_for_old_types(conn, s, b, ADDR_TEXT, skip_backup_rows=False)  # same transaction, lock held
                if cut:  # the with block rolls back: the view is kept, no column is changed
                    raise Fail(cut_message(cut) + ". Steps 1 and 2 are done; fix those rows and run rollback again, or fix "
                               "forward instead")
                cur.execute(f"ALTER TABLE {s}.address " + ", ".join(addr_alters))
                for ddl in view:
                    cur.execute(ddl)
            log("rollback step 3: address column types back", seconds=round(time.time() - t0, 1), view_rebuilt=bool(view))
    except psycopg2.errors.LockNotAvailable:
        conn.rollback()
        raise Fail(f"could not get the table locks within {args.lock_timeout}s: stop the services using the tables. "
                   f"Run rollback again; finished steps are skipped.")
    # ---- check: every restored row equals the backup
    d = rollback_differences(conn, s, b)
    if any(d) or schema_state(conn, s) != "old" or type_changes(conn, s, b, "address", ADDR_TEXT):
        raise Fail(f"rollback check failed (rows different from the backup: individual {d[0]}, identifier {d[1]}, "
                   f"address {d[2]}): run rollback again")
    log("rollback done", seconds=round(time.time() - started, 1))
    log("rollback OK: every restored row equals the backup. Put back the old persister config, then start the old image")


def rollback_differences(conn, s, b):
    """Rows that differ from the backup (individual, identifier, address); counts also rows missing on either side."""
    out = []
    with conn.cursor() as cur:
        cur.execute(f"SELECT (SELECT count(*) FROM {s}.individual t FULL JOIN {b}.individual b ON b.id = t.id WHERE "
                    f"t.id IS NULL OR b.id IS NULL OR "
                    f"({', '.join('t.' + c + '::text' for c in IND_TEXT)}, t.additionaldetails) IS DISTINCT FROM "
                    f"({', '.join('b.' + c + '::text' for c in IND_TEXT)}, b.additionaldetails))")
        out.append(cur.fetchone()[0])
        cur.execute(f"SELECT count(*) FROM {s}.individual_identifier t FULL JOIN {b}.individual_identifier b ON b.id = t.id "
                    f"WHERE t.id IS NULL OR b.id IS NULL OR t.identifierid IS DISTINCT FROM b.identifierid")
        out.append(cur.fetchone()[0])
        cur.execute(f"SELECT count(*) FROM {s}.address t JOIN {b}.address b ON b.id = t.id WHERE "
                    f"({', '.join('t.' + c for c in ADDR_TEXT)}) IS DISTINCT FROM ({', '.join('b.' + c for c in ADDR_TEXT)})")
        out.append(cur.fetchone()[0])
    conn.commit()
    return tuple(out)


# ============================================================================ cleanup
def cmd_cleanup(args, conn, log):
    require_confirm(args, conn)
    if args.entity == "household" and args.drop_old_indexes:
        raise Fail("--drop-old-indexes is not for the household migration (those indexes belong to the individual one)")
    if not (args.drop_old_indexes or args.drop_backup):
        raise Fail("say what to clean: --drop-old-indexes and/or --drop-backup")
    if args.drop_old_indexes and schema_state(conn, args.schema) != "new":
        raise Fail("the database script is not applied: the old indexes are still needed")
    with conn, conn.cursor() as cur:
        if args.drop_old_indexes:
            for idx in OLD_INDEXES:
                cur.execute(f"DROP INDEX IF EXISTS {args.schema}.{idx}")
            log("old indexes dropped", indexes=OLD_INDEXES)
        if args.drop_backup:
            cur.execute(f"DROP SCHEMA IF EXISTS {args.backup_schema} CASCADE")
            log("backup schema dropped (it held plain PII)", schema=args.backup_schema)


# ============================================================================ migrate (guided)
def backup_exists(conn, args):
    return q1(conn, "SELECT to_regclass(%s)", (f"{args.backup_schema}.backup_info",)) is not None


def cmd_migrate(args, conn, log):
    """Every step in order; a step that is already done is skipped, so after a stop the same command continues."""
    if args.entity == "household":
        return hh_migrate(args, conn, log)
    s, b = args.schema, args.backup_schema
    t = target(conn)
    rows = q1(conn, f"SELECT count(*) FROM {s}.individual") if col_type(conn, s, "individual", "id") else None
    log("migrate: target", **t, schema=s, individuals=rows)
    require_confirm(args, conn)
    log("step 1/5 preflight (read-only)")
    cmd_preflight(args, conn, log)
    ns = args.namespace
    print(f"\nCheck the hash secret: this command must print {secret_fingerprint()}\n"
          f"  kubectl -n {ns} get secret health-individual-pii -o jsonpath='{{.data.hash-secret}}' | base64 -d "
          f"| sha256sum | cut -c1-12\n")
    ask_yes(args, f"Is it the same value ({secret_fingerprint()})?")
    state = schema_state(conn, s)
    if state == "old":
        if backup_exists(conn, args):
            diff = rollback_differences(conn, s, b)
            if diff != (0, 0, 0):
                raise Fail(f"backup {b} is from an earlier run and differs from the tables now "
                           f"(individual, identifier, address rows: {diff}). If that run is over, drop it "
                           f"('cleanup --drop-backup') and run 'migrate' again")
            log("step 2/5 backup: already taken (it equals the tables); kept")
        else:
            ask_yes(args, "The individual service must be stopped (scaled to 0), its bulk topics and the persister "
                          "without lag, referral downsync paused and field sync on hold. Is that done?")
            log("step 2/5 backup")
            cmd_backup(args, conn, log)
        log("step 3/5 database script")
        cmd_schema(args, conn, log)
    elif state == "new":
        if not backup_exists(conn, args):
            raise Fail(f"the database script is applied but there is no backup in {b}: this is not a run of this "
                       f"command; continue with the single commands (backfill, verify --baseline FILE)")
        log("steps 2/5 and 3/5 already done (backup taken, database script applied)")
    else:
        raise Fail(f"the tables are {state}")
    log("step 4/5 backfill")
    for attempt in range(1, 4):
        try:
            cmd_backfill(args, conn, log)
            break
        except RunAgain as e:
            if attempt == 3:
                raise
            log(f"backfill again ({e})", attempt=attempt + 1)
    log("step 5/5 verify (every row compared with the backup)")
    args.full = True
    cmd_verify(args, conn, log)
    log("MIGRATION COMPLETE. Next: the re-index job, then the release. Keep the backup "
        f"schema {b} until the rollback period is over; it holds plain data.")


# ============================================================================ household
# The household service keeps its personal data in the shared ADDRESS table: the 8 text fields of the rows that
# household.addressid points to (a household's own row; latitude and longitude stay plain). No database script: the
# individual migration already made the columns TEXT. No hash secret: the household has no hash columns.
HH_COUNT_COLS = ADDR_TEXT + ["latitude", "longitude"]


def hh_addr_rows(schema):
    """The address rows a household points to (an address shared by several households is one row)."""
    return (f"FROM {schema}.address a WHERE EXISTS (SELECT 1 FROM {schema}.household h WHERE h.addressid = a.id)")


def not_individual_row(schema, alias="a"):
    """SQL condition: the address row is not used by an individual (such a row belongs to the individual migration)."""
    return f"NOT EXISTS (SELECT 1 FROM {schema}.individual_address ia WHERE ia.addressid = {alias}.id)"


def hh_cipher_rows(conn, schema):
    """Household address rows with cipher text in any of the 8 fields, written by the household migration (or by the new
    household service). A row that an individual also uses is left out: it is cipher text already from the individual
    migration, so it says nothing about the household one."""
    any_cipher = " OR ".join(f"a.{c} ~ {CIPHER_SQL}" for c in ADDR_TEXT)
    n = q1(conn, f"SELECT count(*) {hh_addr_rows(schema)} AND {not_individual_row(schema)} AND ({any_cipher})")
    conn.commit()
    return n


def hh_touched_shared_address(conn, schema):
    """Why the individual rollback must not run (text for the message), or None: the household migration has changed
    the shared address table when its backup schema exists or household address rows hold cipher text."""
    backup = DEFAULT_BACKUP["household"]
    reasons = []
    if q1(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = %s", (backup,)):
        reasons.append(f"backup schema {backup} exists")
    if q1(conn, "SELECT to_regclass(%s)", (f"{schema}.household",)) is not None:
        n = hh_cipher_rows(conn, schema)
        if n:
            reasons.append(f"{n} household address rows hold cipher text")
    conn.commit()
    return " and ".join(reasons) or None


def hh_refresh_view(args, conn, log):
    """household_address_mv holds a copy of the household address fields (referral reads it and sends it to the devices):
    refresh it so it shows what the table holds now, not the values from before the backfill or the rollback."""
    if not q1(conn, "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = %s "
                    "AND c.relname = %s AND c.relkind = 'm'", (args.schema, MV)):
        conn.commit()
        return
    try:
        with conn, conn.cursor() as cur:
            cur.execute("SET LOCAL lock_timeout = %s", (f"{args.lock_timeout}s",))
            cur.execute(f"REFRESH MATERIALIZED VIEW {args.schema}.{MV}")
    except psycopg2.errors.LockNotAvailable:
        raise Fail(f"could not get the lock on {args.schema}.{MV} within {args.lock_timeout}s (is referral refreshing it?). "
                   f"The address table is done; run the same command again to refresh the view")
    except psycopg2.errors.InsufficientPrivilege:
        raise Fail(f"this database user cannot refresh {args.schema}.{MV} (run as its owner). The address table is done; "
                   f"refresh the view yourself or run the same command as the owner")
    log(f"{MV} refreshed: it shows the values the address table holds now")


def hh_text_problems(conn, schema):
    wrong = [c for c in ADDR_TEXT if col_type(conn, schema, "address", c) != "text"]
    if not wrong:
        return []
    return [f"address columns {', '.join(wrong)} are not text: run the individual migration first (the address table "
            f"is shared and its 'schema' step changes these columns)"]


def hh_snapshot(conn, schema):
    """Counts that must be the same before and after: households, the household address rows, and non-null values
    per address column among those rows."""
    counts = ", ".join(f"count({c})" for c in HH_COUNT_COLS)
    with conn.cursor() as cur:
        cur.execute(f"SELECT count(*) FROM {schema}.household")
        households = cur.fetchone()[0]
        cur.execute(f"SELECT count(*), {counts} {hh_addr_rows(schema)}")
        row = cur.fetchone()
    conn.commit()
    out = {"household.rows": households, "address.household_rows": row[0]}
    out.update({f"address.{c}_present": n for c, n in zip(HH_COUNT_COLS, row[1:])})
    return out


def hh_leftovers(conn, schema):
    """Must all be zero after a complete backfill: values that are neither blank nor cipher text."""
    cols = ", ".join(f"count(*) FILTER (WHERE a.{c} IS NOT NULL AND a.{c} !~ {JAVA_BLANK_SQL} AND a.{c} !~ {CIPHER_SQL})"
                     for c in ADDR_TEXT)
    with conn.cursor() as cur:
        cur.execute(f"SELECT {cols} {hh_addr_rows(schema)}")
        row = cur.fetchone()
    conn.commit()
    return {f"address.{c}_still_plain": n for c, n in zip(ADDR_TEXT, row)}


def hh_preflight(args, conn, log):
    problems = []
    t = target(conn)
    log("target", **t, schema=args.schema, entity="household")
    for table in ["household", "address", "individual_address"]:
        if q1(conn, "SELECT to_regclass(%s)", (f"{args.schema}.{table}",)) is None:
            problems.append(f"table {args.schema}.{table} not found")
    if problems:
        raise Fail("; ".join(problems))
    problems += hh_text_problems(conn, args.schema)
    # rights the later commands need: backup creates a schema, backfill and rollback change the address table
    with conn.cursor() as cur:
        for table in ["household", "address"]:
            cur.execute("SELECT tableowner, pg_has_role(current_user, tableowner, 'USAGE') FROM pg_tables "
                        "WHERE schemaname = %s AND tablename = %s", (args.schema, table))
            owner, is_owner = cur.fetchone()
            if not is_owner:
                problems.append(f"user {t['user']} does not own {args.schema}.{table} (owner {owner}): "
                                f"run as the owner")
        cur.execute("SELECT has_database_privilege(current_user, current_database(), 'CREATE')")
        if not cur.fetchone()[0]:
            problems.append(f"user {t['user']} cannot create a schema in this database: 'backup' needs that right")
        cur.execute("SELECT has_database_privilege(current_user, current_database(), 'TEMP')")
        if not cur.fetchone()[0]:
            problems.append(f"user {t['user']} cannot create temporary tables: 'backfill' needs that right")
    enc = Enc(args.enc_host, args.state_tenant)
    probe = "pii-migrate-preflight"
    back = enc.decrypt(enc.encrypt([probe]))
    if back != [probe]:
        problems.append("enc-service encrypt/decrypt round trip gave a different value")
    else:
        log("enc-service round trip OK", tenant=args.state_tenant)
    # every key id already in the household address rows must decrypt, or 'verify' cannot compare them
    keys = {}
    with conn.cursor() as cur:
        for c in ADDR_TEXT:
            v = f"a.{c}::text"
            cur.execute(f"SELECT DISTINCT ON (split_part({v}, '|', 1)) split_part({v}, '|', 1), {v} "
                        f"{hh_addr_rows(args.schema)} AND {v} ~ {CIPHER_SQL}")
            for key_id, sample in cur.fetchall():
                keys.setdefault(key_id, sample)
    for key_id, sample in sorted(keys.items()):
        try:
            enc.decrypt([sample], retries=1)
            log("existing key id decrypts", key_id=key_id)
        except Fail as e:
            problems.append(f"values encrypted with key id {key_id} cannot be decrypted ({e})")
    with conn.cursor() as cur:
        cur.execute("SELECT usename, coalesce(application_name, ''), count(*) FROM pg_stat_activity "
                    "WHERE datname = current_database() AND pid <> pg_backend_pid() GROUP BY 1, 2 ORDER BY 3 DESC")
        sessions = cur.fetchall()
    log("other sessions on this database (stop the household service before 'backup')",
        sessions=[{"user": u, "application": a, "count": n} for u, a, n in sessions])
    # an address row used by both a household and an individual is one row: the individual migration encrypted it
    # already, and encrypting it again is skipped (it is cipher text then)
    if q1(conn, "SELECT to_regclass(%s)", (f"{args.schema}.individual_address",)) is not None:
        both = q1(conn, f"SELECT count(DISTINCT h.addressid) FROM {args.schema}.household h "
                        f"JOIN {args.schema}.individual_address ia ON ia.addressid = h.addressid")
        log("address ids used by BOTH a household and an individual (info: one row, encrypted once)", count=both)
    with conn.cursor() as cur:
        cur.execute("SELECT pubname, tablename FROM pg_publication_tables WHERE schemaname = %s AND tablename = ANY(%s) "
                    "ORDER BY 1, 2", (args.schema, ["household", "address"]))
        pubs = cur.fetchall()
        cur.execute("SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) "
                    "FROM pg_replication_slots WHERE slot_type = 'logical' AND database = current_database() ORDER BY 1")
        slots = cur.fetchall()
    if pubs or slots:
        log("WARNING: change data capture reads these tables. The backfill sends it one change per address row (values "
            "become cipher text): tell the team that reads it first, check free disk space, and check that every slot "
            "keeps up (small lag) before and during the backfill",
            publications=[f"{p}: {t}" for p, t in pubs],
            logical_slots=[{"slot": n, "active": a, "lag": lag} for n, a, lag in slots])
    with conn.cursor() as cur:
        cur.execute("SELECT pg_get_userbyid(c.relowner), pg_has_role(current_user, c.relowner, 'USAGE') FROM pg_class c "
                    "JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = %s AND c.relname = %s "
                    "AND c.relkind = 'm'", (args.schema, MV))
        mv = cur.fetchone()
    if mv:
        # the backfill and the rollback refresh it (REFRESH MATERIALIZED VIEW needs its owner, or a member of the owner role),
        # so it does not keep the plain values from before
        if mv[1]:
            log(f"{MV} exists: the backfill and the rollback refresh it (it holds a copy of the household address fields)",
                owner=mv[0])
        else:
            problems.append(f"user {t['user']} cannot refresh {args.schema}.{MV} (owner {mv[0]}), which holds a copy of "
                            f"the household address fields: run as {mv[0]} or a member of that role")
    if q1(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = %s", (args.backup_schema,)):
        log(f"backup schema {args.backup_schema} already exists")
    if problems:
        raise Fail("preflight found problems: " + " | ".join(problems))
    log("preflight OK")


def hh_refuse_cipher_without_backup(conn, s, b):
    """Household address rows that already hold cipher text but no backup exists: a backup taken now would hold cipher
    text, not the plain values, so a rollback could not restore them."""
    n = hh_cipher_rows(conn, s)
    if n:
        raise Fail(f"no backup in schema {b}, but {n} household address rows already hold cipher text: a backup taken now "
                   f"would hold cipher text, not the plain values, and a rollback could not restore them. If the backup was "
                   f"dropped, or the backfill ran with --without-backup, there is no rollback any more: fix forward")


def hh_backup(args, conn, log):
    require_confirm(args, conn)
    s, b = args.schema, args.backup_schema
    problems = hh_text_problems(conn, s)
    if problems:
        raise Fail("; ".join(problems))
    if q1(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = %s", (b,)):
        raise Fail(f"schema {b} already exists. Keep it if it is the backup of this run; otherwise drop it "
                   f"yourself or choose another --backup-schema")
    hh_refuse_cipher_without_backup(conn, s, b)
    baseline_counts = hh_snapshot(conn, s)
    started = time.time()
    with conn, conn.cursor() as cur:
        cur.execute(f"CREATE SCHEMA {b}")
        cur.execute(f"CREATE TABLE {b}.household AS SELECT id, clientreferenceid, addressid, lastmodifiedtime, "
                    f"rowversion, isdeleted FROM {s}.household")
        cur.execute(f"CREATE TABLE {b}.address AS SELECT a.* {hh_addr_rows(s)}")
        for table in ["household", "address"]:
            cur.execute(f"CREATE INDEX ON {b}.{table} (id)")  # for the rollback join; not a key, so odd data cannot fail the backup
        # the counts at backup time are the baseline that 'verify' compares with (no local file needed)
        cur.execute(f"CREATE TABLE {b}.backup_info AS SELECT now() AS taken_at, %s::text AS source_schema, "
                    f"%s::text AS baseline", (s, json.dumps({"schema": s, "counts": baseline_counts}, default=str)))
    counts = {}
    with conn.cursor() as cur:
        cur.execute(f"SELECT (SELECT count(*) FROM {b}.household), (SELECT count(*) FROM {s}.household)")
        counts["household"] = cur.fetchone()
        cur.execute(f"SELECT (SELECT count(*) FROM {b}.address), (SELECT count(*) {hh_addr_rows(s)})")
        counts["address"] = cur.fetchone()
    conn.commit()
    log("backup taken", schema=b, seconds=round(time.time() - started, 1), rows={k: v[0] for k, v in counts.items()})
    bad = {k: v for k, v in counts.items() if v[0] != v[1]}
    if bad:
        raise Fail(f"backup row counts differ from the tables (backup, table): {bad}")
    log("backup OK: row counts equal. It holds plain PII: drop it with 'cleanup --drop-backup' after the rollback period")


def hh_schema(args, conn, log):
    problems = hh_text_problems(conn, args.schema)
    log("household migration has no database script; the address column types are checked only", problems=problems)
    if problems:
        raise Fail("checks failed: " + "; ".join(problems))
    log("schema OK (nothing to change: the address columns are text)")


class HouseholdBackfill(Backfill):
    def guard(self, row):
        # the address table has no lastmodifiedtime: the row must still hold the 8 values that were read
        return (" AND ".join(f"{c} IS NOT DISTINCT FROM %s" for c in ADDR_TEXT), [row[c] for c in ADDR_TEXT])

    def household_addresses(self):
        cols = ", ".join(f"a.{c}" for c in ADDR_TEXT)
        # household.addressid has no index of its own, so a page query joining household would scan it for every page:
        # the ids are collected once into a session temp table with a key, and the pages walk that.
        with self.conn.cursor() as cur:
            cur.execute("DROP TABLE IF EXISTS pii_household_address_ids")
            cur.execute(f"CREATE TEMP TABLE pii_household_address_ids AS SELECT DISTINCT addressid AS id "
                        f"FROM {self.s}.household WHERE addressid IS NOT NULL")
            cur.execute("ALTER TABLE pii_household_address_ids ADD PRIMARY KEY (id)")
        self.conn.commit()
        for rows in self.pages(f"SELECT a.id, {cols} FROM pii_household_address_ids t JOIN {self.s}.address a "
                               f"ON a.id = t.id WHERE t.id > %(last)s AND a.id > %(last)s ORDER BY t.id LIMIT %(n)s",
                               "address", "SELECT count(*) FROM pii_household_address_ids"):
            batch, updates = Batch(), []
            for row in rows:
                upd = {}
                for col in ADDR_TEXT:
                    v = row[col]
                    if not blank(v) and not is_cipher(v):
                        batch.enc(v, lambda c, u=upd, k=col: u.__setitem__(k, c))
                updates.append((row, upd))
            batch.run(self.enc)
            self.write("address", updates, "address")


def hh_backfill(args, conn, log):
    if not args.dry_run:
        require_confirm(args, conn)
    problems = hh_text_problems(conn, args.schema)
    if problems:
        raise Fail("; ".join(problems))
    if not args.dry_run and not args.without_backup and not backup_exists(conn, args):
        hh_refuse_cipher_without_backup(conn, args.schema, args.backup_schema)
        raise Fail(f"no backup found in schema {args.backup_schema}: run 'backup' first (the rollback needs it), "
                   f"or add --without-backup if you really mean to go on without one")
    enc = Enc(args.enc_host, args.state_tenant)
    before = hh_snapshot(conn, args.schema)
    started = time.time()
    bf = HouseholdBackfill(args, conn, log, enc, None)
    bf.household_addresses()
    seconds = round(time.time() - started, 1)
    after = hh_snapshot(conn, args.schema)
    changed = {k: (before[k], after[k]) for k in before if before[k] != after[k]}
    log("backfill finished", dry_run=args.dry_run, seconds=seconds, enc_calls=enc.calls, stats=bf.stats)
    if changed:
        raise Fail(f"row or non-null counts changed during the backfill (before, after): {changed}")
    if args.dry_run:
        log("dry run: nothing was written")
        return
    started = time.time()
    conn.commit()  # VACUUM cannot run inside a transaction
    conn.autocommit = True
    with conn.cursor() as cur:
        cur.execute(f"VACUUM ANALYZE {args.schema}.address")
    conn.autocommit = False
    log("VACUUM ANALYZE done", seconds=round(time.time() - started, 1))
    hh_refresh_view(args, conn, log)
    skipped = sum(v for k, v in bf.stats.items() if k.endswith("rows_skipped_changed_meanwhile"))
    if skipped:
        raise RunAgain(f"{skipped} rows changed while they were being encrypted and were skipped: run 'backfill' again "
                       f"(is something still writing to the address table?)")
    log("backfill OK: run 'verify' next")


def hh_verify(args, conn, log):
    problems = hh_text_problems(conn, args.schema)
    if problems:
        raise Fail("; ".join(problems))
    if args.baseline:
        raise Fail("household has no baseline file: its counts are kept in the backup (leave out --baseline)")
    b = args.backup_schema
    if q1(conn, "SELECT to_regclass(%s)", (f"{b}.backup_info",)) is None:
        raise Fail(f"no baseline: backup {b} not found (run 'backup' before the backfill)")
    enc = Enc(args.enc_host, args.state_tenant)
    base = json.loads(q1(conn, f"SELECT baseline FROM {b}.backup_info"))
    if base.get("schema") != args.schema:
        problems.append(f"baseline is for schema {base.get('schema')}, not {args.schema}")
    now = hh_snapshot(conn, args.schema)
    diff = {k: (v, now.get(k)) for k, v in base["counts"].items() if now.get(k) != v}
    log("counts compared with the baseline", baseline=f"{b}.backup_info", different=diff)
    if diff:
        problems.append(f"counts differ from the baseline (baseline, now): {diff}")
    left = hh_leftovers(conn, args.schema)
    nonzero = {k: v for k, v in left.items() if v}
    log("values left plain", left=nonzero)
    if nonzero:
        problems.append(f"plain values left: {nonzero} (run 'backfill' again)")
    problems += hh_compare_with_backup(args, conn, log, enc)
    if problems:
        way = ("roll back if this cannot be fixed (see 'rollback')" if hh_rollback_offered(conn, args.schema, b)
               else HH_FIX_FORWARD)
        raise Fail("verify FAILED: " + " | ".join(problems) + " -> " + way)
    log("verify OK")


def hh_compare_with_backup(args, conn, log, enc):
    """Decrypts the stored household address values and compares them with the backup taken before the change.
    Default: a random sample of --sample rows. --full: every row (one decrypt call per 500 rows)."""
    b, s = args.backup_schema, args.schema
    if q1(conn, "SELECT count(*) FROM pg_namespace WHERE nspname = %s", (b,)) == 0:
        return [f"backup schema {b} not found: the values cannot be compared"]
    order = "ORDER BY t.id" if args.full else "ORDER BY random() LIMIT %d" % args.sample
    cols = ", ".join(f"t.{c} AS t_{c}, b.{c} AS b_{c}" for c in ADDR_TEXT)
    sql = f"SELECT t.id, {cols} FROM {s}.address t JOIN {b}.address b ON b.id = t.id {order}"
    problems, n = [], 0
    with conn.cursor(name="cmp_household_address", cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.itersize = 500
        cur.execute(sql)
        while True:
            page = cur.fetchmany(500)
            if not page:
                break
            # the backup holds what was stored before: a value may already have been cipher text
            ciphers = sorted({r[k] for r in page for k in r if k.startswith(("t_", "b_")) and is_cipher(r[k])})
            plain = dict(zip(ciphers, enc.decrypt(ciphers))) if ciphers else {}
            for r in page:
                for c in ADDR_TEXT:
                    new, old = plain.get(r[f"t_{c}"], r[f"t_{c}"]), plain.get(r[f"b_{c}"], r[f"b_{c}"])
                    if new != old and not (blank(new) and blank(old)):
                        problems.append(f"address {r['id']}: {c} differs from the backup")
                n += 1
    conn.commit()
    log("values compared with the backup", full=args.full, rows={"address": n}, problems=len(problems))
    return [f"{len(problems)} differences, first: " + "; ".join(problems[:10])] if problems else []


def hh_rollback_differences(conn, s, b):
    """Backed-up address rows whose 8 text values differ from the table now. Rows that an individual also uses are left
    out: they belong to the individual migration (neither migrated nor restored here)."""
    n = q1(conn, f"SELECT count(*) FROM {s}.address t JOIN {b}.address b ON b.id = t.id WHERE "
                 f"({', '.join('t.' + c for c in ADDR_TEXT)}) IS DISTINCT FROM ({', '.join('b.' + c for c in ADDR_TEXT)}) "
                 f"AND {not_individual_row(s, 't')}")
    conn.commit()
    return n


def hh_new_address_rows(conn, s, b):
    """Household address rows that are not in the backup (created after it) and hold cipher text in any of the 8 fields: a
    rollback cannot put their plain values back. A new row that is plain needs no restore, so it is not counted."""
    any_cipher = " OR ".join(f"a.{c} ~ {CIPHER_SQL}" for c in ADDR_TEXT)
    n = q1(conn, f"SELECT count(*) {hh_addr_rows(s)} AND NOT EXISTS (SELECT 1 FROM {b}.address b WHERE b.id = a.id) "
                 f"AND ({any_cipher})")
    conn.commit()
    return n


def hh_shared_plain_rows(conn, s, b):
    """(count, first 10 ids) of backed-up address rows that an individual uses now and whose household backup holds a plain
    value (not blank, not cipher text): the row became shared after the backup, so the household migration encrypted it
    and only the backup holds the plain value. A row that was shared at the backup holds cipher text or blank there. Only a
    row that BOTH differs from its backup now AND holds cipher text in any of the 8 fields counts. A shared row that still
    equals its backup was not changed (a row shared at the backup with a plain field and a cipher field is such a row), and
    a shared row that holds no cipher text now (changed to another plain value) was not encrypted: the rollback loses
    nothing by skipping either."""
    plain = " OR ".join(f"(b.{c} IS NOT NULL AND b.{c} !~ {JAVA_BLANK_SQL} AND b.{c} !~ {CIPHER_SQL})" for c in ADDR_TEXT)
    any_cipher = " OR ".join(f"t.{c} ~ {CIPHER_SQL}" for c in ADDR_TEXT)
    with conn.cursor() as cur:
        cur.execute(f"SELECT b.id, count(*) OVER () FROM {b}.address b JOIN {s}.address t ON t.id = b.id WHERE EXISTS "
                    f"(SELECT 1 FROM {s}.individual_address ia WHERE ia.addressid = b.id) AND ({plain}) AND "
                    f"({', '.join('t.' + c for c in ADDR_TEXT)}) IS DISTINCT FROM ({', '.join('b.' + c for c in ADDR_TEXT)}) "
                    f"AND ({any_cipher}) ORDER BY b.id LIMIT 10")
        rows = cur.fetchall()
    conn.commit()
    return (rows[0][1] if rows else 0), [r[0] for r in rows]


def hh_rollback_guard(conn, s, b):
    """(households created after the backup, households updated after it)."""
    new_rows = q1(conn, f"SELECT count(*) FROM {s}.household t WHERE NOT EXISTS "
                        f"(SELECT 1 FROM {b}.household b WHERE b.id = t.id)")
    changed = q1(conn, f"SELECT count(*) FROM {s}.household t JOIN {b}.household b ON b.id = t.id "
                       f"WHERE t.lastmodifiedtime IS DISTINCT FROM b.lastmodifiedtime")
    conn.commit()
    return new_rows, changed


def hh_rollback_refusal(conn, s, b):
    """(why the household rollback stops with no override, or None; address rows that differ from the backup; households
    updated after the backup). The one place for these refusals: the rollback raises them, and the lines that advise a
    rollback ask hh_rollback_offered, so they never offer one that would be refused."""
    differ = hh_rollback_differences(conn, s, b)
    # both address refusals come before "nothing to roll back": the difference count joins only the backed-up rows, so it
    # cannot see them
    new_addr = hh_new_address_rows(conn, s, b)
    if new_addr:  # no override: these rows are not in the backup, so their plain values cannot be restored
        return (f"address rows were created after the backup {{'address': {new_addr}}}: a household points to a row that "
                f"is not in the backup, so a rollback cannot restore it and would leave it encrypted for the old "
                f"service. After the release, fix forward instead"), differ, 0
    shared_plain, shared_ids = hh_shared_plain_rows(conn, s, b)
    if shared_plain:  # no override: only the backup holds the plain value of a row that an individual uses now
        return (f"address rows an individual uses now had plain values at the backup {{'address': {shared_plain}}}, "
                f"first ids: {', '.join(shared_ids)}. They became shared with the individual after the backup, so the "
                f"household migration encrypted them, and a rollback skips rows an individual uses: it would leave "
                f"them encrypted for the old service. Fix forward"), differ, 0
    if differ == 0:
        return None, differ, 0
    new_rows, changed = hh_rollback_guard(conn, s, b)
    if new_rows:
        refusal = (f"rows were created after the backup {{'household': {new_rows}}}; a rollback would leave their "
                   f"addresses encrypted for the old service. After the release, fix forward instead")
        return refusal, differ, changed
    return None, differ, changed


HH_FIX_FORWARD = "fix forward (the household rollback would refuse in this state)"


def hh_rollback_offered(conn, s, b):
    """False when the household rollback would refuse with no override: the lines that advise a rollback must then say
    'fix forward'. True when it would go on, or when there is no backup yet (nothing to ask)."""
    try:
        if q1(conn, "SELECT to_regclass(%s)", (f"{b}.backup_info",)) is None:
            conn.commit()
            return True
        return hh_rollback_refusal(conn, s, b)[0] is None
    except psycopg2.Error:  # the line that asks is written while a step is failing: it must not fail itself
        conn.rollback()
        return True


def hh_rollback(args, conn, log):
    """Puts the 8 text columns of the backed-up household address rows back, in batches (other rows, rows that an
    individual also uses, and the column types are not touched), then compares every restored row with the backup and
    refreshes household_address_mv. It can be run again after a stop."""
    require_confirm(args, conn)
    s, b = args.schema, args.backup_schema
    if q1(conn, "SELECT to_regclass(%s)", (f"{b}.backup_info",)) is None:
        raise Fail(f"backup {b} not found: nothing to roll back from")
    refusal, n_differ, changed = hh_rollback_refusal(conn, s, b)
    if n_differ == 0:
        # when no row differs the view is refreshed first, also when the rollback then refuses: an earlier rollback may have
        # stopped before it refreshed the view, and the view must show what the table holds now
        try:
            hh_refresh_view(args, conn, log)
        except Fail as e:
            if not refusal:
                raise
            raise Fail(f"{refusal}. The view could not be refreshed either: {e}")  # the refusal must not stay hidden
    if refusal:
        raise Fail(refusal)
    if n_differ == 0:
        log("the household address rows already equal the backup: nothing to roll back")
        return
    if changed:
        if not args.discard_later_updates:
            raise Fail(f"rows were updated after the backup {{'household': {changed}}}; a rollback would put back the "
                       f"older values and lose those updates. After the release, fix forward instead. "
                       f"Only if losing them is agreed, run again with --discard-later-updates")
        log("WARNING: rolling back although rows were updated after the backup; those updates are lost",
            household=changed)
    shared = q1(conn, f"SELECT count(*) FROM {b}.address b WHERE EXISTS "
                      f"(SELECT 1 FROM {s}.individual_address ia WHERE ia.addressid = b.id)")
    conn.commit()
    if shared:
        log(f"{shared} backed-up address rows are also used by an individual: they belong to the individual migration, "
            f"so they are not restored", rows=shared)
    started, last, done = time.time(), "", 0
    sets = ", ".join(f"{c} = b.{c}" for c in ADDR_TEXT)
    differ = " OR ".join(f"t.{c} IS DISTINCT FROM b.{c}" for c in ADDR_TEXT)
    while True:
        with conn, conn.cursor() as cur:
            cur.execute(f"SELECT max(id) FROM (SELECT id FROM {b}.address WHERE id > %s ORDER BY id LIMIT %s) x",
                        (last, args.rollback_batch))
            upto = cur.fetchone()[0]
            if upto is None:
                break
            cur.execute(f"UPDATE {s}.address t SET {sets} FROM {b}.address b WHERE b.id = t.id AND b.id > %s "
                        f"AND b.id <= %s AND ({differ}) AND {not_individual_row(s, 't')}", (last, upto))
            done += cur.rowcount
            last = upto
    log("rollback: household address rows put back", seconds=round(time.time() - started, 1), rows_changed=done)
    d = hh_rollback_differences(conn, s, b)
    if d:
        raise Fail(f"rollback check failed ({d} address rows different from the backup): run rollback again")
    hh_refresh_view(args, conn, log)
    log("rollback OK: every restored row equals the backup. The address column types and the individual's rows were "
        "not touched. Start the old household image")


def hh_migrate(args, conn, log):
    """Every step in order; a step that is already done is skipped, so after a stop the same command continues."""
    s, b = args.schema, args.backup_schema
    t = target(conn)
    rows = q1(conn, f"SELECT count(*) FROM {s}.household") if col_type(conn, s, "household", "id") else None
    log("migrate: target", **t, schema=s, entity="household", households=rows)
    require_confirm(args, conn)
    log("step 1/5 preflight (read-only)")
    hh_preflight(args, conn, log)
    if backup_exists(conn, args):
        new_rows, changed = hh_rollback_guard(conn, s, b)
        if hh_cipher_rows(conn, s):
            # rows are already encrypted: this backup is the plain copy of THIS migration, so it is never dropped
            if new_rows:  # the rollback refuses new households (no override), so it is not offered
                log(f"WARNING: households were created ({new_rows}) since the backup, and updated ({changed}), while the "
                    f"migration was running. Stop whatever still writes them (old household pods, persister, consumers). "
                    f"'verify' at the end fails on the household count. The data is safe: every row is encrypted and the "
                    f"backup {b} is kept. Do not drop it: it is the only plain copy. The way on is to fix forward: "
                    f"release the new household image and keep the backup")
            elif changed:
                way = ("fix forward, or roll back only if losing those updates is agreed ('rollback --discard-later-updates')"
                       if hh_rollback_offered(conn, s, b) else HH_FIX_FORWARD)
                log(f"WARNING: households were updated ({changed}) since the backup, while the migration was running. "
                    f"Stop whatever still writes them (old household pods, persister, consumers). 'verify' at the end "
                    f"compares with the backup and may fail; then {way}. "
                    f"Do not drop the backup {b}: it is the only plain copy")
            log("step 2/5 backup: already taken (this migration has encrypted rows already); kept")
        else:
            differ = hh_rollback_differences(conn, s, b)
            if new_rows or changed or differ:
                raise Fail(f"backup {b} is from an earlier run: households were created ({new_rows}) or updated "
                           f"({changed}), or address values differ ({differ}) since. No row is encrypted, so the tables "
                           f"still hold the plain values. If that run is over, drop it ('cleanup --drop-backup') and run "
                           f"'migrate' again")
            log("step 2/5 backup: already taken (it equals the tables); kept")
    else:
        hh_refuse_cipher_without_backup(conn, s, b)
        ask_yes(args, "The household service must be stopped (scaled to 0), its bulk topics and the persister "
                      "without lag, referral downsync paused and field sync on hold. Is that done?")
        log("step 2/5 backup")
        hh_backup(args, conn, log)
    log("step 3/5 database script (none for household: the address column types are checked)")
    hh_schema(args, conn, log)
    log("step 4/5 backfill")
    for attempt in range(1, 4):
        try:
            hh_backfill(args, conn, log)
            break
        except RunAgain as e:
            if attempt == 3:
                raise
            log(f"backfill again ({e})", attempt=attempt + 1)
    log("step 5/5 verify (every row compared with the backup)")
    args.full = True
    hh_verify(args, conn, log)
    log("MIGRATION COMPLETE (household). Keep the backup "
        f"schema {b} until the rollback period is over; it holds plain data.")


# ============================================================================ main
COMMANDS = {"migrate": cmd_migrate, "preflight": cmd_preflight, "baseline": cmd_baseline, "backup": cmd_backup, "schema": cmd_schema,
            "backfill": cmd_backfill, "verify": cmd_verify, "rollback": cmd_rollback, "cleanup": cmd_cleanup}


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("command", choices=list(COMMANDS))
    p.add_argument("--entity", choices=list(DEFAULT_BACKUP), default="individual",
                   help="which service's data to migrate (default individual; household: run it after the individual one)")
    p.add_argument("--schema", required=True, help="schema of the individual and household tables (dev: health)")
    p.add_argument("--host", help="database host (or PGHOST)")
    p.add_argument("--port", help="database port (or PGPORT; default 5432)")
    p.add_argument("--dbname", help="database name (or PGDATABASE)")
    p.add_argument("--namespace", default="health", help="migrate: Kubernetes namespace of health-individual (dev: health)")
    p.add_argument("--yes", action="store_true", help="migrate: answer yes to its questions (automated test runs only)")
    p.add_argument("--backup-schema", help="default: pii_backup_individual, or pii_backup_household with --entity household")
    p.add_argument("--enc-host", help="enc-service base URL, e.g. http://egov-enc-service.egov:8080")
    p.add_argument("--state-tenant", help="state-level tenant id (the service's state.level.tenant.id)")
    p.add_argument("--confirm", help="name of the database; required by every command that writes")
    p.add_argument("--batch", type=int, default=100, help="rows per batch (one enc-service call, one transaction)")
    p.add_argument("--progress-every", type=int, default=200, help="log progress every N batches")
    p.add_argument("--health-keys", default="weight,height,isPregnant", help="additionalFields keys that are encrypted")
    p.add_argument("--dry-run", action="store_true", help="backfill: read and encrypt, write nothing")
    p.add_argument("--baseline", help="verify: the file written by 'baseline'")
    p.add_argument("--out", help="baseline: file to write")
    p.add_argument("--ddl", default=DEFAULT_DDL, help="schema: the database script")
    p.add_argument("--sample", type=int, default=500, help="verify: rows per table compared with the backup")
    p.add_argument("--full", action="store_true", help="verify: compare EVERY row with the backup (slower)")
    p.add_argument("--without-backup", action="store_true", help="schema: go on although no backup was taken")
    p.add_argument("--lock-timeout", type=int, default=30, help="schema/rollback: seconds to wait for table locks")
    p.add_argument("--rollback-batch", type=int, default=20000, help="rollback: address rows per batch")
    p.add_argument("--discard-later-updates", action="store_true",
                   help="rollback: go on although rows were updated after the backup (those updates are lost)")
    p.add_argument("--drop-old-indexes", action="store_true")
    p.add_argument("--drop-backup", action="store_true")
    p.add_argument("--log", default="pii_migrate.log")
    args = p.parse_args()
    if args.backup_schema is None:
        args.backup_schema = DEFAULT_BACKUP[args.entity]
    for name in (args.schema, args.backup_schema):
        if not re.match(r"^[a-z_][a-z0-9_]*$", name):
            sys.exit(f"bad schema name: {name}")
    if args.schema == args.backup_schema:
        sys.exit("--backup-schema must differ from --schema")
    args.health_keys = {k.strip() for k in args.health_keys.split(",") if k.strip()}
    if args.command == "baseline" and not args.out:
        sys.exit("baseline needs --out FILE")
    if args.command in ("migrate", "preflight", "backfill", "verify") and not (args.enc_host and args.state_tenant):
        sys.exit(f"{args.command} needs --enc-host and --state-tenant")
    global INTERACTIVE
    INTERACTIVE = sys.stdin.isatty()
    log = Log(args.log)
    try:
        if args.command in ("migrate", "preflight", "backfill", "verify") and args.entity == "individual":
            ensure_hash_secret()  # the household has no hash columns, so it needs no secret
        conn = connect(args)
    except Fail as e:
        log(f"STOPPED: {e}")
        sys.exit(1)
    except psycopg2.OperationalError as e:
        log(f"STOPPED: could not connect: {str(e).strip().splitlines()[-1]}")
        sys.exit(1)
    except (KeyboardInterrupt, EOFError):
        sys.exit("\nstopped")
    log(f"command {args.command}", schema=args.schema, dry_run=args.dry_run,
        **({"entity": args.entity} if args.entity != "individual" else {}), **target(conn))
    try:
        COMMANDS[args.command](args, conn, log)
    except Fail as e:
        conn.rollback()
        log(f"STOPPED: {e}")
        if args.command == "migrate":
            way = " (or roll back: 'rollback')"
            if args.entity == "household" and not hh_rollback_offered(conn, args.schema, args.backup_schema):
                way = f"; {HH_FIX_FORWARD}"
            log(f"fix the cause and run the same 'migrate' command again: it continues where it stopped{way}")
        sys.exit(1)
    except KeyboardInterrupt:
        conn.rollback()
        log("STOPPED by Ctrl-C: the current batch was not saved; run the same command again to continue")
        sys.exit(130)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
