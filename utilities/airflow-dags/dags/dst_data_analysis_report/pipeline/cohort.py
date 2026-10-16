"""cohort.py — OPTIONAL cohort report stage (children across cycles).

Reads the pre-computed cross-cycle index ({tenant}-crosscycle-index-v2) — NOT
the project-task index. That index is built by an ES transform: one doc per
project beneficiary with cycle01..cycleNN = quantity given in that cycle
(0 = not treated) plus boundaryHierarchy and campaignNumber. One filters
aggregation per area (and per health facility) gives every number, so the
stage takes seconds; only the optional children-to-reach list is per child
(beneficiary IDs, never names).

Output:
  - "Cohort Data" Excel: one sheet by LGA, one by health facility, lowest
    retention first, rows coloured by status, derived columns as live
    formulas; uploaded to the campaign Drive folder (becomes a Google Sheet).
  - the "Children Across Cycles" report section, two sub-sections, each a
    table first, then its chart:
      N.1 Children Treated per Cycle   (+ chart, + "What this shows")
      N.2 Children Treated per Cycle, by LGA   (+ chart, + Cohort Data link)
  - while a cycle after Cycle 1 is running, the "Children To
    Reach" sheet (beneficiary IDs, one tab per LGA) — see "children yet to be
    reached" below; not linked from the report.
Verified 2026-10-08: the LGA numbers rebuild the Nasarawa reference cohort
sheet exactly (13/13 LGAs, every column).

SMC (drug_type SPAQ) and AZM campaigns only, every other drug type skipped; cumulative report only (a
daily report gets no cohort).
Same insert-only contract as pipeline/stock.py: common/campaign_runner.py
calls cohort.run(cfg) non-fatally before the report stage; with the flag off, run() returns
None immediately and nothing changes anywhere.

Feature switch (stock.py's DUP_MATRIX pattern):
  1. COHORT_REPORT_DEFAULT below           (per checkout)
  2. the DST_COHORT_REPORT environment key (per deployment, overrides 1)
Index override: DST_COHORT_INDEX (default: task index prefix +
"crosscycle-index-v2").

Definitions (child = project beneficiary):
  treated in cycle k            cycleK > 0
  first treatment in cycle k    treated in k, not treated in any earlier cycle
  treated in an earlier cycle   treated in k and in at least one earlier cycle
  treated in every cycle 1-k    treated in each of cycles 1..k (the cohort)
  missed at least one cycle     treated in Cycle 1, missed at least one of 2..k
Status compares an area's full-course % with the state's: LOW more than
3 points below, HIGH more than 3 points above, MODERATE in between.
"""
import logging
import os
import re
from datetime import datetime

import requests
import urllib3

from dst_data_analysis_report.pipeline.core.es import post as es_post
from dst_data_analysis_report.pipeline.core.es import scroll_batches

urllib3.disable_warnings()
log = logging.getLogger(__name__)

# ── feature switches (the .env key, when set, overrides the default here) ─────
COHORT_REPORT_DEFAULT = "TRUE"          # DST_COHORT_REPORT: the whole stage
                                        # (section, Cohort Data, Children To Reach)

_ON = ("TRUE", "YES", "1", "Y", "ON")


def _flag_on(cfg):
    val = (os.getenv("DST_COHORT_REPORT", "").strip() or COHORT_REPORT_DEFAULT)
    return val.strip().upper() in _ON or bool(cfg.get("cohort_report"))


_TIMEOUT = 300
_COHORT_DRUG_TYPES = {"SPAQ", "SMC", "AZM"}   # SMC (sheet drug_type SPAQ) and AZM
_AREA_KEYS = ("lga", "district", "county", "province")
_AREA_LABELS = {"lga": "LGA", "district": "District", "county": "County",
                "province": "Province"}
_HF_KEY = "healthFacility"
_BAND_POINTS = 3.0                  # status band: +/- points around the state


# ── ES access ─────────────────────────────────────────────────────────────────

def _req(cfg, method, url_path, body=None):
    """GET for the mapping; searches go through core.es.post, so the ES load
    guard (DST_ES_MAX_CONCURRENT / DST_ES_THROTTLE_MS) applies."""
    url = f"{cfg['es_url']}/{url_path}"
    if method == "GET":
        r = requests.get(url, auth=cfg["es_auth"], verify=False, timeout=_TIMEOUT)
    else:
        r = es_post(url, json=body, auth=cfg["es_auth"], verify=False,
                    timeout=_TIMEOUT)
    r.raise_for_status()
    return r.json()


def _index(cfg):
    env = os.getenv("DST_COHORT_INDEX", "").strip()
    if env:
        return env
    return cfg["ES_INDEX_TASK"].replace("project-task-index-v1",
                                        "crosscycle-index-v2")


def _layout(cfg, index):
    """Cycle fields (cycle01..), the area key and whether the health facility
    level exists, read from the mapping."""
    props = next(iter(_req(cfg, "GET", f"{index}/_mapping").values()))
    props = props["mappings"]["properties"]
    cycles = sorted(k for k in props if re.fullmatch(r"cycle\d+", k))
    bh = (props.get("Data", {}).get("properties", {})
          .get("boundaryHierarchy", {}).get("properties", {}))
    area_key = next((k for k in _AREA_KEYS if k in bh), None)
    return cycles, area_key, _HF_KEY in bh


def _scope(cfg):
    if cfg.get("campaign_number"):
        return [{"term": {"Data.campaignNumber.keyword": cfg["campaign_number"]}}]
    log.warning("[cohort] no campaign_number — the index may hold more than "
                "one campaign; counting every doc in it")
    return []


def _treated(c):
    return {"range": {c: {"gt": 0}}}


def _not_treated(c):
    return {"bool": {"must_not": [_treated(c)]}}


