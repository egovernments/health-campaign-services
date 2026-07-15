#!/usr/bin/env bash
# Per-API benchmark table from a JMeter .jtl (CSV format).
# Columns: samples, error%, avg, p50, p90, p95, p99, min, max (ms), throughput (req/s)
# Usage: ./summarize.sh results/run-XXXX/results.jtl
set -euo pipefail
JTL="${1:?usage: summarize.sh <results.jtl>}"

python3 - "$JTL" <<'PY'
import csv, sys, collections
path = sys.argv[1]
rows = collections.defaultdict(list)
ok   = collections.defaultdict(int)
tot  = collections.defaultdict(int)
tmin, tmax = {}, {}
with open(path) as f:
    r = csv.DictReader(f)
    for row in r:
        lbl = row.get('label')
        if not lbl: continue
        try: el = int(row['elapsed'])
        except (KeyError, ValueError): continue
        rows[lbl].append(el)
        tot[lbl] += 1
        if row.get('success','').lower() == 'true': ok[lbl] += 1
        ts = int(row['timeStamp'])
        tmin[lbl] = min(tmin.get(lbl, ts), ts)
        tmax[lbl] = max(tmax.get(lbl, ts), ts)

def pct(v, p):
    if not v: return 0
    v = sorted(v); k = (len(v)-1)*p/100.0; f=int(k)
    return v[f] if f+1>=len(v) else v[f]+(v[f+1]-v[f])*(k-f)

hdr = f"{'API':<42}{'n':>7}{'err%':>7}{'avg':>7}{'p50':>7}{'p90':>7}{'p95':>7}{'p99':>7}{'max':>8}{'req/s':>9}"
print(hdr); print('-'*len(hdr))
for lbl in sorted(rows):
    v = rows[lbl]; n = tot[lbl]
    errpct = 100.0*(n-ok[lbl])/n if n else 0
    dur = (tmax[lbl]-tmin[lbl])/1000.0 or 1
    tput = n/dur
    print(f"{lbl[:42]:<42}{n:>7}{errpct:>7.1f}{sum(v)/len(v):>7.0f}"
          f"{pct(v,50):>7.0f}{pct(v,90):>7.0f}{pct(v,95):>7.0f}{pct(v,99):>7.0f}"
          f"{max(v):>8.0f}{tput:>9.1f}")
PY
