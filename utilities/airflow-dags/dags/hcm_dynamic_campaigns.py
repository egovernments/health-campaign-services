"""
hcm_dynamic_campaigns.py

DAG 2: Campaign Report Processor (Trigger-only DAG)

This DAG is triggered by hcm_campaign_scheduler with matched campaigns in conf.
It dynamically creates KubernetesPodOperator tasks (one per campaign) that run in PARALLEL.

Flow:
1. build_payload: Reads matched campaigns from conf, calculates date ranges, builds env vars
2. campaign_pods: Dynamically expands to N pods (one per campaign), all run in parallel
3. print_summary: Logs final summary of processed campaigns

Triggered by: hcm_campaign_scheduler
Schedule: None (trigger-only)
Parallelism: All campaigns run simultaneously (max controlled by max_active_tasks)

Required Environment Variables:
- REPORT_IMAGE: Docker image for report generation (set by CI/CD)
- OUTPUT_PVC_NAME: PVC name for storing reports (default: hcm-reports-output)
- K8S_NAMESPACE: Kubernetes namespace (default: default)
"""

import os
import json
import logging
from datetime import datetime, timedelta, timezone

from airflow import DAG
from airflow.decorators import task

from airflow.providers.cncf.kubernetes.operators.kubernetes_pod import KubernetesPodOperator
from kubernetes.client import models as k8s_models

logger = logging.getLogger("airflow.task")
logger.setLevel(logging.INFO)

# Timezone: All calculations in UTC
UTC = timezone.utc

# -----------------------------------------
# ENVIRONMENT CONFIGURATION
# -----------------------------------------

# Docker image for report generation (updated by CI/CD pipeline)
REPORT_IMAGE = os.getenv("REPORT_IMAGE")
if not REPORT_IMAGE:
    raise ValueError("REPORT_IMAGE environment variable is required")

# PVC configuration for report output
OUTPUT_PVC_NAME = os.getenv("OUTPUT_PVC_NAME", "hcm-reports-output")
OUTPUT_MOUNT_PATH = "/app/REPORTS_GENERATION/FINAL_REPORTS"

# Kubernetes namespace where pods will be created
K8S_NAMESPACE = os.getenv("K8S_NAMESPACE", "airflow")

# Container resource constraints
# Requests: Minimum guaranteed resources
# Limits: Maximum allowed resources
CONTAINER_RESOURCES = k8s_models.V1ResourceRequirements(
    requests={
        "memory": "128Mi",   # Start with minimal memory
        "cpu": "100m"        # 0.1 CPU cores
    },
    limits={
        "memory": "2Gi",     # Max 2GB memory
        "cpu": "1000m"       # Max 1 CPU core
    }
)

# -------------------------------------------------
# HELPER FUNCTION: Compute Date Range by Frequency
# -------------------------------------------------