def _filters(cycles):
    f = {"any": {"bool": {"should": [_treated(c) for c in cycles],
                          "minimum_should_match": 1}}}
    for k, c in enumerate(cycles):
        f[f"treated{k + 1}"] = _treated(c)
        f[f"new{k + 1}"] = {"bool": {"filter": [_treated(c)]
                                     + [_not_treated(e) for e in cycles[:k]]}}
        f[f"every{k + 1}"] = {"bool": {"filter": [_treated(e)
                                                  for e in cycles[:k + 1]]}}
    return f


def _counts(bucket):
    return {k: v["doc_count"] for k, v in bucket["f"]["buckets"].items()}


def _fetch(cfg, index, cycles, area_key, has_hf):
    """({area: counts}, {(area, facility): counts}) — one request.
    counts = {treatedK/newK/everyK/any: doc count}."""
    filt = {"filters": {"filters": _filters(cycles)}}
    sub = {"f": filt}
    if has_hf:
        sub["hf"] = {"terms": {"field": f"Data.boundaryHierarchy.{_HF_KEY}.keyword",
                               "size": 5000, "missing": "Unknown"},
                     "aggs": {"f": filt}}
    body = {"size": 0, "query": {"bool": {"filter": _scope(cfg)}},
            "aggs": {"a": {"terms": {
                "field": f"Data.boundaryHierarchy.{area_key}.keyword",
                "size": 2000, "missing": "Unknown"}, "aggs": sub}}}
    data = _req(cfg, "POST", f"{index}/_search", body)
    by_area, by_hf = {}, {}
    for b in data["aggregations"]["a"]["buckets"]:
        by_area[b["key"]] = _counts(b)
        for h in (b.get("hf") or {}).get("buckets", []):
            by_hf[(b["key"], h["key"])] = _counts(h)
    return by_area, by_hf


# ── numbers ───────────────────────────────────────────────────────────────────

def _pct(a, b, dp=1):
    return f"{a / b * 100:.{dp}f}%" if b else "-"


def _ratio(a, b):
    return a / b * 100 if b else 0.0


def _derive(g, n):
    """Per-cycle rows for one area from the raw filter counts."""
    base = g["treated1"]
    rows = []
    for k in range(1, n + 1):
        rows.append({"treated": g[f"treated{k}"], "new": g[f"new{k}"],
                     "returning": g[f"treated{k}"] - g[f"new{k}"],
                     "every": g[f"every{k}"],
                     "missed": base - g[f"every{k}"]})
    return rows


def _active_cycles(raw, cycles):
    """Drop trailing cycle fields nobody has been treated in yet."""
    n = len(cycles)
    while n and not sum(g[f"treated{n}"] for g in raw.values()):
        n -= 1
    return n


def _band(p, state):
    if p < state - _BAND_POINTS:
        return "LOW"
    if p > state + _BAND_POINTS:
        return "HIGH"
    return "MODERATE"


def _full(entry, done):
    """(Cycle 1 children, treated in every completed cycle, full-course %)."""
    r = entry["rows"]
    c1, ev = r[0]["treated"], r[max(done, 1) - 1]["every"]
    return c1, ev, _ratio(ev, c1)


# ── Excel: Cohort Data (by LGA, by health facility) ───────────────────────────

