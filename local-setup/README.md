# HCM Local Setup

Run the **Health Campaign Management (HCM)** platform on your own laptop using Docker — works on **Windows, macOS and Linux**. Everything (database seed, services, two web UIs, API gateway) is in this folder.

This guide is written for someone who has **never set up a developer environment before**. Follow the steps in order. You should be done in about **15 minutes** (most of which is waiting for downloads).

---

## 1. What you need

### Hardware (your machine)

| | Minimum | Recommended |
|---|---|---|
| **Free RAM** for Docker | **12 GB** | 16 GB |
| **Free disk space** | **20 GB** | 30 GB |
| Internet | Required for the first run (~16 GB of downloads) | — |

> Why 12 GB? The full stack idles at about **5.9 GB of RAM** across 45 containers, and briefly peaks at about **10 GB** when the admin UI loads for the first time. 12 GB leaves comfortable headroom for your operating system and browser.

### Software (programs)

You only **need to install these things yourself**:

| Program | Why it's needed | How to get it |
|---|---|---|
| **Git** | To download (clone) this project | Windows / macOS / Linux: [git-scm.com/downloads](https://git-scm.com/downloads) |
| **Docker Desktop** (Windows, macOS) **or** Docker Engine (Linux) | Runs all the services in containers | Windows / macOS: [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/) <br> Linux: the bootstrap script in step 4 will install it for you if it's missing |
| **WSL 2 with Ubuntu** (Windows only) | The bootstrap script is a bash script — Windows can only run it inside a Linux environment. WSL 2 (Windows Subsystem for Linux) provides that. | See the box below |

**Everything else** — Python, curl, database client, Kafka topics, seed data, encryption keys — is installed and configured automatically by the bootstrap script in step 4. **Do not run any other install commands by hand before that step.**

> **Windows users — one-time setup before anything else:**
>
> 1. Open **PowerShell as Administrator** (Start menu → right-click *Windows PowerShell* → *Run as administrator*).
> 2. Run this single command — it enables WSL 2 and installs Ubuntu as the default Linux distribution:
>    ```powershell
>    wsl --install
>    ```
> 3. **Restart your computer** when it asks.
> 4. After the restart, an Ubuntu window will open by itself and ask you to **create a Linux username and password**. Pick anything you like (the username is lowercase, no spaces; the password won't show as you type — that's normal).
> 5. Now install **Docker Desktop** from [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/). During install, leave **"Use WSL 2 based engine"** ticked. Open Docker Desktop once it's installed and wait until the whale icon at the bottom-left says **"Engine running"** (green).
> 6. In Docker Desktop go to **Settings → Resources → WSL Integration**, make sure **Ubuntu** is enabled, and click **Apply & Restart**.
>
> From now on, whenever this guide tells you to "open a terminal", **open the Ubuntu app** from your Start menu — not PowerShell, not Command Prompt.

---

## 2. Step-by-step setup

### Step 1 — Open a terminal

- **Windows:** open the **Ubuntu** app from the Start menu (the one you installed via `wsl --install` in the box above). Do **not** use PowerShell or Command Prompt — the bootstrap script will not run there.
- **macOS:** press `Cmd + Space`, type `Terminal`, press Enter.
- **Linux:** open your usual terminal (often `Ctrl + Alt + T`).

### Step 2 — Download (clone) the project

Copy and paste this into the terminal, then press Enter:

```bash
git clone -b hcm-docker-compose-uat-env https://github.com/egovernments/health-campaign-services.git
```

> `-b hcm-docker-compose-uat-env` tells git to download the **`hcm-docker-compose-uat-env`** branch (the one with this local setup).

### Step 3 — Enter the project folder

```bash
cd health-campaign-services/local-setup
```

From here onwards, **every command assumes you are inside this folder.** If you close the terminal and come back later, just re-run this `cd` command.

### Step 4 — Run the bootstrap script

This one command does everything: installs anything missing (on Linux/macOS), downloads all the container images, starts all 46 services, encrypts the demo user so login works, creates Kafka topics, and runs API smoke tests.

```bash
sudo ./scripts/bootstrap.sh
```

> **You will be asked for your computer's login password.** That's normal — `sudo` needs it to install software. Type it (the characters won't appear on screen — that's also normal) and press Enter.

> On **Windows (inside the Ubuntu/WSL terminal)** the command is exactly the same.

> On **macOS**, if it says "Permission denied" the first time, run `chmod +x ./scripts/bootstrap.sh` once, then re-run the `sudo` command above.

You will see colored progress lines:

```
[1/7] OS detected: ubuntu
[2/7] Checking / installing prerequisites
[3/7] Verifying DB seed dump is present
[4/7] Starting the stack
[5/7] Waiting for services to become healthy
[5b/7] Encrypting SYSTEM user PII so OAuth login works
[6/7] Ensuring project-factory consumer topics exist
[7/7] Running API smoke test
✅ Bootstrap complete.
```

When you see the green **`✅ Bootstrap complete.`** — the stack is running. **Go to step 5.**

> **First run pulls about 16 GB of container images.** On a typical home connection it takes 8 - 15 minutes. Just leave the terminal running. If something fails in the middle, look at **section 3 — Common errors** below; almost everything is fixed by re-running the same command.

### Step 5 — Check that everything is healthy

Open this URL in your web browser (click it):

> 🔗 **[http://localhost:28889](http://localhost:28889)**

This is **Gatus**, a live health dashboard. Within ~2 minutes every row should turn **green**.

If a few rows stay red after 5 minutes, just re-run `sudo ./scripts/bootstrap.sh` — the script is safe to run again and only retries the stragglers.

### Step 6 — Log in to the Admin (Workbench) UI

Open this URL in your web browser:

> 🔗 **[http://localhost:28080/workbench-ui/employee](http://localhost:28080/workbench-ui/employee)**

On the login page, type these values exactly (case-sensitive):

| Field | Value |
|---|---|
| **Username** | `SYSTEM` |
| **Password** | `eGov@123` |
| **City** | `mz` |

Click **Continue / Login**. You should land on a page with 12 cards (Manage Campaign, MDMS, Boundary, Localisation, etc.).

**🎉 If you see the 12 cards, your setup is complete.** You can stop here.

### Step 7 *(optional)* — Log in to the Payments UI as a field worker

Open this URL:

> 🔗 **[http://localhost:28080/payments-ui/employee](http://localhost:28080/payments-ui/employee)**

| Field | Value |
|---|---|
| **Username** | `EMP-DIST-002` |
| **Password** | `eGov@123` |
| **City** | `mz` |

This account has only the `DISTRIBUTOR` role (not admin) — useful for seeing how the UI behaves for a real field worker.

---

## 3. Common errors and quick fixes

> If something didn't work, find your error message below and copy-paste the fix. Almost every problem is fixed by re-running `sudo ./scripts/bootstrap.sh`.

### "git: command not found"

You haven't installed Git yet. Get it from [git-scm.com/downloads](https://git-scm.com/downloads) (Windows / macOS) or run:

```bash
sudo apt-get install -y git          # Ubuntu / Debian / WSL
brew install git                     # macOS (if you use Homebrew)
```

Then retry **step 2**.

### "docker: command not found" — on Windows or macOS

You haven't installed Docker Desktop yet, or it isn't running.

1. Install it from [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop/).
2. Open the Docker Desktop application.
3. Wait until the whale icon at the bottom-left says **"Engine running"** (green).
4. Re-run `sudo ./scripts/bootstrap.sh`.

### "docker: command not found" — on Linux

The bootstrap script tries to install Docker for you, but if it failed (no internet, unsupported distribution, etc.), install it by hand:

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

Then **log out and log back in** (so the group change takes effect) and re-run `sudo ./scripts/bootstrap.sh`.

### "permission denied while trying to connect to the Docker daemon socket"

Your user isn't in the `docker` group yet. Fix:

```bash
sudo usermod -aG docker $USER
```

Then **log out and log back in**, and re-run `sudo ./scripts/bootstrap.sh`.

### "Cannot connect to the Docker daemon at unix:///var/run/docker.sock"

Docker is installed but not running.

- **Windows / macOS:** open the Docker Desktop app and wait for the whale icon to turn green.
- **Linux:**
  ```bash
  sudo systemctl enable --now docker
  ```

Then re-run `sudo ./scripts/bootstrap.sh`.

### "Permission denied" on `./scripts/bootstrap.sh`

The script lost its executable bit (often happens after a Windows-formatted download). Fix:

```bash
chmod +x ./scripts/bootstrap.sh
sudo ./scripts/bootstrap.sh
```

### "Port already in use" during bootstrap

Another copy of the stack (or another program using the same port) is already running.

```bash
docker compose down
sudo ./scripts/bootstrap.sh
```

### Bootstrap says "some containers still need attention"

A few containers were slow to wake up. Just re-run the script — it only retries the ones that failed:

```bash
sudo ./scripts/bootstrap.sh
```

### Browser shows raw codes like `HCM_LOGIN_TITLE` instead of "Login"

The browser has cached a stale translation file. Open the browser's developer tools, then clear the cache for the page:

1. **Open DevTools:**
   - **Windows / Linux** (Chrome, Edge, Firefox): press **`F12`** *or* **`Ctrl + Shift + I`**
   - **macOS** (Chrome, Edge, Firefox): press **`Cmd + Option + I`**
   - **macOS Safari:** enable the Develop menu first (`Safari → Settings → Advanced → Show Develop menu`), then press **`Cmd + Option + I`**
2. Click the **Console** tab at the top of the DevTools panel.
3. Paste this line and press Enter:
   ```js
   localStorage.clear(); sessionStorage.clear(); location.reload(true);
   ```

<p align="center">
  <img src="./docs/screenshots/devtools-console-paste.png" alt="DevTools Console with the localStorage.clear() command pasted in" width="800">
</p>

The page will reload with proper text labels.

> Yes, this line is needed — the UI saves translations in the browser, and a stale save can leave you stuck on `HCM_LOGIN_TITLE` codes. The line empties that cache and reloads the page.

### Login error: "Invalid login credentials"

The bootstrap didn't finish the encryption step. Just re-run:

```bash
sudo ./scripts/bootstrap.sh
```

### Workbench page is blank (no cards after login)

The UI seed didn't apply. Run:

```bash
docker exec -i hcm-postgres psql -U egov -d egov < db/02-hcm-ui-seed.sql
docker exec hcm-redis redis-cli FLUSHALL
```

Then refresh the browser (and clear the browser cache as in the previous fix if needed).

### You want a completely clean reset

This **wipes the database** and starts over (adds about 5 minutes):

```bash
docker compose down -v
sudo ./scripts/bootstrap.sh
```

---

## 4. URLs to bookmark

All links below open in your browser. Click them.

| What it is | URL | Login |
|---|---|---|
| **Admin (Workbench) UI** | 🔗 [http://localhost:28080/workbench-ui/employee](http://localhost:28080/workbench-ui/employee) | `SYSTEM` / `eGov@123` / city `mz` |
| **Payments UI** (grievances, HR) | 🔗 [http://localhost:28080/payments-ui/employee](http://localhost:28080/payments-ui/employee) | `SYSTEM` / `eGov@123` / city `mz` |
| **Gatus** (health dashboard) | 🔗 [http://localhost:28889](http://localhost:28889) | no login |
| **Redpanda Console** (Kafka topics) | 🔗 [http://localhost:28082](http://localhost:28082) | no login |
| **MinIO Console** (file storage) | 🔗 [http://localhost:29001](http://localhost:29001) | `minioadmin` / `minioadmin` |
| **Portainer** (container manager) | 🔗 [http://localhost:29009](http://localhost:29009) | set on first visit |
| **Grafana** (traces) | 🔗 [http://localhost:13000](http://localhost:13000) | anonymous admin |
| **Kong Manager** (API gateway) | 🔗 [http://localhost:28002](http://localhost:28002) | no login |
| PostgreSQL (DBeaver / psql) | `localhost:25432`, db `egov` | user `egov` / pass `egov123` |

---

## 5. Day-to-day commands

```bash
# Stop the stack (keeps your data)
docker compose down

# Start it again later
docker compose up -d

# See which containers are running
docker compose ps

# Watch logs from one service (Ctrl + C to exit)
docker compose logs -f egov-user
```

After running `docker compose up -d`, give it about 2 minutes, then re-open Gatus to confirm everything is green again: 🔗 [http://localhost:28889](http://localhost:28889).

---

## 6. Resource usage — measured

These are real numbers measured on a running stack (45 long-running containers + 1 one-shot init container), captured with `docker stats --no-stream` right after a fresh `docker compose down && docker compose up -d`.

| Metric | Value |
|---|---|
| RAM in use, idle (just after startup, no UI activity) | **~5.9 GB** total |
| RAM peak (during first Workbench login + localization load) | **~10 GB** total |
| RAM declared upper limits (sum of compose limits) | ~23 GB |
| Docker images on disk | ~16 GB |
| Named volumes on disk (Postgres, MinIO, Grafana, etc.) | ~3 GB |

**Top RAM consumers when idle** (everything else is under 200 MB each):

| Container | RAM in use |
|---|---|
| `hcm-redpanda` (Kafka) | 285 MB |
| `hcm-egov-user` | 244 MB |
| `hcm-beneficiary-idgen` | 236 MB |
| `hcm-egov-filestore` | 225 MB |
| `hcm-household` | 224 MB |
| `hcm-health-project` | 219 MB |
| `hcm-health-expense-calculator` | 214 MB |
| `hcm-health-attendance` | 213 MB |
| `hcm-referralmanagement` | 206 MB |
| `hcm-stock` | 204 MB |

> The single largest brief spike is `hcm-egov-localization`, which jumps from ~130 MB at idle to **~3 GB** while loading the translation cache the first time the Workbench UI opens. After that it drops back down. This is why the recommended minimum is 12 GB of free RAM.

To see live numbers on your own machine:

```bash
docker stats --no-stream
```

---

## 7. For developers — deeper reference

If you only wanted to *run* the stack, you are done. Everything below is reference material for people building on top of it.

- **[STARTUP.md](./STARTUP.md)** — manual step-by-step (no bootstrap script)
- **[docs/HCM-Local-Setup-Report.docx](./docs/HCM-Local-Setup-Report.docx)** — director-friendly write-up
- **[docs/postman/](./docs/postman/)** — ready-to-import Postman collections
- **[api_automation_project/](./api_automation_project/)** — pytest API suite (135 passed / 2 skipped)

### Ports

#### Infrastructure
| Port | Service | Purpose |
|---|---|---|
| 25432 | PostgreSQL | Direct DB access (psql, DBeaver) |
| 26379 | Redis | Cache inspection |
| 28000 | **Kong (proxy)** | All API calls — used by Postman, pytest, server-side clients |
| 28001 | Kong (admin) | Kong Admin API |
| 28080 | **Frontend proxy** | Single-origin entrypoint for browser — UIs + API. Lands on Workbench |
| 28082 | Redpanda Console | Kafka topic browser |
| 28889 | **Gatus** | Service health dashboard |
| 29000 | MinIO | Object storage (filestore) |
| 29001 | MinIO Console | MinIO admin UI |
| 29009 | Portainer | Container management UI |
| 13000 | Grafana | Distributed traces (Tempo) |

#### HCM service ports (direct — bypass Kong)
| Port | Service | Default context path |
|---|---|---|
| 28093 | health-hrms | `/health-hrms` |
| 28095 | beneficiary-idgen | `/beneficiary-idgen` |
| 28101 | health-individual | `/health-individual` |
| 28102 | household | `/household` |
| 28103 | facility | `/facility` |
| 28104 | product | `/product` |
| 28105 | health-project | `/health-project` |
| 28106 | stock | `/stock` |
| 28107 | egov-user | `/user` |
| 28108 | referralmanagement | `/referralmanagement` |
| 28109 | egov-workflow-v2 | `/egov-workflow-v2` |
| 28110 | health-expense-calculator | `/health-expense-calculator` |
| 28111 | health-muster-roll | `/health-muster-roll` |
| 28112 | health-attendance | `/health-attendance` |
| 28113 | health-expense | `/health-expense` |
| 28114 | plan-service | `/plan-service` |
| 28115 | census-service | `/census-service` |
| 28116 | boundary-management | `/boundary-management` |
| 28117 | resource-generator | `/resource-generator` |
| 28118 | excel-ingestion | `/excel-ingestion` |
| 28119 | project-factory | `/project-factory` |
| 28190 | **pgr-services** | `/pgr` |

> All API calls from clients / Postman / pytest go through **Kong on port 28000**. The per-service ports above are for direct service-to-service inspection if you need it.

### Directory structure

```
local-setup/
├── docker-compose.yml          # Main compose file — 46 services
├── .env                        # Environment overrides (optional)
│
├── db/
│   ├── full-dump.sql           # Seed: schema + master data + PGR seed + MICROPLAN
│   │                           # boundary + DROPs + ALTER ROLE/DB search_path
│   │                           # + sequence resets. ~34 MB. Loaded on first init.
│   └── 02-hcm-ui-seed.sql      # Supplementary UI seed (~6 MB) — applied after
│                               # full-dump.sql. Adds 9 role grants to SYSTEM user,
│                               # 6,288 ACCESSCONTROL-ACTIONS-TEST.actions-test
│                               # MDMS rows (so workbench home renders cards),
│                               # and 19,551 localization rows.
│
├── nginx/                      # Frontend proxy + MDMS rewrite + UI globalConfigs
├── kong/                       # Kong declarative config (routes, Lua plugins)
├── configs/persister/          # Persister YAML mappings per domain
├── otel/                       # OpenTelemetry → Tempo → Grafana
├── gatus/                      # Health monitor config
│
├── scripts/
│   ├── bootstrap.sh            # One-shot bootstrap (the script you ran in step 4)
│   ├── build_report.py
│   └── build_postman.py
│
├── docs/
│   ├── screenshots/            # PNG snapshots referenced from this README
│   ├── HCM-Local-Setup-Report.docx
│   └── postman/                # Two ready-to-import Postman collections
│
└── api_automation_project/     # Pytest API-automation suite
```

### Where data comes from

`db/full-dump.sql` + `db/02-hcm-ui-seed.sql` are mounted into Postgres' init dir and applied **in order** on first DB init. Self-contained — no remote fetch, no manual restore. Contains: schema, MDMS, ~607 TCHAD boundary rows, MICROPLAN boundary, 17 HCM roles, 1 SYSTEM admin, ~1000 sample users, 501 HRMS employees, 124 products, demo `EMP-DIST-002` DISTRIBUTOR user, NULL-relaxation patch on `health.project`.

### Kong Lua plugins (test-contract fixes)

`kong/kong.yml` includes small Lua plugins for: invalid-tenant → 401 routing, MDMS create response casing, PGR workflow transition stubs, household-member head-of-household toggle. See file comments for full list.

### Known issues (not fixed in this stack)

Found during a full manual CRUD sweep — documented so demo flows steer around them.

| # | Symptom | Affected endpoints | Workaround |
|---|---|---|---|
| A | `MalformedURLException: 8080health-individual/...` | `/health-attendance/log/v1/_create`, `/health-muster-roll/v1/_create` | Prepend leading `/` to the `*_SEARCH_ENDPOINT` env vars in `docker-compose.yml`, then `docker compose up -d --no-deps <service>` |
| B | API returns 200 but row never lands in DB | `/health-project/user-action/v1/_create`, `/health-project/user-location/v1/_create` | Add `save-user-action-project-bulk-topic` to the persister consumer subscription |
| C | `Map.get(Object) is null` | `/health-expense/bill/v1/_create` | Seed `egf-master.BusinessService` MDMS rows for tenant `mz` |
| D | `JSONPath null` retried 10x | `/pgr/v2/request/_create` (when `address.geoLocation` is missing) | Always pass `address.geoLocation.latitude/longitude` |
| E | NPE (no message) | `/health-project/task/v1/_create` (when `address` is missing) | Always include `address` in Task payload |
| F | `BOUNDARY_SERVICE_SEARCH_ERROR` | Anything passing `address.locality.code` not in `public.boundary` | Omit `address` or use `ADMIN_TC` |
| G | OAuth fails for legacy DISTRIBUTORs (rows with `317304\|...`) | All seeded DISTRIBUTORs except `EMP-DIST-002` | Use `EMP-DIST-002` (encrypted with local key) |

### Test users

| Username | Password | Role | Notes |
|---|---|---|---|
| `SYSTEM` | `eGov@123` | SUPERUSER + 8 admin roles | Workbench admin |
| `EMP-DIST-002` | `eGov@123` | DISTRIBUTOR | Field worker; survives `down -v && up -d` |

### OAuth login (for API testing)

```bash
curl -s -X POST http://localhost:28000/user/oauth/token \
  -H 'Authorization: Basic ZWdvdi11c2VyLWNsaWVudDo=' \
  -d 'username=SYSTEM&password=eGov@123&grant_type=password&scope=read&tenantId=mz&userType=EMPLOYEE'
```

### Default credentials reference

| Service | Username | Password |
|---|---|---|
| PostgreSQL | `egov` | `egov123` |
| MinIO | `minioadmin` | `minioadmin` |
| Portainer | (set on first visit) | — |
| Grafana | (anonymous admin) | — |

