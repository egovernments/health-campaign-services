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


# Task 1: Read config file and extract active campaigns
@task(dag=dag)
def get_active_campaigns():
    """
    Read campaigns config file and return list of active campaign objects.
    Config file location: /opt/airflow/config/campaigns_config.yaml
    (Mounted from ConfigMap: hcm-campaigns-parallel-config)

    Expected config structure:
    campaigns:
      - report_type: "hhr_registered_report"
        start_date: "2025-10-01 00:00:00+0000"
        end_date: "2025-11-05 00:00:00+0000"
        campaign_number: "CMP-2025-09-19-853729"
        output_pvc_name: "hcm-reports-output"
        upload_to_drive: false
        drive_folder_id: ""
        enabled: true
    """
    config_path = "/opt/airflow/config/campaigns_config.yaml"

    logger.info("=" * 80)
    logger.info("📖 STEP 1: Reading campaigns configuration")
    logger.info("=" * 80)
    logger.info(f"Config file: {config_path}")

    try:
        # Check if file exists
        if not os.path.exists(config_path):
            logger.warning(f"⚠️  Config file not found: {config_path}")
            logger.info("Returning empty campaign list")
            return []

        # Read YAML config
        with open(config_path, 'r') as f:
            config = yaml.safe_load(f)

        # Get campaigns list (simple array, no groups)
        all_campaigns = config.get('campaigns', [])

        if not all_campaigns:
            logger.warning("⚠️  No campaigns found in config")
            return []

        logger.info(f"Found {len(all_campaigns)} campaign(s) in config")

        # Filter only enabled campaigns
        active_campaigns = []

        for idx, campaign in enumerate(all_campaigns):
            # Check if enabled
            if not campaign.get('enabled', False):
                logger.info(f"⏭️  Skipping disabled campaign: {campaign.get('campaign_number', f'campaign_{idx}')}")
                continue

            # Validate required fields
            campaign_number = campaign.get('campaign_number')
            if not campaign_number:
                logger.warning(f"⚠️  Skipping campaign {idx}: missing campaign_number")
                continue

            # Add to active list
            active_campaigns.append(campaign)
            logger.info(f"  ✓ Added: {campaign_number}")

        logger.info("")
        logger.info("=" * 80)
        logger.info(f"📊 TOTAL ACTIVE CAMPAIGNS: {len(active_campaigns)}")
        logger.info("=" * 80)

        for idx, campaign in enumerate(active_campaigns):
            logger.info(f"{idx + 1}. {campaign['campaign_number']}")
            logger.info(f"   Report Type: {campaign.get('report_type', 'N/A')}")
            logger.info(f"   Date Range: {campaign.get('start_date')} to {campaign.get('end_date')}")
            logger.info(f"   Upload to Drive: {campaign.get('upload_to_drive', False)}")

        logger.info("=" * 80)

        return active_campaigns

    except Exception as e:
        logger.error(f"❌ Error reading config: {str(e)}")
        raise


# Task 2: Prepare pod config for each campaign
@task(dag=dag)
def prepare_pod_configs(campaigns: list):
    """
    Convert campaign list into pod configuration dicts.
    This task takes the output from get_active_campaigns and prepares
    the configuration for each KubernetesPodOperator instance.
    """
    pod_configs = []

    for campaign in campaigns:
        campaign_number = campaign['campaign_number']
        pod_name = f"campaign-{campaign_number.lower().replace('_', '-').replace('/', '-')}"

        config = {
            'name': pod_name,
            'env_vars': {
                'PYTHONUNBUFFERED': '1',
                'CAMPAIGN_NUMBER': campaign_number,
                'REPORT_TYPE': campaign.get('report_type', 'hhr_registered_report'),
            },
            'volumes': [
                k8s_models.V1Volume(
                    name='reports-output',
                    persistent_volume_claim=k8s_models.V1PersistentVolumeClaimVolumeSource(
                        claim_name=campaign.get('output_pvc_name', 'hcm-reports-output')
                    ),
                ),
            ],
            'volume_mounts': [
                k8s_models.V1VolumeMount(
                    name='reports-output',
                    mount_path='/app/REPORTS_GENERATION/FINAL_REPORTS',
                    sub_path=f"campaign-{campaign_number}",
                ),
            ],
        }
        pod_configs.append(config)
        logger.info(f"Prepared pod config for: {campaign_number}")

    return pod_configs


# Task 3: Generate report for one campaign (will be expanded)
generate_campaign_report = KubernetesPodOperator.partial(
    task_id='generate_campaign_report',
    namespace=NAMESPACE,
    image=DOCKER_IMAGE,
    cmds=['python3'],
    arguments=['main.py'],
    get_logs=True,
    is_delete_operator_pod=True,
    in_cluster=True,
    startup_timeout_seconds=600,
    container_resources=k8s_models.V1ResourceRequirements(
        requests={'memory': '1Gi', 'cpu': '500m'},
        limits={'memory': '2Gi', 'cpu': '1000m'},
    ),
    dag=dag,
).expand_kwargs(
    op_kwargs=prepare_pod_configs(get_active_campaigns())
)


# Task 4: Summary
@task(dag=dag)
def create_execution_summary(campaigns: list):
    """
    Create summary of parallel execution
    """
    total = len(campaigns) if campaigns else 0

    logger.info("")
    logger.info("=" * 80)
    logger.info("📊 EXECUTION SUMMARY")
    logger.info("=" * 80)
    logger.info(f"Total campaigns processed: {total}")
    logger.info("")

    if campaigns:
        logger.info("Campaigns:")
        for idx, campaign in enumerate(campaigns):
            logger.info(f"  {idx + 1}. {campaign['campaign_number']}")
            logger.info(f"     Report: {campaign['report_type']}")
            logger.info(f"     Period: {campaign['start_date']} to {campaign['end_date']}")
            logger.info(f"     Upload: {campaign['upload_to_drive']}")

    logger.info("=" * 80)
    logger.info("✅ All campaigns completed successfully!")
    logger.info("=" * 80)

    return {
        'total_campaigns': total,
        'timestamp': datetime.now().isoformat(),
        'campaigns': [c['campaign_number'] for c in campaigns] if campaigns else []
    }


# Define DAG flow
active_campaigns = get_active_campaigns()
pod_configs = prepare_pod_configs(active_campaigns)
# generate_campaign_report already has .expand_kwargs() defined above
summary = create_execution_summary(active_campaigns)

# Dependencies
active_campaigns >> pod_configs >> generate_campaign_report >> summary
