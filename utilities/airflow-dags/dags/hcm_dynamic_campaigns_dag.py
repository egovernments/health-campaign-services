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
import requests

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
    schedule="@daily",
    catchup=False,
    tags=['hcm', 'reports', 'campaigns', 'parallel', 'dynamic'],
    max_active_runs=1,
    max_active_tasks=10,  # Max parallel tasks
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
        "campaign_number": "CMP-2025-10-15-007116",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-19-175465",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-19-21725",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-19-346919",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-19-42121",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-19-491739",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-19-554383",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-23-514912",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-23-198506",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    },
    {
        "report_type": "hhr_registered_report",
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "campaign_number": "CMP-2025-09-23-498627",
        "output_pvc_name": "hcm-reports-output",
        "upload_to_drive": False,
        "drive_folder_id": "",
        "enabled": True,
    }
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


def call_api(url, method="GET", headers=None, params=None, data=None, json=None, timeout=30):
    """
    Generic function to make API calls using the requests library.

    Args:
        url (str): The API endpoint URL.
        method (str): HTTP method (GET, POST, PUT, DELETE, etc.).
        headers (dict): Optional HTTP headers.
        params (dict): Query parameters for GET requests.
        data (dict or str): Form data for POST/PUT requests.
        json (dict): JSON body for POST/PUT requests.
        timeout (int): Request timeout in seconds.

    Returns:
        dict: Response JSON if available, else raw text.
    Raises:
        requests.exceptions.RequestException: For network or HTTP errors.
    """

    try:
        response = requests.request(
            method=method.upper(),
            url=url,
            headers=headers,
            params=params,
            data=data,
            json=json,
            timeout=timeout
        )

        # Raise exception for HTTP error codes (4xx, 5xx)
        response.raise_for_status()

        # Try to parse JSON; fallback to text
        try:
            return response.json()
        except ValueError:
            return {"response_text": response.text}

    except requests.exceptions.RequestException as e:
        print(f":x: API request failed: {e}")
        return {"error": str(e)}


#Task 1(modified): Fetch campaign data from mdms
@task(dag=dag)
def fetch_campaigns_from_mdms():
    """
    Call mdms for fetching the campaign data
    Mdms data structure
    [
        {
            "campaignNumber" : "CMP-2025-09-18-006990",
            "campaignStartDate" : "23-09-2025 00:00:00+0000",
            "campaignEndDate" : "04-10-2025 00:00:00+0000",
            "reportName" : "hhr_registered_report",
            "triggerTime" : "16:00:00+0000",
            "triggerFrequency" : "Daily",
            "reportStartTime": "00:00:00+0000",
            "reportEndTime": "23:59:59+0000",
            "outputPvcName": "hcm-reports-output"
        }
    ]
    """

    MDMS_URL = os.getenv("MDMS_URL") 
    MDMS_MASTER_NAME = os.getenv("MDMS_MASTER_NAME", "campaign-report-config") 
    MDMS_MODULE_NAME = os.getenv("MDMS_MODULE_NAME", "airflow-configs")
    TENANT_ID = os.getenv("TENANT_ID", "dev")
    LIMIT = os.getenv("LIMIT", 100)

    if not MDMS_URL:
        raise ValueError("MDMS_URL environment variable is not set")

    body = {
        "RequestInfo": {
            "authToken": ""
        },
        "MdmsCriteria": {
            "tenantId": TENANT_ID,
            "schemaCode": f"{MDMS_MODULE_NAME}.{MDMS_MASTER_NAME}",
            "limit": LIMIT,
            "offset": 0,
            "isActive" : True
        }
    }

    result = call_api(
        MDMS_URL, 
        method="POST", 
        json=body,
        headers={"Content-Type": "application/json"}
    )

    if "error" in result:
        raise Exception(f"Error fetching data from MDMS: {result['error']}")

    mdms_res = result.get("mdms", [])


    if not mdms_res:
        raise Exception("MDMS response is empty")
    
    for campaign in mdms_res:
        logger.info(f"Active campaign found: {campaign.get("campaignNumber")}")

    
    logger.info(f"Total active campaigns: {len(mdms_res)}")
    
    return mdms_res

# Get the campaign list
campaigns = fetch_campaigns_from_mdms()


# Task 2: Create one KubernetesPodOperator per campaign
@task.bash(dag=dag)
def trigger_campaign_reports(campaign_list):
    """Dummy task - actual pods are created statically below."""
    return f"Triggering {len(campaign_list)} campaigns"


# Dynamically create campaign report tasks
campaign_report_tasks = []

for idx, campaign in enumerate(campaigns):
    if not campaign.get('enabled', False):
        continue

    campaign_number = campaign['campaignNumber']
    # Shorten pod name to avoid Kubernetes 63-char limit
    # Extract last part of campaign number (e.g., "006990" from "CMP-2025-09-18-006990")
    campaign_short = campaign_number.split('-')[-1]

    report_task = KubernetesPodOperator(
        task_id=f"campaign_{campaign_short}",
        name=f"campaign-{campaign_short}",
        namespace=NAMESPACE,
        image=DOCKER_IMAGE,
        cmds=['python3'],
        arguments=['main.py'],
        env_vars={
            'PYTHONUNBUFFERED': '1',
            'CAMPAIGN_NUMBER': campaign_number,
            'REPORT_TYPE': campaign.get('reportName', 'hhr_registered_report'),
        },
        volumes=[
            k8s_models.V1Volume(
                name='reports-output',
                persistent_volume_claim=k8s_models.V1PersistentVolumeClaimVolumeSource(
                    claim_name=campaign.get('outputPvcName', 'hcm-reports-output')
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
            requests={'memory': '512Mi', 'cpu': '250m'},
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
        logger.info(f"{idx + 1}. {campaign['campaignNumber']}")
        logger.info(f"   Period: {campaign['campaignStartDate']} to {campaign['campaignEndDate']}")

    logger.info("=" * 80)
    logger.info("✅ All campaigns completed successfully!")
    logger.info("=" * 80)


summary = print_summary(campaigns)


# Dependencies - all campaign tasks run in parallel
campaigns >> campaign_report_tasks >> summary