def _write_xlsx(cfg, data, reach=None):
    """Two workbooks (user decision 2026-10-09):
      cohort_data.xlsx        BY <AREA>, BY HEALTH FACILITY (counts only, no
                              names) plus two "yet to be reached" count columns
                              when the children-to-reach rows exist
      children_to_reach.xlsx  SUMMARY, then one tab per area, one row per child
                              (beneficiary IDs) — only when the rows exist
    Returns (cohort_path, reach_path or None). Streaming (write-only): the
    list can run to several hundred thousand rows."""
    from openpyxl import Workbook
    from openpyxl.cell import WriteOnlyCell
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
    from openpyxl.utils import get_column_letter

    banner = PatternFill("solid", fgColor="17365D")      # same as the stock Excel
    head_fill = PatternFill("solid", fgColor="1A6496")
    total_fill = PatternFill("solid", fgColor="EEEEEE")
    amber = PatternFill("solid", fgColor="FFEB9C")
    reach_fill = PatternFill("solid", fgColor="FCE4D6")  # the cycle in progress
    status_style = {"LOW": (PatternFill("solid", fgColor="FFC7CE"), "9C0006"),
                    "MODERATE": (amber, "9C5700"),
                    "HIGH": (None, "006100")}
    thin = Side(style="thin", color="BFBFBF")
    box = Border(left=thin, right=thin, top=thin, bottom=thin)
    center = Alignment(horizontal="center")
    n, cur, area = data["n_cycles"], data["in_progress"], data["area"]
    done = max(data["done"], 1)          # a lone in-progress cycle: show Cycle 1
    state = data["state_pct"]
    wb = Workbook(write_only=True)

    def cell(ws, v, fill=None, font=None, align=None, fmt=None, border=True):
        c = WriteOnlyCell(ws, value=v)
        if border:
            c.border = box
        if fill:
            c.fill = fill
        if font:
            c.font = font
        if align:
            c.alignment = align
        if fmt:
            c.number_format = fmt
        return c

    def titles(ws, title, note, head, widths):
        for ci, w in enumerate(widths, 1):
            ws.column_dimensions[get_column_letter(ci)].width = w
        ws.freeze_panes = "A4"
        ws.append([cell(ws, title, banner, Font(bold=True, color="FFFFFF", size=12),
                        border=False)]
                  + [cell(ws, "", banner, border=False) for _ in head[1:]])
        ws.append([cell(ws, note, font=Font(italic=True, size=9, color="595959"),
                        border=False)])
        ws.append([cell(ws, h, head_fill, Font(bold=True, color="FFFFFF"),
                        Alignment(wrap_text=True, horizontal="center", vertical="center"))
                   for h in head])

    def counts_sheet(ws, title, keys, items, reach_counts):
        nk = len(keys)
        extra = ([f"Cycle {cur} - yet to be reached (treated in an earlier cycle)",
                  "of whom received every earlier cycle"] if reach_counts is not None else [])
        head = (keys + [f"Cycle {k} - children treated"
                        + (" (to date)" if k == cur else "") for k in range(1, n + 1)]
                + [f"Treated in every cycle (1-{done})", "Missed at least one cycle",
                   "% treated in every cycle", "Status"] + extra)
        col = lambda off: get_column_letter(nk + off)      # 1..n = cycles
        c_c1, c_ev, c_pc = col(1), col(n + 1), col(n + 3)
        widths = [18] * nk + [13] * n + [16, 14, 14, 12] + [18, 16] * bool(extra)
        if nk == 2:
            widths[1] = 32
        titles(ws, f"{cfg['state_name']} - {title}",
               f"Lowest first. Red = LOW (more than {_BAND_POINTS:.0f} points below the "
               f"state), Amber = MODERATE, no fill = HIGH (more than {_BAND_POINTS:.0f} "
               f"points above).   Formula:  Missed = Cycle 1 - Every cycle  |  % = Every "
               f"cycle / Cycle 1  |  Every cycle = treated in Cycle 1 AND each of Cycles "
               f"2-{done}." + (f"   Yet to be reached = treated in an earlier cycle, not yet "
                               f"in Cycle {cur}." if extra else ""), head, widths)
        last = 4 + len(items)
        rows = [(["Total"] + [""] * (nk - 1), data["total"]["rows"], None, None)]
        rows += [(list(k), e["rows"], _band(_full(e, done)[2], state),
                  (reach_counts or {}).get(k if nk > 1 else k[0], [0, 0]))
                 for k, e in items]
        for ri, (key, r, st, rc) in enumerate(rows, 4):
            if ri == 4:
                vals = key + [f"=SUM({col(o)}5:{col(o)}{last})" for o in range(1, n + 2)]
            else:
                vals = key + [r[k]["treated"] for k in range(n)] + [r[done - 1]["every"]]
            vals += [f"={c_c1}{ri}-{c_ev}{ri}",
                     f"=IF({c_c1}{ri}=0,0,{c_ev}{ri}/{c_c1}{ri})",
                     "-" if ri == 4 else
                     (f'=IF({c_pc}{ri}<{c_pc}$4-{_BAND_POINTS / 100},"LOW",'
                      f'IF({c_pc}{ri}>{c_pc}$4+{_BAND_POINTS / 100},"HIGH","MODERATE"))')]
            if extra:
                vals += ([f"=SUM({col(o)}5:{col(o)}{last})" for o in (n + 5, n + 6)]
                         if ri == 4 else [rc[1], rc[0]])
            fill, ink = status_style.get(st, (None, None))
            out = []
            for ci, v in enumerate(vals, 1):
                is_num = ci > nk
                out.append(cell(
                    ws, v,
                    fill=total_fill if ri == 4 else fill,
                    font=(Font(bold=True) if ri == 4 else
                          Font(bold=True, color=ink) if ci in (nk + n + 3, nk + n + 4)
                          else None),
                    align=center if is_num else None,
                    fmt=("0.0%" if ci == nk + n + 3 else "#,##0") if is_num else None))
            ws.append(out)
        ws.auto_filter.ref = f"A3:{get_column_letter(len(head))}{last}"

    def reach_summary(ws, groups):
        head = [area, f"Cycle {cur} - yet to be reached",
                "Priority 1 - received every earlier cycle",
                "Priority 2 - received some earlier cycles", "No beneficiary ID"]
        titles(ws, f"{cfg['state_name']} - Children yet to be reached in Cycle {cur}  "
                   f"(as of {reach['as_of']})",
               f"INTERNAL - beneficiary IDs only, no names; share each {area} tab only "
               f"with the supervisors and CDDs of that {area}. One tab per {area}, one "
               f"row per child treated in an earlier cycle and not yet in Cycle {cur}. "
               f"Priority 1 first: reaching them keeps them on the full course.",
               head, [22, 16, 20, 20, 14])
        last = 4 + len(groups)
        tot = [cell(ws, "Total", total_fill, Font(bold=True))]
        tot += [cell(ws, f"=SUM({get_column_letter(c)}5:{get_column_letter(c)}{last})",
                     total_fill, Font(bold=True), center, "#,##0") for c in range(2, 6)]
        ws.append(tot)
        for name, rs in groups:
            p1 = sum(r["priority"] == 1 for r in rs)
            ws.append([cell(ws, name)]
                      + [cell(ws, v, align=center, fmt="#,##0") for v in
                         (len(rs), p1, len(rs) - p1, sum(not r["bid"] for r in rs))])
        ws.auto_filter.ref = f"A3:E{last}"

    def reach_sheet(ws, name, rs):
        earlier = list(range(1, cur))
        head = (["#", "Ward", "Health Facility", "Beneficiary ID"]
                + [f"Cycle {k}" for k in earlier]
                + [f"Cycle {cur} (in progress)", "Priority"])
        p1n = sum(r["priority"] == 1 for r in rs)
        titles(ws, f"{name} {area} - {len(rs):,} children yet to be reached in Cycle "
                   f"{cur}  (as of {reach['as_of']})",
               f"INTERNAL - beneficiary IDs only (look the child up by ID in the app). "
               f"Priority 1 (highlighted, {p1n:,}) = received every earlier cycle: "
               f"reaching them in this cycle keeps them on the full course. Filter by "
               f"ward or health facility to plan CDD visits.",
               head, [7, 20, 30, 15] + [9] * len(earlier) + [16, 30])
        for i, r in enumerate(rs, 1):
            p1 = r["priority"] == 1
            f = amber if p1 else None
            vals = ([i, r["ward"], r["hf"], r["bid"] or "-"]
                    + ["Yes" if t else "No" for t in r["cyc"]])
            out = [cell(ws, v, f, align=center if ci == 0 or ci >= 3 else None)
                   for ci, v in enumerate(vals)]
            out.append(cell(ws, "Yet to reach", reach_fill,
                            Font(bold=True, color="9C5700"), center))
            out.append(cell(ws, "1 - received every earlier cycle" if p1
                            else "2 - received some earlier cycles", f))
            ws.append(out)
        ws.auto_filter.ref = f"A3:{get_column_letter(len(head))}{3 + len(rs)}"

    lowest = lambda kv: (_full(kv[1], done)[2], kv[0])
    rc_area = rc_hf = None
    if reach:
        rc_area, rc_hf = {}, {}
        for r in reach["rows"]:
            for d, k in ((rc_area, r["area"]), (rc_hf, (r["area"], r["hf"]))):
                d.setdefault(k, [0, 0])
                d[k][0] += r["priority"] == 1
                d[k][1] += 1
    counts_sheet(wb.create_sheet(f"BY {area.upper()}"),
                 f"Children Across Cycles by {area}", [area],
                 [((k,), v) for k, v in sorted(data["by_area"].items(), key=lowest)],
                 rc_area)
    if data["by_hf"]:
        counts_sheet(wb.create_sheet("BY HEALTH FACILITY"),
                     "Children Across Cycles by Health Facility", [area, "Health Facility"],
                     sorted(data["by_hf"].items(), key=lowest), rc_hf)
    path = os.path.join(cfg["out_dir"], "cohort_data.xlsx")
    wb.save(path)
    if not (reach and reach["rows"]):
        return path, None
    # second workbook: Children To Reach — SUMMARY, then one tab per area (rows
    # arrive sorted by area, ward, facility, beneficiary ID)
    groups = {}
    for r in reach["rows"]:
        groups.setdefault(r["area"], []).append(r)
    groups = sorted(groups.items())
    wb = Workbook(write_only=True)
    reach_summary(wb.create_sheet("SUMMARY"), groups)
    used = {"summary"}
    for name, rs in groups:
        tab = re.sub(r"[\[\]:*?/\\']", " ", str(name)).strip()[:31] or "Unknown"
        while tab.lower() in used:                 # tab names are case-insensitive
            tab = f"{tab[:28]} {len(used)}"
        used.add(tab.lower())
        reach_sheet(wb.create_sheet(tab), name, rs)
    reach_path = os.path.join(cfg["out_dir"], "children_to_reach.xlsx")
    wb.save(reach_path)
    return path, reach_path


