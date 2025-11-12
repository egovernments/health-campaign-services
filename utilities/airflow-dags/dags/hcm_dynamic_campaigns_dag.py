"""
HCM Dynamic Campaigns DAG - Simple Parallel Execution

Flow:
1. Read campaigns config file and return list of active campaigns
2. Use .expand() to spawn one KubernetesPodOperator per campaign
3. Each pod generates report for its campaign in parallel

Usage:
- Edit config: /opt/airflow/dags/repo/utilities/airflow-dags/k8s/campaigns-simple-configmap.yaml
- Trigger DAG: No parameters needed
- All enabled campaigns run in parallel automatically
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.decorators import task
from airflow.providers.cncf.kubernetes.operators.pod import KubernetesPodOperator
from airflow.models import Variable
from kubernetes.client import models as k8s_models
import logging
import yaml
import os

logger = logging.getLogger(__name__)

# Default arguments
default_args = {
    'owner': 'hcm-reports-team',
    'depends_on_past': False,
    'start_date': datetime(2025, 1, 1),
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

# DAG Definition
dag = DAG(
    'hcm_dynamic_campaigns',
    default_args=default_args,
    description='Dynamic parallel campaign execution using task mapping',
    schedule=None,
    catchup=False,
    tags=['hcm', 'reports', 'campaigns', 'parallel', 'dynamic'],
    max_active_runs=1,
    max_active_tasks=50,  # Max parallel tasks
)

# Configuration
try:
    DOCKER_IMAGE = Variable.get("hcm_reports_image", default_var=None)
except:
    DOCKER_IMAGE = None

if not DOCKER_IMAGE:
    DOCKER_IMAGE = os.getenv("HCM_REPORTS_DOCKER_IMAGE", "egovio/hcm-custom-reports:latest")

try:
    NAMESPACE = Variable.get("hcm_reports_namespace", default_var=None)
except:
    NAMESPACE = None

if not NAMESPACE:
    NAMESPACE = os.getenv("AIRFLOW__KUBERNETES__NAMESPACE", "airflow")


# Campaign Configuration - Edit this list to add/remove campaigns
CAMPAIGNS_CONFIG = [
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-18-006990",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-23-797063",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-23-952046",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": False,
    },
]


# Task 1: Get active campaigns
@task(dag=dag)
def list_active_campaigns():
    """
    Return list of active (enabled) campaigns.
    """
    # Filter only enabled campaigns
    active_campaigns = [c for c in CAMPAIGNS_CONFIG if c.get('enabled', False)]

    logger.info(f"Found {len(active_campaigns)} enabled campaign(s)")
    for c in active_campaigns:
        logger.info(f"  - {c['campaign_number']}")

    return active_campaigns


# Get the campaign list
campaigns = list_active_campaigns()


# Task 2: Create one KubernetesPodOperator per campaign
@task.bash(dag=dag)
def trigger_campaign_reports(campaign_list):
    """Dummy task - actual pods are created statically below."""
    return f"Triggering {len(campaign_list)} campaigns"


# Dynamically create campaign report tasks
campaign_report_tasks = []

for idx, campaign in enumerate(CAMPAIGNS_CONFIG):
    if not campaign.get('enabled', False):
        continue

    campaign_number = campaign['campaign_number']
    pod_name = f"campaign-{campaign_number.lower().replace('_', '-').replace('/', '-')}"

    report_task = KubernetesPodOperator(
        task_id=f"generate_report_{idx}_{campaign_number.replace('-', '_').replace('/', '_')}",
        name=pod_name,
        namespace=NAMESPACE,
        image=DOCKER_IMAGE,
        cmds=['python3'],
        arguments=['main.py'],
        env_vars={
            'PYTHONUNBUFFERED': '1',
            'CAMPAIGN_NUMBER': campaign_number,
            'REPORT_TYPE': campaign.get('report_type', 'hhr_registered_report'),
        },
        volumes=[
            k8s_models.V1Volume(
                name='reports-output',
                persistent_volume_claim=k8s_models.V1PersistentVolumeClaimVolumeSource(
                    claim_name=campaign.get('output_pvc_name', 'hcm-reports-output')
                ),
            ),
        ],
        volume_mounts=[
            k8s_models.V1VolumeMount(
                name='reports-output',
                mount_path='/app/REPORTS_GENERATION/FINAL_REPORTS',
                sub_path=f"campaign-{campaign_number}",
            ),
        ],
        container_resources=k8s_models.V1ResourceRequirements(
            requests={'memory': '1Gi', 'cpu': '500m'},
            limits={'memory': '2Gi', 'cpu': '1000m'},
        ),
        get_logs=True,
        is_delete_operator_pod=True,
        in_cluster=True,
        startup_timeout_seconds=600,
        dag=dag,
    )

    campaign_report_tasks.append(report_task)


# Task 3: Summary
@task(dag=dag)
def print_summary(campaign_list):
    """Print execution summary."""
    logger.info("=" * 80)
    logger.info("📊 EXECUTION SUMMARY")
    logger.info("=" * 80)
    logger.info(f"Total campaigns processed: {len(campaign_list)}")
    logger.info("")

    for idx, campaign in enumerate(campaign_list):
        logger.info(f"{idx + 1}. {campaign['campaign_number']}")
        logger.info(f"   Period: {campaign['start_date']} to {campaign['end_date']}")

    logger.info("=" * 80)
    logger.info("✅ All campaigns completed successfully!")
    logger.info("=" * 80)


summary = print_summary(campaigns)


# Dependencies - all campaign tasks run in parallel
campaigns >> campaign_report_tasks >> summary
