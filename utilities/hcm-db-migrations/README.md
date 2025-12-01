# HCM Database Migrations

Database migration tool for HCM Reports using Alembic with automatic deployment via Helm hooks.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  REPOSITORY STRUCTURE                                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  health-campaign-services/              ◄── This repo (migration code)      │
│  └── utilities/hcm-db-migrations/                                           │
│      ├── alembic/versions/*.py          ◄── Migration files                 │
│      ├── alembic.ini                                                        │
│      └── requirements.txt                                                   │
│                                                                             │
│  DIGIT-DevOps/                          ◄── DevOps repo (Helm chart)        │
│  └── deploy-as-code/helm/                                                   │
│      ├── charts/utilities/hcm-db-migrations/                                │
│      │   ├── Chart.yaml                                                     │
│      │   ├── values.yaml                ◄── Default configuration           │
│      │   └── templates/migration-job.yaml                                   │
│      └── environments/                                                      │
│          └── unified-dev.yaml           ◄── Environment-specific config     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Project Structure

```
hcm-db-migrations/
├── alembic/
│   ├── versions/                    # Migration files
│   │   └── 20251201_..._001_create_hcm_report_metadata.py
│   ├── env.py                       # Alembic environment config
│   └── script.py.mako               # Migration template
├── alembic.ini                      # Alembic configuration
├── requirements.txt                 # Python dependencies
├── .env.example                     # Environment template (local dev)
└── README.md
```

## How Deployment Works (Automatic)

When you deploy via Helm, migrations run **automatically** via Helm hooks:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  AUTOMATIC DEPLOYMENT FLOW                                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. Developer adds migration file                                           │
│     └── alembic/versions/002_add_status_column.py                           │
│                                                                             │
│  2. Git push to branch (e.g., airflow-analysis)                             │
│                                                                             │
│  3. Helm deploy triggers                                                    │
│     └── helm upgrade --install hcm-db-migrations ...                        │
│                                                                             │
│  4. Helm hook runs Job (pre-install, pre-upgrade)                           │
│     ┌─────────────────────────────────────────────────────────────────┐    │
│     │  Init Container: git-sync                                        │    │
│     │  └── Pulls latest code from git                                  │    │
│     │                                                                  │    │
│     │  Main Container: python:3.10-slim                                │    │
│     │  └── pip install -r requirements.txt                             │    │
│     │  └── alembic upgrade head  ◄── Runs automatically                │    │
│     └─────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  5. Database schema updated                                                 │
│                                                                             │
│  6. Job completes and self-deletes                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Adding a New Migration

### Step 1: Create migration file locally

```bash
cd health-campaign-services/utilities/hcm-db-migrations
alembic revision -m "add_status_column"
```

This creates a file like: `alembic/versions/20251202_143000_002_add_status_column.py`

### Step 2: Edit the migration file

```python
"""add_status_column

Revision ID: 002
Revises: 001
"""
from alembic import op
import sqlalchemy as sa

revision = '002'
down_revision = '001'  # Points to previous migration

def upgrade():
    op.add_column('hcm_report_metadata',
        sa.Column('status', sa.String(50), nullable=True))

def downgrade():
    op.drop_column('hcm_report_metadata', 'status')
```

### Step 3: Test locally (optional)

```bash
# Setup
cp .env.example .env
# Edit .env with your database credentials

python -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Test migration
alembic upgrade head
```

### Step 4: Commit and push

```bash
git add .
git commit -m "Add status column to hcm_report_metadata"
git push origin airflow-analysis
```

### Step 5: Deploy (migrations run automatically)

```bash
# Migrations run automatically via Helm hook
helm upgrade --install hcm-db-migrations \
  DIGIT-DevOps/deploy-as-code/helm/charts/utilities/hcm-db-migrations \
  -f DIGIT-DevOps/deploy-as-code/helm/environments/unified-dev.yaml \
  -n health
```

**That's it! No manual `alembic upgrade head` needed in production.**

## Local Development

### Setup

```bash
cd hcm-db-migrations

# Create virtual environment
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Configure database
cp .env.example .env
# Edit .env with your database credentials
```

### Connect to Local PostgreSQL

```bash
# .env file
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/unifieddevdb
```

### Connect to Remote Dev Database

```bash
# .env file
DB_HOST=unified-dev-db-new.czvokiourya9.ap-south-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=unifieddevdb
DB_USER=<username>
DB_PASSWORD=<password>
```

### Run Local PostgreSQL with Docker

```bash
docker run -d \
  --name hcm-postgres \
  -e POSTGRES_DB=unifieddevdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15
```

## Common Commands

| Command | Description |
|---------|-------------|
| `alembic upgrade head` | Run all pending migrations |
| `alembic upgrade +1` | Run next migration only |
| `alembic downgrade -1` | Rollback last migration |
| `alembic downgrade base` | Rollback all migrations |
| `alembic current` | Show current version |
| `alembic history` | Show migration history |
| `alembic revision -m "description"` | Create new migration |
| `alembic upgrade head --sql` | Preview SQL without executing |

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | Full PostgreSQL URL | - |
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 5432 |
| `DB_NAME` | Database name | unifieddevdb |
| `DB_USER` | Database user | postgres |
| `DB_PASSWORD` | Database password | postgres |

**Priority:** `DATABASE_URL` > Individual `DB_*` variables

## Helm Configuration

### values.yaml (defaults)

Located at: `DIGIT-DevOps/deploy-as-code/helm/charts/utilities/hcm-db-migrations/values.yaml`

```yaml
namespace: health

git:
  repo: ""      # Set in environment file
  branch: ""    # Set in environment file

image:
  repository: python
  tag: "3.10-slim"

db:
  configMap:
    name: egov-config      # DIGIT standard ConfigMap
    hostKey: db-host
    nameKey: db-name
  secret:
    name: db               # DIGIT standard Secret
    usernameKey: username
    passwordKey: password
```

### unified-dev.yaml (environment-specific)

Located at: `DIGIT-DevOps/deploy-as-code/helm/environments/unified-dev.yaml`

```yaml
hcm-db-migrations:
  git:
    repo: "git@github.com:egovernments/health-campaign-services"
    branch: "airflow-analysis"
```

## Database Credentials

Credentials are **automatically** picked from existing DIGIT cluster secrets:

- **ConfigMap:** `egov-config` (contains `db-host`, `db-name`)
- **Secret:** `db` (contains `username`, `password`)

You do **NOT** need to create or manage these - they already exist in the DIGIT cluster.

## Tables Created

### hcm_report_metadata

| Column | Type | Description |
|--------|------|-------------|
| id | SERIAL | Primary key |
| dag_run_id | VARCHAR(255) | Airflow DAG run ID |
| dag_name | VARCHAR(100) | DAG name |
| campaign_number | VARCHAR(100) | Campaign identifier |
| report_name | VARCHAR(100) | Report type |
| trigger_frequency | VARCHAR(50) | Daily/Weekly/Monthly |
| trigger_date | TIMESTAMP | When report was triggered |
| filestore_id | VARCHAR(255) | UUID from filestore |
| tenant_id | VARCHAR(50) | Tenant identifier |
| created_at | TIMESTAMP | Record creation time |

**Indexes:**
- `idx_hcm_report_campaign` - campaign_number
- `idx_hcm_report_name` - report_name
- `idx_hcm_report_frequency` - trigger_frequency
- `idx_hcm_report_trigger_date` - trigger_date
- `idx_hcm_report_campaign_report_freq` - Composite (campaign, report, frequency)

## Troubleshooting

### Check current migration status
```bash
alembic current
alembic history --verbose
```

### Migration already applied error
```bash
# Force stamp to specific version (use with caution)
alembic stamp head
```

### Connection refused
- Verify database is running
- Check DATABASE_URL or DB_* variables
- Ensure network connectivity

### Permission denied
- Verify database user has CREATE TABLE permissions
- Check schema permissions

### View Kubernetes job logs
```bash
kubectl logs job/hcm-db-migration-<revision> -n health
```

## Summary: Local vs Deployed

| Action | Local | Deployed (K8s) |
|--------|-------|----------------|
| Create migration file | Manual | Manual |
| Run `alembic upgrade head` | **Manual** | **Automatic** (Helm hook) |
| Git push required? | No | Yes |
| Database credentials | `.env` file | DIGIT secrets (auto) |