def _publish(cfg, path, label):
    """Upload one Excel to the campaign Drive folder (becomes a Google Sheet);
    returns the link or "". Non-fatal."""
    if cfg.get("no_upload") or not path:
        return ""
    try:
        from dst_data_analysis_report.pipeline import notify
        fid = notify.campaign_folder_id(cfg)
        period = (f"Cumulative Days 1-{cfg['DAY']}" if cfg.get("cumulative")
                  else f"Day {cfg['DAY']}")
        stamp = datetime.now().strftime("%H:%M")
        return notify.upload_file(
            path, f"{cfg['state_name']} {period} {label} — "
                  f"{cfg['DATE_LABEL']} {stamp}", folder_id=fid) or ""
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"[cohort] {label} Drive upload failed (non-fatal): {e}")
        return ""


# ── charts ────────────────────────────────────────────────────────────────────

_INK, _INK2, _GRID, _SURF = "#0b0b0b", "#52514e", "#e6e5e0", "#ffffff"
_BLUE, _ORANGE = "#2a78d6", "#eb6834"


def _hex(rgb):
    return "#%02x%02x%02x" % (rgb[0], rgb[1], rgb[2])


def _charts(cfg, data):
    """Two PNGs in out_dir: per-cycle bars and the area full-course bars.
    Returns {"cycles": path|None, "areas": path|None}. Non-fatal."""
    out = {"cycles": None, "areas": None}
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.lines import Line2D
        from matplotlib.patches import Patch
        from matplotlib.ticker import FuncFormatter
        from dst_data_analysis_report.pipeline.core.word import STATUS_COLOR
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"[cohort] charts skipped (matplotlib): {e}")
        return out
    plt.rcParams.update({"font.size": 9, "axes.edgecolor": _GRID,
                         "xtick.color": _INK2, "ytick.color": _INK2})
    n, done, cur = data["n_cycles"], data["done"], data["in_progress"]
    tr = data["total"]["rows"]
    c1 = tr[0]["treated"]

    def clean(ax):
        for s in ("top", "right", "left"):
            ax.spines[s].set_visible(False)
        ax.tick_params(length=0)

    try:
        fig, ax = plt.subplots(figsize=(7.2, 3.4), facecolor=_SURF)
        top = max(r["treated"] for r in tr)
        for i, r in enumerate(tr):
            ax.bar(i, r["returning"], 0.5, color=_BLUE, edgecolor=_SURF,
                   linewidth=2, zorder=2)
            ax.bar(i, r["new"], 0.5, bottom=r["returning"], color=_ORANGE,
                   edgecolor=_SURF, linewidth=2, zorder=2)
            ax.text(i + 0.27, r["treated"] + top * 0.01, f"{r['treated']:,}",
                    ha="left", va="bottom", color=_INK, fontsize=9, fontweight="bold")
        xs = list(range(done))
        ys = [tr[k]["every"] for k in xs]
        if xs:
            ax.plot(xs, ys, color=_INK, lw=2, marker="o", ms=8, mec=_SURF, mew=2,
                    zorder=3)
            ax.text(xs[-1] + 0.28, ys[-1] - top * 0.05,
                    f"{ys[-1]:,} ({_ratio(ys[-1], c1):.0f}% of Cycle 1)",
                    color=_INK, fontsize=8, va="top")
        ax.set_xticks(range(n))
        ax.set_xticklabels([f"Cycle {k}" + ("\n(to date)" if k == cur else "")
                            for k in range(1, n + 1)])
        ax.set_ylim(0, top * 1.15)
        ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v / 1000:,.0f}k"))
        ax.grid(axis="y", color=_GRID, lw=0.8, zorder=0)
        clean(ax)
        ax.legend(handles=[Patch(color=_BLUE, label="Treated in an earlier cycle"),
                           Patch(color=_ORANGE, label="First treatment this season"),
                           Line2D([], [], color=_INK, marker="o", lw=2,
                                  label="Treated in every cycle so far")],
                  loc="upper center", bbox_to_anchor=(0.5, -0.13), ncol=3,
                  frameon=False, fontsize=8)
        out["cycles"] = os.path.join(cfg["out_dir"], "cohort_cycles.png")
        fig.savefig(out["cycles"], dpi=150, bbox_inches="tight", facecolor=_SURF)
        plt.close(fig)
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"[cohort] per-cycle chart failed (non-fatal): {e}")

    if done < 2:
        return out
    try:
        state = data["state_pct"]
        rows = sorted(((name, _full(e, done)[2]) for name, e in data["by_area"].items()),
                      key=lambda kv: kv[1], reverse=True)
        fig, ax = plt.subplots(figsize=(7.2, max(3.0, 0.3 * len(rows) + 1.2)),
                               facecolor=_SURF)
        ax.barh([r[0] for r in rows], [r[1] for r in rows], height=0.6,
                color=[_hex(STATUS_COLOR[_band(p, state)]) for _, p in rows],
                edgecolor=_SURF, lw=2, zorder=2)
        ax.axvline(state, color=_INK2, ls="--", lw=1, zorder=3)
        ax.text(state, len(rows) - 0.3, f" State {state:.1f}%", color=_INK2,
                fontsize=8, va="bottom")
        for i, (_, p) in enumerate(rows):
            ax.text(max(p, state) + 0.8, i, f"{p:.1f}%  {_band(p, state)}",
                    va="center", color=_INK, fontsize=8)
        hi = max([p for _, p in rows] + [state])
        ax.set_xlim(0, min(100, hi + 18) if hi < 90 else 115)
        ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: f"{v:.0f}%"))
        ax.grid(axis="x", color=_GRID, lw=0.8, zorder=0)
        clean(ax)
        pts = f"{_BAND_POINTS:.0f}"
        ax.legend(handles=[Patch(color=_hex(STATUS_COLOR[k]), label=l) for k, l in (
            ("LOW", f"LOW  >{pts} pts below state"), ("MODERATE", f"MODERATE  within {pts} pts"),
            ("HIGH", f"HIGH  >{pts} pts above state"))],
            loc="upper center", bbox_to_anchor=(0.5, -0.08), ncol=3, frameon=False,
            fontsize=8)
        out["areas"] = os.path.join(cfg["out_dir"], "cohort_areas.png")
        fig.savefig(out["areas"], dpi=150, bbox_inches="tight", facecolor=_SURF)
        plt.close(fig)
    except Exception as e:                                       # noqa: BLE001
        log.warning(f"[cohort] area chart failed (non-fatal): {e}")
    return out


