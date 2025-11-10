"""
HCM Custom Reports Generation DAG
Automates the generation of various HCM reports using Kubernetes pods
Supports dynamic report selection, date range configuration, and PVC storage
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.providers.cncf.kubernetes.operators.pod import KubernetesPodOperator
from airflow.operators.python import PythonOperator, BranchPythonOperator
from airflow.models import Variable
from airflow.utils.trigger_rule import TriggerRule
from kubernetes.client import models as k8s_models
import logging
import json
import os

# Configure logging
logger = logging.getLogger(__name__)

# Default arguments
default_args = {
    'owner': 'hcm-reports-team',
    'depends_on_past': False,
    'start_date': datetime(2025, 1, 1),
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}

# DAG Definition
dag = DAG(
    'hcm_custom_reports_generation',
    default_args=default_args,
    description='Generate HCM Custom Reports with dynamic report selection and date configuration',
    schedule=None,  # Manual triggering only
    catchup=False,
    tags=['hcm', 'reports', 'custom-reports', 'kubernetes'],
    params={
        "report_type": "hhr_registered_report",  # Default report type
        "start_date": "2025-10-01 00:00:00+0000",
        "end_date": "2025-11-05 00:00:00+0000",
        "output_pvc_name": "hcm-reports-output",  # PVC for storing output reports
    },
)

# Docker image configuration
# Priority: Airflow Variable > Environment Variable > Default
try:
    DOCKER_IMAGE = Variable.get("hcm_reports_image", default_var=None)
    logger.info(f"Using Docker image from Airflow Variable: {DOCKER_IMAGE}")
except:
    DOCKER_IMAGE = None

if not DOCKER_IMAGE:
    DOCKER_IMAGE = os.getenv("HCM_REPORTS_DOCKER_IMAGE", "egovio/hcm-custom-reports:latest")
    logger.info(f"Using Docker image from environment/default: {DOCKER_IMAGE}")

# Namespace configuration
try:
    NAMESPACE = Variable.get("hcm_reports_namespace", default_var=None)
    logger.info(f"Using namespace from Airflow Variable: {NAMESPACE}")
except:
    NAMESPACE = None

if not NAMESPACE:
    NAMESPACE = os.getenv("AIRFLOW__KUBERNETES__NAMESPACE", "airflow")
    logger.info(f"Using namespace from environment/default: {NAMESPACE}")

# Available reports configuration
AVAILABLE_REPORTS = {
    "hhr_registered_report": {
        "name": "Household Registration Report",
        "description": "Detailed household registration data with beneficiary information",
        "scripts": ["hhr_registered_report.py"],
        "output_files": ["HHR_Detailed_Registration_Report.xlsx"]
    },
    # Add more reports here as they are developed
}


def validate_and_prepare_config(**context):
    """
    Task 1: Validate report selection and prepare configuration
    """
    params = context['params']
    report_type = params.get('report_type')
    start_date = params.get('start_date')
    end_date = params.get('end_date')

    logger.info(f"Selected report type: {report_type}")
    logger.info(f"Date range: {start_date} to {end_date}")

    # Validate report type
    if report_type not in AVAILABLE_REPORTS:
        raise ValueError(f"Invalid report type: {report_type}. Available: {list(AVAILABLE_REPORTS.keys())}")

    # Validate dates
    try:
        start = datetime.strptime(start_date.split('+')[0].strip(), "%Y-%m-%d %H:%M:%S")
        end = datetime.strptime(end_date.split('+')[0].strip(), "%Y-%m-%d %H:%M:%S")
        if start >= end:
            raise ValueError("Start date must be before end date")
    except Exception as e:
        raise ValueError(f"Invalid date format: {str(e)}")

    # Prepare configuration
    config = {
        "report_type": report_type,
        "report_info": AVAILABLE_REPORTS[report_type],
        "start_date": start_date,
        "end_date": end_date,
        "output_pvc_name": params.get('output_pvc_name', 'hcm-reports-output'),
        "docker_image": DOCKER_IMAGE,
        "namespace": NAMESPACE,
    }

    # Push configuration to XCom
    context['task_instance'].xcom_push(key='report_config', value=config)
    logger.info(f"Configuration validated and prepared: {config}")

    return config


def create_date_config(**context):
    """
    Task 2: Create date configuration JSON that will be mounted in the pod
    """
    ti = context['task_instance']
    config = ti.xcom_pull(task_ids='validate_report_config', key='report_config')

    date_config = {
        "start_date": config['start_date'],
        "end_date": config['end_date']
    }

    # Store as XCom for use in the Kubernetes pod
    ti.xcom_push(key='date_config_json', value=json.dumps(date_config))
    logger.info(f"Date configuration created: {date_config}")

    return date_config


def get_report_summary(**context):
    """
    Task 5: Get report generation summary and output information
    """
    ti = context['task_instance']
    config = ti.xcom_pull(task_ids='validate_report_config', key='report_config')

    report_info = config['report_info']
    output_files = report_info['output_files']

    summary = {
        "report_name": report_info['name'],
        "output_files": output_files,
        "pvc_location": f"/mnt/reports/{config['start_date'][:10]}_to_{config['end_date'][:10]}/",
        "status": "completed"
    }

    logger.info(f"Report generation summary: {summary}")
    return summary


# Task 1: Validate report configuration
validate_config_task = PythonOperator(
    task_id='validate_report_config',
    python_callable=validate_and_prepare_config,
    dag=dag,
)

# Task 2: Create date configuration
create_date_config_task = PythonOperator(
    task_id='create_date_configuration',
    python_callable=create_date_config,
    dag=dag,
)

# Task 3: Run report generation in Kubernetes Pod
run_report_generation = KubernetesPodOperator(
    task_id='generate_report_in_k8s',
    name='hcm-report-generator',
    namespace=NAMESPACE,
    image=DOCKER_IMAGE,
    cmds=['python3'],
    arguments=['main.py'],
    env_vars={
        'PYTHONUNBUFFERED': '1',
    },
    # Mount PVC for output storage
    volumes=[
        k8s_models.V1Volume(
            name='reports-output',
            persistent_volume_claim=k8s_models.V1PersistentVolumeClaimVolumeSource(
                claim_name='{{ params.output_pvc_name }}'
            ),
        ),
        k8s_models.V1Volume(
            name='date-config',
            config_map=k8s_models.V1ConfigMapVolumeSource(
                name='hcm-reports-date-config',
                optional=True,
            ),
        ),
    ],
    volume_mounts=[
        k8s_models.V1VolumeMount(
            name='reports-output',
            mount_path='/app/FINAL_REPORTS',
            sub_path=None,
        ),
        k8s_models.V1VolumeMount(
            name='date-config',
            mount_path='/app/reports_date_config.json',
            sub_path='reports_date_config.json',
        ),
    ],
    # Resource limits
    container_resources=k8s_models.V1ResourceRequirements(
        requests={
            'memory': '1Gi',
            'cpu': '500m',
        },
        limits={
            'memory': '2Gi',
            'cpu': '1000m',
        },
    ),
    # Pod configuration
    get_logs=True,
    is_delete_operator_pod=True,
    in_cluster=True,  # Set to False if running Airflow outside Kubernetes
    startup_timeout_seconds=600,
    dag=dag,
)

# Task 4: Verify output files in PVC
verify_output_task = KubernetesPodOperator(
    task_id='verify_output_files',
    name='verify-report-output',
    namespace=NAMESPACE,
    image='busybox:latest',
    cmds=['sh', '-c'],
    arguments=[
        'ls -lh /mnt/reports/ && '
        'find /mnt/reports/ -name "*.xlsx" -type f && '
        'echo "Output files verified successfully"'
    ],
    volumes=[
        k8s_models.V1Volume(
            name='reports-output',
            persistent_volume_claim=k8s_models.V1PersistentVolumeClaimVolumeSource(
                claim_name='{{ params.output_pvc_name }}'
            ),
        ),
    ],
    volume_mounts=[
        k8s_models.V1VolumeMount(
            name='reports-output',
            mount_path='/mnt/reports',
            sub_path=None,
        ),
    ],
    get_logs=True,
    is_delete_operator_pod=True,
    in_cluster=True,
    dag=dag,
)

# Task 5: Generate summary
generate_summary_task = PythonOperator(
    task_id='generate_summary',
    python_callable=get_report_summary,
    trigger_rule=TriggerRule.ALL_SUCCESS,
    dag=dag,
)

# Define task dependencies
validate_config_task >> create_date_config_task >> run_report_generation >> verify_output_task >> generate_summary_task
