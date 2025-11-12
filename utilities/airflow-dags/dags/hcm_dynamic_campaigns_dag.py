"""
HCM Dynamic Campaigns DAG - Simple & Clean Parallel Execution

Flow:
1. Read campaigns config file
2. Extract list of active campaign IDs
3. Use .expand() to spawn one KubernetesPodOperator per campaign
4. Each pod generates report for its campaign in parallel

Usage:
- Edit config file: kubectl edit configmap hcm-campaigns-parallel-config -n airflow
- Trigger DAG: No parameters needed
- All active campaigns run in parallel automatically
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


# Read campaigns from ConfigMap at DAG parse time
def read_campaigns_config():
    """
    Read campaigns directly from ConfigMap YAML file.
    This runs once when DAG is parsed, not as a task.
    """
    config_path = "/opt/airflow/dags/repo/utilities/airflow-dags/k8s/campaigns-simple-configmap.yaml"

    try:
        with open(config_path, 'r') as f:
            configmap = yaml.safe_load(f)

        # Extract the campaigns_config.yaml content from ConfigMap data
        config_yaml = configmap.get('data', {}).get('campaigns_config.yaml', '')
        config = yaml.safe_load(config_yaml)

        all_campaigns = config.get('campaigns', [])

        # Filter only enabled campaigns
        active_campaigns = [c for c in all_campaigns if c.get('enabled', False)]

        logger.info(f"Found {len(active_campaigns)} enabled campaign(s)")
        for c in active_campaigns:
            logger.info(f"  - {c['campaign_number']}")

        return active_campaigns
    except Exception as e:
        logger.error(f"Error reading campaigns config: {e}")
        return []


# Get active campaigns at DAG parse time
ACTIVE_CAMPAIGNS = read_campaigns_config()


# Task: Generate report for one campaign (will be expanded for each active campaign)
def create_campaign_task(campaign):
    """Create a KubernetesPodOperator for one campaign."""
    campaign_number = campaign['campaign_number']
    pod_name = f"campaign-{campaign_number.lower().replace('_', '-').replace('/', '-')}"

    return KubernetesPodOperator(
        task_id=f"generate_report_{campaign_number.replace('-', '_').replace('/', '_')}",
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


# Create tasks for all active campaigns
campaign_tasks = [create_campaign_task(campaign) for campaign in ACTIVE_CAMPAIGNS]


# Summary task
@task(dag=dag)
def print_summary():
    """Print execution summary."""
    logger.info("=" * 80)
    logger.info("📊 EXECUTION SUMMARY")
    logger.info("=" * 80)
    logger.info(f"Total campaigns processed: {len(ACTIVE_CAMPAIGNS)}")
    logger.info("")

    for idx, campaign in enumerate(ACTIVE_CAMPAIGNS):
        logger.info(f"{idx + 1}. {campaign['campaign_number']}")
        logger.info(f"   Period: {campaign['start_date']} to {campaign['end_date']}")

    logger.info("=" * 80)
    logger.info("✅ All campaigns completed successfully!")
    logger.info("=" * 80)


summary = print_summary()


# Define dependencies - all campaign tasks run in parallel, then summary
if campaign_tasks:
    campaign_tasks >> summary