# ── report section ────────────────────────────────────────────────────────────

def _table(doc, headers, rows, bold_rows=(), left_cols=(0,), widths_cm=None):
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.shared import Cm
    from dst_data_analysis_report.pipeline.core.word import dat, hdr
    tbl = doc.add_table(rows=1, cols=len(headers))
    tbl.style = "Table Grid"
    for ci, h in enumerate(headers):
        hdr(tbl.cell(0, ci), h)
    # repeat the header row when the table runs onto the next page
    trpr = tbl.rows[0]._tr.get_or_add_trPr()
    rep = OxmlElement("w:tblHeader"); rep.set(qn("w:val"), "true"); trpr.append(rep)
    for ri, vals in enumerate(rows, 1):
        cells = tbl.add_row().cells
        for ci, val in enumerate(vals):
            dat(cells[ci], val, alt=ri % 2 == 0, bold=(ri - 1) in bold_rows,
                align=(WD_ALIGN_PARAGRAPH.LEFT if ci in left_cols
                       else WD_ALIGN_PARAGRAPH.CENTER))
    if widths_cm:
        tbl.autofit = False
        lay = OxmlElement("w:tblLayout"); lay.set(qn("w:type"), "fixed")
        tbl._tbl.tblPr.append(lay)
        for gc, w in zip(tbl._tbl.tblGrid.findall(qn("w:gridCol")), widths_cm):
            gc.set(qn("w:w"), str(int(Cm(w).twips)))
        for row in tbl.rows:
            for cell, w in zip(row.cells, widths_cm):
                cell.width = Cm(w)
    return tbl


def _colour_status(tbl, pct_col, st_col):
    """Colour % and Status by band; rows without a band (Total) stay as they are."""
    from dst_data_analysis_report.pipeline.core.word import GREY_RGB, STATUS_COLOR
    for r in tbl.rows[1:]:
        st = r.cells[st_col].text.strip()
        if st not in STATUS_COLOR:
            continue
        for cell in (r.cells[pct_col], r.cells[st_col]):
            for run in cell.paragraphs[0].runs:
                run.font.bold = True
                run.font.color.rgb = STATUS_COLOR.get(st, GREY_RGB)