def compute_range(campaign, now):
    """
    Calculate report date range based on trigger frequency.

    Args:
        campaign (dict): Campaign object with triggerFrequency
        now (datetime): Current execution time (UTC)

    Returns:
        tuple: (start_datetime, end_datetime) in UTC

    Logic:
        - Daily: Yesterday (00:00:00 to 23:59:59)
        - Weekly: Last 7 days ending yesterday
        - Monthly: Last 30 days ending yesterday

    Examples:
        If today is 2025-01-18:
        - Daily: 2025-01-17 00:00:00 to 2025-01-17 23:59:59
        - Weekly: 2025-01-11 00:00:00 to 2025-01-17 23:59:59 (7 days)
        - Monthly: 2024-12-19 00:00:00 to 2025-01-17 23:59:59 (30 days)
    """
    freq = campaign.get("triggerFrequency", "Daily").lower()

    if freq == "weekly":
        # Report covers last 7 days (excluding today)
        # Start: 7 days ago at 00:00:00
        start = (now - timedelta(days=7)).replace(hour=0, minute=0, second=0, microsecond=0)
        # End: Yesterday at 23:59:59
        end = (now - timedelta(days=1)).replace(hour=23, minute=59, second=59, microsecond=999999)
        return (start, end)

    if freq == "monthly":
        # Report covers last 30 days (excluding today)
        # Start: 30 days ago at 00:00:00
        start = (now - timedelta(days=30)).replace(hour=0, minute=0, second=0, microsecond=0)
        # End: Yesterday at 23:59:59
        end = (now - timedelta(days=1)).replace(hour=23, minute=59, second=59, microsecond=999999)
        return (start, end)

    # Default: Daily report (yesterday only)
    # Start: Yesterday at 00:00:00
    start = (now - timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    # End: Yesterday at 23:59:59
    end = (now - timedelta(days=1)).replace(hour=23, minute=59, second=59, microsecond=999999)
    return (start, end)


# ============================
#       DAG DEFINITION
# ============================

default_args = {
    "owner": "hcm-reports-team",
    "depends_on_past": False,
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}

with DAG(
    dag_id="hcm_dynamic_campaigns",
    default_args=default_args,
    description="Campaign report processor - dynamically creates pods for matched campaigns",
    schedule=None,   # ✅ No schedule - triggered by hcm_campaign_scheduler only
    start_date=datetime(2025, 1, 1, tzinfo=UTC),
    catchup=False,
    tags=["hcm", "reports", "processor"],
    max_active_tasks=10,      # Max 10 pods running in parallel (adjust based on cluster capacity)
) as dag:

    # ================================================================
    # TASK 1: Build Payload
    # ================================================================
    @task(task_id="build_payload")
    def build_payload(**context):
        """
        Read matched campaigns from DAG run conf and build environment variables
        for each campaign's pod.

        Input:
            context["dag_run"].conf = {
                "matched_campaigns": [
                    {
                        "campaignNumber": "CMP-2025-01-15-001",
                        "reportName": "hhr_registered_report",
                        "triggerFrequency": "Daily",
                        "triggerTime": "09:00:00+0000",
                        "startDate": "15-01-2025 00:00:00+0000",
                        "endDate": "31-01-2025 00:00:00+0000",
                        "outputPvcName": "hcm-reports-output"
                    },
                    ...
                ]
            }

        Output:
            List of environment variable dictionaries, one per campaign:
            [
                {
                    "CAMPAIGN_NUMBER": "CMP-2025-01-15-001",
                    "REPORT_NAME": "hhr_registered_report",
                    "TRIGGER_FREQUENCY": "Daily",
                    "START_DATE": "2025-01-17 00:00:00+0000",
                    "END_DATE": "2025-01-17 23:59:59+0000",
                    "OUTPUT_PVC_NAME": "hcm-reports-output",
                    "OUTPUT_DIR": "/app/REPORTS_GENERATION/FINAL_REPORTS/CMP-001/hhr_registered_report/Daily",
                    "OUTPUT_FILE": "/app/REPORTS_GENERATION/FINAL_REPORTS/CMP-001/hhr_registered_report/Daily/hhr_registered_report_2025-01-18_09-00-00.xlsx",
                    "REPORT_SCRIPT": "/app/REPORTS_GENERATION/REPORTS/hhr_registered_report/hhr_registered_report.py"
                },
                ...
            ]

        Pod Execution Flow:
        1. Dockerfile ENTRYPOINT runs: python /app/main.py
        2. main.py reads environment variables (REPORT_NAME, CAMPAIGN_NUMBER, START_DATE, END_DATE)
        3. main.py calls report script with command-line arguments
        4. Report script queries Elasticsearch filtered by campaign and date range
        5. Generate Excel file and save to OUTPUT_FILE path on PVC
        """
        # Read matched campaigns from DAG run configuration
        matches = context["dag_run"].conf.get("matched_campaigns", [])

        if not matches:
            logger.warning("No matched campaigns in DAG run conf")
            return []

        logger.info("=" * 80)
        logger.info("BUILDING PAYLOAD FOR %d CAMPAIGNS", len(matches))
        logger.info("=" * 80)

        env_list = []
        now = datetime.now(UTC)

        for idx, c in enumerate(matches, 1):
            campaign_number = c.get("campaignNumber", "UNKNOWN")
            report_name = c.get("reportName", "UNKNOWN")
            frequency = c.get("triggerFrequency", "Daily")

            logger.info("Campaign %d/%d: %s", idx, len(matches), campaign_number)
            logger.info("  Report: %s", report_name)
            logger.info("  Frequency: %s", frequency)

            # Calculate report date range based on frequency
            start_dt, end_dt = compute_range(c, now)
            logger.info("  Date range: %s to %s",
                       start_dt.to_datetime_string(),
                       end_dt.to_datetime_string())

            # Build PVC folder structure
            # Format: /app/REPORTS_GENERATION/FINAL_REPORTS/<campaign>/<report>/<frequency>/
            # Example: /app/REPORTS_GENERATION/FINAL_REPORTS/CMP-001/hhr_registered_report/Daily/
            output_dir = f"{OUTPUT_MOUNT_PATH}/{campaign_number}/{report_name}/{frequency}"

            # Generate timestamped filename
            # Format: <report_name>_YYYY-MM-DD_HH-MM-SS.xlsx
            # Example: hhr_registered_report_2025-01-18_09-00-00.xlsx
            timestamp = now.to_datetime_string().replace(" ", "_").replace(":", "-")
            filename = f"{report_name}_{timestamp}.xlsx"
            output_file = f"{output_dir}/{filename}"

            logger.info("  Output: %s", output_file)

            # Build environment variables for this campaign's pod
            # These match the env vars expected by main.py
            env_dict = {
                # Campaign identification
                "CAMPAIGN_NUMBER": campaign_number,
                "REPORT_NAME": report_name,
                "TRIGGER_FREQUENCY": frequency,

                # Date range for report (format: YYYY-MM-DD HH:MM:SS+ZZZZ)
                # main.py expects START_DATE and END_DATE (not REPORT_START/REPORT_END)
                "START_DATE": start_dt.strftime("%Y-%m-%d %H:%M:%S%z"),
                "END_DATE": end_dt.strftime("%Y-%m-%d %H:%M:%S%z"),

                # Output configuration
                "OUTPUT_PVC_NAME": OUTPUT_PVC_NAME,
                "OUTPUT_DIR": output_dir,
                "OUTPUT_FILE": output_file,

                # Script location (main.py will execute this)
                # Report scripts are stored in /app/REPORTS_GENERATION/REPORTS/<report_name>/<report_name>.py
                "REPORT_SCRIPT": f"/app/REPORTS_GENERATION/REPORTS/{report_name}/{report_name}.py"
            }

            env_list.append(env_dict)

        logger.info("=" * 80)
        logger.info("PAYLOAD BUILT: %d environment configurations", len(env_list))
        logger.info("=" * 80)

        # Return list of env dicts (will be passed to .expand())
        return env_list


    # ================================================================
    # TASK 2: Campaign Pods (Dynamic Expansion)
    # ================================================================

    # Build environment variables for all campaigns
    envs = build_payload()

    # Dynamically create KubernetesPodOperator tasks (one per campaign)
    # All pods run in PARALLEL (up to max_active_tasks limit)
    #
    # .partial() sets common parameters for all pods
    # .expand(env_vars=envs) creates one pod task per env dict in the list
    #
    # If envs = [{...}, {...}, {...}], this creates 3 pod tasks:
    #   - campaign_pods[0] with env_vars = {...}
    #   - campaign_pods[1] with env_vars = {...}
    #   - campaign_pods[2] with env_vars = {...}
    campaign_pods = KubernetesPodOperator.partial(
        task_id="campaign_pods",

        # Kubernetes configuration
        namespace=K8S_NAMESPACE,
        image=REPORT_IMAGE,

        # No cmds/arguments needed - Dockerfile ENTRYPOINT handles execution
        # Dockerfile should contain: ENTRYPOINT ["python3", "/app/main.py"]

        # Resource constraints (requests and limits)
        container_resources=CONTAINER_RESOURCES,

        # Pod behavior
        get_logs=True,                    # Stream pod logs to Airflow task logs
        is_delete_operator_pod=True,      # Delete pod after completion (cleanup)
        in_cluster=True,                  # Running inside Kubernetes cluster
        startup_timeout_seconds=600,      # Wait up to 10 minutes for pod to start

        # Volume configuration: Mount PVC for report output
        volumes=[
            k8s_models.V1Volume(
                name="reports-output",
                persistent_volume_claim=k8s_models.V1PersistentVolumeClaimVolumeSource(
                    claim_name=OUTPUT_PVC_NAME
                )
            )
        ],
        volume_mounts=[
            k8s_models.V1VolumeMount(
                mount_path=OUTPUT_MOUNT_PATH,
                name="reports-output"
            )
        ],

        # Labels for pod identification in Kubernetes
        labels={
            "app": "hcm-reports",
            "managed-by": "airflow"
        }
    ).expand(
        # ✅ CRITICAL: .expand() creates dynamic tasks
        # Each env dict becomes a separate pod with those environment variables
        env_vars=envs
    )


    # ================================================================
    # TASK 3: Print Summary
    # ================================================================
    @task(task_id="print_summary")
    def print_summary(env_list):
        """
        Log final summary of processed campaigns.

        This task runs after all pods complete (successfully or failed).
        It provides a consolidated view of what was processed.

        Args:
            env_list: List of environment dicts from build_payload
        """
        if not env_list:
            logger.info("=" * 80)
            logger.info("NO CAMPAIGNS PROCESSED")
            logger.info("=" * 80)
            return {"status": "no_campaigns", "count": 0}

        logger.info("=" * 80)
        logger.info("CAMPAIGN PROCESSING SUMMARY")
        logger.info("=" * 80)
        logger.info("Total campaigns processed: %d", len(env_list))
        logger.info("")

        summary = []
        for idx, env in enumerate(env_list, 1):
            campaign_info = {
                "index": idx,
                "campaignNumber": env.get("CAMPAIGN_NUMBER"),
                "reportName": env.get("REPORT_NAME"),
                "frequency": env.get("TRIGGER_FREQUENCY"),
                "dateRange": f"{env.get('REPORT_START')} to {env.get('REPORT_END')}",
                "outputFile": env.get("OUTPUT_FILE")
            }
            summary.append(campaign_info)

            logger.info("Campaign %d: %s", idx, env.get("CAMPAIGN_NUMBER"))
            logger.info("  Report: %s", env.get("REPORT_NAME"))
            logger.info("  Frequency: %s", env.get("TRIGGER_FREQUENCY"))
            logger.info("  Date range: %s to %s",
                       env.get("REPORT_START"),
                       env.get("REPORT_END"))
            logger.info("  Output file: %s", env.get("OUTPUT_FILE"))
            logger.info("")

        logger.info("=" * 80)
        logger.info("All campaign reports have been generated and saved to PVC")
        logger.info("PVC: %s", OUTPUT_PVC_NAME)
        logger.info("=" * 80)

        return {
            "status": "completed",
            "count": len(env_list),
            "campaigns": summary
        }


    # ================================================================
    # DAG FLOW
    # ================================================================
    #
    # build_payload → campaign_pods (N pods in parallel) → print_summary
    #      ↓                ↓ ↓ ↓                              ↓
    #   [env1,         pod1 pod2 pod3                    log summary
    #    env2,          ↓    ↓    ↓
    #    env3]       report report report
    #
    # The campaign_pods task automatically waits for build_payload to complete
    # because it depends on the output (envs)
    #
    # print_summary receives the env_list and waits for all pods to complete
    envs >> campaign_pods >> print_summary(envs)
