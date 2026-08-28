# Campaign Data Analysis Report - report execution image
#
# One pod per campaign, launched by dst_campaign_run (KubernetesPodOperator).
# The pipeline's dependencies live HERE, not in the shared Airflow image, which
# on a hosted Airflow cannot be changed - so a dependency change becomes a
# rebuild plus an Airflow Variable edit, with no Helm or cluster access.
#
# Follows utilities/analysis-report's multi-stage shape (smaller image, non-root)
# rather than the single-stage one, on Python 3.12 to match where the pipeline is
# tested.
#
# BUILD CONTEXT: this folder. dst_common lives in
# utilities/airflow-dags/dags/dst_common and is staged in by the GitHub Actions
# workflow before the build - it is not duplicated in git. Build locally with:
#
#   cp -r ../airflow-dags/dags/dst_common ./dst_common
#   docker build -t campaign-data-analysis-report:dev .
#   rm -rf ./dst_common

# Stage 1: build the virtualenv
FROM python:3.12-slim AS builder

# gcc/g++ for any wheel without a manylinux binary.
RUN apt-get update && apt-get install -y --no-install-recommends \
        gcc g++ \
    && rm -rf /var/lib/apt/lists/*

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

WORKDIR /build

# requirements BEFORE the code, so this layer caches across code-only changes.
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir -r requirements.txt

# Stage 2: runtime
FROM python:3.12-slim

COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

WORKDIR /app

# main.py      the pod entrypoint - one campaign per pod
# pipeline/    the report logic (Airflow-free by design)
# dst_common/  the stage chain and error classification. Its Airflow import sits
#              behind a guarded try, so it runs here with no Airflow installed.
COPY main.py .
COPY pipeline/ ./pipeline/
COPY dst_common/ ./dst_common/

# Matplotlib needs a writable config dir and must not try to open a display.
ENV MPLBACKEND=Agg \
    MPLCONFIGDIR=/tmp/matplotlib \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

# Scratch only - every durable artifact goes to Google Drive, so nothing here
# needs to outlive the pod.
RUN mkdir -p /tmp/dst-out /tmp/matplotlib

# Non-root, as analysis-report does.
RUN useradd --create-home --shell /bin/bash dst \
    && chown -R dst:dst /app /tmp/dst-out /tmp/matplotlib
USER dst

# Fail the BUILD if the image cannot import what it needs, so a missing
# dependency surfaces in CI rather than on the first campaign run.
RUN python -c "import gspread, pandas, openpyxl, docx, dotenv, matplotlib, googleapiclient, kafka; import pipeline.config, dst_common.campaign_runner; print('image self-check OK')"

ENTRYPOINT ["python", "main.py"]