def _picture(doc, path):
    from docx.shared import Inches
    if path and os.path.exists(path):
        doc.add_picture(path, width=Inches(5.8))


def build_cohort_section(doc, cfg, heading_num="7", partner=False):
    """ONE section, TWO sub-sections, each a table first then its chart.
    Aggregate counts only, identical in both docs (partner is kept for the
    caller's signature). No-op when the stage produced nothing."""
    data = cfg.get("cohort_data")
    if not data:
        return
    from dst_data_analysis_report.pipeline.core.word import (GREY_RGB, add_heading, add_hyperlink,
                                 add_para, two_col_table)

    n, cur, done, area = (data["n_cycles"], data["in_progress"], data["done"],
                          data["area"])
    tr = data["total"]["rows"]
    c1 = tr[0]["treated"]
    state = data["state_pct"]
    H = heading_num

    def formula(text):
        add_para(doc, f"Formula:  {text}", size=8, color=GREY_RGB)

    def link_line(text, label, link, path):
        """'<text>: <label> ↗' (Drive link, else the local file name);
        label None = not linked from this doc."""
        from docx.shared import Pt
        p = add_para(doc, f"{text}: ", size=8, color=GREY_RGB)
        if label and link:
            add_hyperlink(p, f"{label} ↗", link)
            return
        run = p.add_run(os.path.basename(path) if label and path
                        else "shared in the internal report")
        run.font.size, run.font.color.rgb = Pt(8), GREY_RGB

    note = (f"Each child is counted once per cycle, across every cycle of this "
            f"campaign (data up to {cfg['LTE'][:10]}). The cohort is the {c1:,} "
            f"children treated in Cycle 1.")
    if cur:
        note += (f" Cycle {cur} is in progress: its figures are to date and it is "
                 f"not yet counted in the full-course figures.")
    add_heading(doc, f"{H}.  Children Across Cycles", 4)
    add_para(doc, note, size=9, color=GREY_RGB)

    # ── N.1 per cycle: table, chart, what this shows ──────────────────────
    add_heading(doc, f"{H}.1  Children Treated per Cycle", 5)
    add_para(doc, "How many children were treated in each cycle, and whether they "
                  "had been treated before.", size=9, color=GREY_RGB)
    rows = []
    for k, r in enumerate(tr, 1):
        live = k == cur
        rows.append([f"Cycle {k}" + (" (to date)" if live else ""), f"{r['treated']:,}",
                     f"{r['returning']:,}" if k > 1 else "-", f"{r['new']:,}",
                     "in progress" if live else f"{r['every']:,}",
                     "-" if live else _pct(r["every"], c1)])
    _table(doc, ["Cycle", "Children treated", "Treated in an earlier cycle",
                 "First treatment this season", "Treated in every cycle so far",
                 "% of Cycle 1 children"], rows,
           widths_cm=[3.0, 2.4, 2.6, 2.8, 2.8, 2.4])
    formula("Children treated = Treated in an earlier cycle + First treatment this "
            "season   |   Treated in every cycle so far = treated in Cycle 1 AND every "
            "cycle up to this one   |   % of Cycle 1 children = Treated in every cycle "
            "so far / Cycle 1 children treated x 100")
    _picture(doc, data["charts"].get("cycles"))
    if done >= 2:
        ev = tr[done - 1]["every"]
        later = sum(r["new"] for r in tr[1:])
        add_para(doc, "What this shows", size=9, bold=True)
        two_col_table(doc, [
            (f"Full course (Cycles 1-{done})",
             f"{ev:,} of {c1:,} Cycle 1 children ({_pct(ev, c1)})"),
            ("Missed at least one cycle", f"{c1 - ev:,}  ({_pct(c1 - ev, c1)})"),
            (f"First treated in Cycles 2-{n}", f"{later:,}"),
        ])
        formula(f"Full course % = Treated in every completed cycle / Cycle 1 children "
                f"treated x 100   |   Missed = Cycle 1 children treated - Treated in "
                f"every completed cycle   |   First "
                f"treated in Cycles 2-{n} = sum of first treatments in those cycles")
    doc.add_paragraph()

    # ── N.2 by area: table, chart ─────────────────────────────────────────
    add_heading(doc, f"{H}.2  Children Treated per Cycle, by {area}", 5)
    if done < 2:
        add_para(doc, "Needs at least two completed cycles; shown from the next "
                      "cycle.", size=9, color=GREY_RGB)
    else:
        add_para(doc, f"Children treated in each cycle, then Cycle 1 children treated "
                      f"in every cycle up to Cycle {done}. Lowest full course first. "
                      f"Status compares each {area} with the state ({state:.1f}%).",
                 size=9, color=GREY_RGB)
        ev = tr[done - 1]["every"]
        rows = []
        for name, e in sorted(data["by_area"].items(),
                              key=lambda kv: (_full(kv[1], done)[2], kv[0])):
            a1, aev, ap = _full(e, done)
            rows.append([name] + [f"{r['treated']:,}" for r in e["rows"]]
                        + [f"{aev:,}", f"{ap:.1f}%", _band(ap, state)])
        rows.append(["Total"] + [f"{r['treated']:,}" for r in tr]       # last row
                    + [f"{ev:,}", _pct(ev, c1), "-"])
        cyc_w = max(1.4, min(2.0, 9.0 / n))
        tbl = _table(doc, [area] + [f"Cycle {k}" + (" (to date)" if k == cur else "")
                                    for k in range(1, n + 1)]
                     + [f"Every cycle (1-{done})", "% every cycle", "Status"],
                     rows, bold_rows=(len(rows) - 1,),
                     widths_cm=[2.8] + [cyc_w] * n + [2.0, 1.7, 2.4])
        _colour_status(tbl, n + 2, n + 3)
        formula(f"Cycle columns = children treated in that cycle   |   Every cycle = "
                f"Cycle 1 children treated in all of Cycles 1-{done}   |   % every cycle "
                f"= Every cycle / Cycle 1 x 100   |   Status: LOW more than "
                f"{_BAND_POINTS:.0f} points below the state, HIGH more than "
                f"{_BAND_POINTS:.0f} points above, MODERATE in between")
        _picture(doc, data["charts"].get("areas"))
    # counts only (no names): linked from both docs
    link_line(f"Full list by {area} and health facility"
              + (f", with the count of children yet to be reached in Cycle {cur}"
                 if data.get("reach_counts") else ""),
              "Cohort Data", cfg.get("cohort_drive_link"), (cfg.get("cohort_files") or [""])[0])
    doc.add_paragraph()

    doc.add_paragraph()


# ── children yet to be reached in the cycle in progress ───────────────────────
# While a cycle runs, lists every child treated in an earlier cycle of this
# campaign who has not yet been treated in the current one, with the
# beneficiary ID and the boundary (LGA / ward / health facility), so
# supervisors can send CDDs to those places and keep children on the full
# course. NO NAMES (user decision 2026-10-09: child / household head names are
# PII); the CDD looks the child up by beneficiary ID in the app.
#   Priority 1  received every earlier cycle  — reach first (full course)
#   Priority 2  received some earlier cycles
# Beneficiary ID: project-task Data.additionalDetails.beneficiaryId (plain; the
# individual index holds it ENCRYPTED). Reuses core.es.scroll_batches, so
# DST_ES_PAGE_SIZE applies.
# Output (user decision 2026-10-09: two sheets): the "Children To Reach" sheet
# (SUMMARY + one tab per LGA, one row per child: ward, facility,
# beneficiary ID, Yes/No per earlier cycle, the cycle in progress marked "Yet
# to reach", priority), uploaded to Drive but not linked from the report;
# plus two count columns on the Cohort Data sheet (BY LGA / BY HEALTH
# FACILITY), linked under N.2. Rows are collected once per day (the beneficiary-ID lookups
# are the heaviest ES work) and cached; later runs that day reuse the cache.
# No switch of its own: it follows COHORT_REPORT_DEFAULT / DST_COHORT_REPORT.


def _reach_query(cfg, cycles, cur, area_key, size):
    earlier = cycles[:cur - 1]
    bh = "Data.boundaryHierarchy"
    return {
        "size": size,
        "query": {"bool": {
            "filter": _scope(cfg),
            "must_not": [_treated(cycles[cur - 1])],
            "should": [_treated(c) for c in earlier],
            "minimum_should_match": 1}},
        "_source": (["Data.projectBeneficiaryClientReferenceId",
                     f"{bh}.{area_key}", f"{bh}.ward", f"{bh}.healthFacility"]
                    + earlier),
    }


def _reach_beneficiary_ids(cfg, pb_refs, size):
    """{pb clientReferenceId: beneficiaryId}, one task per beneficiary."""
    pb_field = "Data.projectBeneficiaryClientReferenceId.keyword"
    out = {}
    for i in range(0, len(pb_refs), size):
        batch = pb_refs[i:i + size]
        q = {"size": len(batch),
             "query": {"bool": {"filter": _scope(cfg) + [
                 {"terms": {pb_field: batch}},
                 {"exists": {"field": "Data.additionalDetails.beneficiaryId"}}]}},
             "collapse": {"field": pb_field},
             "_source": ["Data.projectBeneficiaryClientReferenceId",
                         "Data.additionalDetails.beneficiaryId"]}
        r = es_post(f"{cfg['es_url']}/{cfg['ES_INDEX_TASK']}/_search",
                    json=q, auth=cfg["es_auth"], verify=False, timeout=120)
        r.raise_for_status()
        for h in r.json()["hits"]["hits"]:
            d = h["_source"].get("Data", {})
            pb = d.get("projectBeneficiaryClientReferenceId") or ""
            bid = (d.get("additionalDetails") or {}).get("beneficiaryId") or ""
            if pb and bid:
                out[pb] = str(bid)
    return out


def _reach_collect(cfg, data):
    from dst_data_analysis_report.pipeline import analyze
    cycles, cur, area_key = data["cycle_fields"], data["in_progress"], data["area_key"]
    earlier = cycles[:cur - 1]
    size = analyze._BATCH
    rows = []
    for batch in scroll_batches(cfg["es_url"], data["index"],
                                _reach_query(cfg, cycles, cur, area_key, size),
                                cfg["es_auth"], "children-to-reach"):
        src = []
        for h in batch:
            s = h["_source"]
            d = s.get("Data", {})
            pb = d.get("projectBeneficiaryClientReferenceId") or ""
            if pb:
                src.append((pb, d.get("boundaryHierarchy") or {},
                            [(s.get(c) or 0) > 0 for c in earlier]))
        bids = _reach_beneficiary_ids(cfg, list({pb for pb, _, _ in src}), size)
        for pb, bh, treated in src:
            rows.append({
                "area": bh.get(area_key) or "Unknown",
                "ward": bh.get("ward") or "", "hf": bh.get("healthFacility") or "",
                "bid": bids.get(pb, ""),
                "cyc": treated,                     # yes/no per earlier cycle
                "priority": 1 if all(treated) else 2,
            })
    return rows


def _reach_run(cfg, data):
    """Collect today's children-to-reach rows (first run of the day) or reload
    them from the day's cache; stash the summary on cfg['children_to_reach'].
    Returns {"as_of", "rows", ...} for the Children To Reach sheet, or None."""
    import gzip
    import json
    cur = data.get("in_progress")
    if not cur or cur < 2:
        log.info("[cohort] no later cycle in progress — no children-to-reach list")
        return None
    day = datetime.now().strftime("%Y-%m-%d")
    cache = os.path.join(cfg["out_dir"], "children_to_reach", f"{day}-ids.json.gz")
    if os.path.exists(cache):
        with gzip.open(cache, "rt", encoding="utf-8") as f:
            reach = json.load(f)
        log.info(f"[cohort] reusing today's children-to-reach list "
                 f"({reach['total']:,}, as of {reach['as_of']}) — built once per day")
    else:
        log.info(f"[cohort] {cfg['state_name']}: children yet to be reached in "
                 f"Cycle {cur} ...")
        as_of = datetime.now().strftime("%Y-%m-%d %H:%M")
        rows = _reach_collect(cfg, data)
        rows.sort(key=lambda r: (r["area"], r["ward"], r["hf"], r["bid"]))
        reach = {"cycle": cur, "as_of": as_of, "total": len(rows),
                 "p1": sum(r["priority"] == 1 for r in rows),
                 "no_beneficiary_id": sum(not r["bid"] for r in rows),
                 "rows": rows}
        os.makedirs(os.path.dirname(cache), exist_ok=True)
        with gzip.open(cache, "wt", encoding="utf-8") as f:
            json.dump(reach, f)
        log.info(f"[cohort] {reach['total']:,} children yet to be reached in Cycle {cur} "
                 f"({reach['p1']:,} received every earlier cycle); no beneficiary "
                 f"ID {reach['no_beneficiary_id']:,}")
    cfg["children_to_reach"] = {k: v for k, v in reach.items() if k != "rows"}
    return reach


# ── stage entry point ─────────────────────────────────────────────────────────

def run(cfg):
    """Build the cohort numbers, the Cohort Data Excel and the two charts from
    the cross-cycle index.

    Returns [xlsx_path] on success, None on the no-op (flag off, ITN, or no
    data). Callers wrap it non-fatally."""
    if not _flag_on(cfg):
        log.info("[cohort] cohort report disabled (DST_COHORT_REPORT / "
                 "COHORT_REPORT_DEFAULT) — skipped")
        return None
    # SMC and AZM only: per-child cycles. Every other drug type (ITN / LLIN
    # bed nets, anything new) is skipped.
    if str(cfg.get("drug_type", "")).upper() not in _COHORT_DRUG_TYPES:
        log.info(f"[cohort] {cfg.get('drug_type')} campaign — cohort is SMC/AZM "
                 f"only, skipped")
        return None
    # Cumulative report only: the cohort barely moves day to day. Mid-campaign
    # the current cycle is shown "to date" and kept out of the full-course
    # figures.
    if not cfg.get("cumulative"):
        log.info("[cohort] daily report — cohort is cumulative-only, skipped")
        return None
    index = _index(cfg)
    log.info(f"[cohort] {cfg['state_name']} cohort from {index} "
             f"(campaign {cfg.get('campaign_number') or '-'}) ...")
    cycles, area_key, has_hf = _layout(cfg, index)
    if not cycles or not area_key:
        log.error(f"[cohort] {index}: no cycle fields or area level in the "
                  f"mapping — no cohort report this run")
        return None

    raw, raw_hf = _fetch(cfg, index, cycles, area_key, has_hf)
    n = _active_cycles(raw, cycles)
    if not raw or n == 0:
        log.error("[cohort] zero treated children in the index — no cohort "
                  "report this run")
        return None
    if n < 2:
        log.info("[cohort] Cycle 1 only — no cohort until a later cycle starts")
        return None

    keys = {k for g in raw.values() for k in g}
    tot_raw = {k: sum(g[k] for g in raw.values()) for k in keys}
    by_area = {name: {"rows": _derive(g, n), "any": g["any"]}
               for name, g in raw.items()}
    by_hf = {key: {"rows": _derive(g, n), "any": g["any"]}
             for key, g in raw_hf.items() if g.get("treated1")}
    total = {"rows": _derive(tot_raw, n), "any": tot_raw["any"]}

    # the last cycle is in progress until the campaign's last day
    final = cfg.get("DAY", 0) >= cfg.get("campaign_days", 0)
    done = n if final else n - 1
    c1 = total["rows"][0]["treated"]
    data = {
        "index": index, "area": _AREA_LABELS[area_key], "area_key": area_key,
        "cycle_fields": cycles[:n], "n_cycles": n,
        "in_progress": None if final else n, "done": done,
        "docs": tot_raw["any"], "by_area": by_area, "by_hf": by_hf,
        "total": total,
        "state_pct": _ratio(total["rows"][done - 1]["every"], c1) if done else 0.0,
    }
    data["charts"] = _charts(cfg, data)
    # children yet to be reached in the cycle in progress — its own
    # Children To Reach sheet; never blocks the cohort or the report
    reach = None
    try:
        reach = _reach_run(cfg, data)
    except Exception as e:                                       # noqa: BLE001
        log.error(f"[cohort] children-to-reach list FAILED (non-fatal): {e}",
                  exc_info=True)
    data["reach_counts"] = bool(reach and reach["rows"])
    path, reach_path = _write_xlsx(cfg, data, reach)
    cfg["cohort_data"] = data
    cfg["cohort_files"] = [p for p in (path, reach_path) if p]
    cfg["cohort_drive_link"] = _publish(cfg, path, "Cohort Data")
    cfg["reach_drive_link"] = _publish(cfg, reach_path, "Children To Reach")
    log.info(f"[cohort] done — {tot_raw['any']:,} children, {n} cycles "
             f"({done} completed), {len(by_area)} {data['area']}s, "
             f"{len(by_hf)} facilities")
    return cfg["cohort_files"]
