"""
Punjab Data Analysis Pipeline - Kubernetes Version
This version uses KubernetesPodOperator for scalable, distributed processing
Simplified to process selected tenants dynamically in single tasks
"""

from datetime import datetime, timedelta
from airflow import DAG
from airflow.providers.cncf.kubernetes.operators.pod import KubernetesPodOperator
from airflow.operators.python import PythonOperator
from airflow.models import Variable
from kubernetes.client import models as k8s
import logging
import os

# Configure logging
logger = logging.getLogger(__name__)

# Default arguments
default_args = {
    'owner': 'punjab-analytics-team',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 3,  # Increased to handle KubernetesExecutor false failures
    'retry_delay': timedelta(minutes=2),  # Reduced since retries are due to executor bug, not real failures
}

# DAG Definition
dag = DAG(
    'punjab_analysis_kubernetes_pipeline',
    default_args=default_args,
    description='Punjab property tax analysis using Kubernetes - Scalable and distributed processing',
    schedule=None,  # Manual triggering only (changed from schedule_interval for Airflow 3.0)
    catchup=False,
    max_active_tasks=10,
    tags=['punjab', 'kubernetes', 'property-tax', 'scalable'],
    params={
        "tenant_ids": ["pb.adampur", "pb.samana", "pb.amloh"],  # Default tenant list
    },
)

# Configuration
DEFAULT_TENANT_IDS = ['pb.adampur', 'pb.samana', 'pb.amloh']

# Docker image - can be overridden via Airflow Variable or environment variable
# Priority: Airflow Variable > Environment Variable > Default
try:
    DOCKER_IMAGE = Variable.get("punjab_analysis_image", default_var=None)
    logger.info(f"Using Docker image from Airflow Variable: {DOCKER_IMAGE}")
except:
    DOCKER_IMAGE = None

if not DOCKER_IMAGE:
    DOCKER_IMAGE = os.getenv("PUNJAB_DOCKER_IMAGE", "egovio/punjab-analysis:latest")
    logger.info(f"Using Docker image from environment/default: {DOCKER_IMAGE}")

# Namespace - can be overridden via Airflow Variable or environment variable
# Priority: Airflow Variable > Environment Variable > Default
try:
    NAMESPACE = Variable.get("punjab_analysis_namespace", default_var=None)
    logger.info(f"Using namespace from Airflow Variable: {NAMESPACE}")
except:
    NAMESPACE = None

if not NAMESPACE:
    NAMESPACE = os.getenv("AIRFLOW__KUBERNETES__NAMESPACE", "airflow")
    logger.info(f"Using namespace from environment/default: {NAMESPACE}")

# Kubernetes Resource Configuration
def get_container_resources(memory_request="512Mi", memory_limit="2Gi", cpu_request="500m", cpu_limit="2000m"):
    """Get standard resource configuration for pods"""
    return k8s.V1ResourceRequirements(
        requests={"memory": memory_request, "cpu": cpu_request},
        limits={"memory": memory_limit, "cpu": cpu_limit}
    )

# Volume Configuration
def get_volume_mounts():
    """Get standard volume mounts for Punjab analysis pods"""
    return [
        k8s.V1VolumeMount(
            name="data-storage",
            mount_path="/data",
        ),
        k8s.V1VolumeMount(
            name="output-storage",
            mount_path="/output",
        ),
        k8s.V1VolumeMount(
            name="secrets-storage",
            mount_path="/app/secrets",
            read_only=True,
        ),
    ]

def get_volumes():
    """Get standard volumes for Punjab analysis pods"""
    return [
        k8s.V1Volume(
            name="data-storage",
            persistent_volume_claim=k8s.V1PersistentVolumeClaimVolumeSource(
                claim_name="punjab-data-pvc"
            ),
        ),
        k8s.V1Volume(
            name="output-storage",
            persistent_volume_claim=k8s.V1PersistentVolumeClaimVolumeSource(
                claim_name="punjab-output-pvc"
            ),
        ),
        k8s.V1Volume(
            name="secrets-storage",
            persistent_volume_claim=k8s.V1PersistentVolumeClaimVolumeSource(
                claim_name="punjab-secrets-pvc"
            ),
        ),
    ]

def get_security_context():
    """Get security context for pods to ensure proper permissions on mounted volumes"""
    return k8s.V1PodSecurityContext(
        run_as_user=1000,  # punjab-user UID
        run_as_group=1000,  # punjab-user GID
        fs_group=1000,  # Ensures mounted volumes are writable by group 1000
    )

def get_tenant_ids(**context):
    """Get tenant IDs from DAG run configuration, params, or use defaults with validation"""
    dag_run = context.get('dag_run')
    tenant_ids = None

    # Try to get from DAG run conf first (command line triggers)
    if dag_run and dag_run.conf:
        # Support both 'tenant_ids' (list) and 'tenant_id' (single string)
        if 'tenant_ids' in dag_run.conf:
            tenant_ids = dag_run.conf.get('tenant_ids')
            logger.info(f"Using tenant IDs from DAG run configuration: {tenant_ids}")
        elif 'tenant_id' in dag_run.conf:
            tenant_ids = [dag_run.conf.get('tenant_id')]
            logger.info(f"Using single tenant ID from DAG run configuration: {tenant_ids}")

    # Try to get from DAG params (UI triggers)
    if tenant_ids is None and 'params' in context:
        if 'tenant_ids' in context['params']:
            tenant_ids = context['params']['tenant_ids']
            logger.info(f"Using tenant IDs from DAG params: {tenant_ids}")
        elif 'tenant_id' in context['params']:
            tenant_ids = [context['params']['tenant_id']]
            logger.info(f"Using single tenant ID from DAG params: {tenant_ids}")

    # Use defaults if nothing provided
    if tenant_ids is None:
        tenant_ids = DEFAULT_TENANT_IDS
        logger.info(f"No configuration provided, using default tenant IDs: {tenant_ids}")

    # Validate tenant IDs format
    if not isinstance(tenant_ids, list):
        raise ValueError(f"tenant_ids must be a list, got: {type(tenant_ids)}")

    # VALIDATION: Simple format validation only
    # Data existence will be checked during extraction phase
    logger.info("Validating tenant ID format...")

    for tenant_id in tenant_ids:
        # Check basic format
        if not tenant_id or not isinstance(tenant_id, str):
            raise ValueError(f"Invalid tenant ID: {tenant_id}. Must be a non-empty string.")

        # Must start with 'pb.'
        if not tenant_id.startswith('pb.'):
            raise ValueError(
                f"Invalid tenant format: '{tenant_id}'. Must start with 'pb.'\n"
                f"Example: pb.adampur, pb.samana, pb.amloh"
            )

        # Must have format pb.{name} (exactly 2 parts)
        parts = tenant_id.split('.')
        if len(parts) != 2 or not parts[1]:
            raise ValueError(
                f"Invalid tenant format: '{tenant_id}'. Expected format: 'pb.{{tenant_name}}'\n"
                f"Example: pb.adampur"
            )

    logger.info(f"✅ Tenant format validation passed for: {tenant_ids}")
    logger.info("ℹ️  Data existence will be checked during extraction phase")

    # Store tenant IDs in XCom for other tasks to use
    context['ti'].xcom_push(key='tenant_ids', value=tenant_ids)

    logger.info("=" * 60)
    logger.info(f"📋 Selected tenants for processing: {', '.join(tenant_ids)}")
    logger.info("=" * 60)

    return tenant_ids

# Start task - gets and validates tenant configuration
start_task = PythonOperator(
    task_id='start_pipeline',
    python_callable=get_tenant_ids,
    dag=dag,
)

# Data Extraction Task - processes all selected tenants
extract_task = KubernetesPodOperator(
    task_id='extract_data',
    name='extract-all-tenants',
    namespace=NAMESPACE,
    image=DOCKER_IMAGE,
    cmds=["python", "process_all_tenants.py"],
    arguments=[],

    # Environment variables for extraction
    # Using list format to support both simple values and ConfigMap/Secret references
    env_vars=[
        # Simple string values
        k8s.V1EnvVar(name='MODE', value='extract'),
        k8s.V1EnvVar(name='TENANT_IDS', value='{{ ti.xcom_pull(task_ids="start_pipeline", key="tenant_ids") | tojson }}'),
        k8s.V1EnvVar(name='OUTPUT_DIR', value='/data'),
        k8s.V1EnvVar(name='EXECUTION_DATE', value="{{ dag_run.logical_date.strftime('%Y-%m-%d') if dag_run.logical_date else dag_run.start_date.strftime('%Y-%m-%d') }}"),
        k8s.V1EnvVar(name='ENVIRONMENT', value='production'),
        k8s.V1EnvVar(name='DEBUG_MODE', value='false'),
        k8s.V1EnvVar(name='DB_PORT', value='5432'),
        # Database configuration from ConfigMap (egov-config)
        k8s.V1EnvVar(
            name='DB_HOST',
            value_from=k8s.V1EnvVarSource(
                config_map_key_ref=k8s.V1ConfigMapKeySelector(
                    name='egov-config',
                    key='db-host'
                )
            )
        ),
        k8s.V1EnvVar(
            name='DB_NAME',
            value_from=k8s.V1EnvVarSource(
                config_map_key_ref=k8s.V1ConfigMapKeySelector(
                    name='egov-config',
                    key='db-name'
                )
            )
        ),
        # Database credentials from existing 'db' Secret (created by cluster-configs)
        k8s.V1EnvVar(
            name='DB_USER',
            value_from=k8s.V1EnvVarSource(
                secret_key_ref=k8s.V1SecretKeySelector(
                    name='db',
                    key='username'
                )
            )
        ),
        k8s.V1EnvVar(
            name='DB_PASSWORD',
            value_from=k8s.V1EnvVarSource(
                secret_key_ref=k8s.V1SecretKeySelector(
                    name='db',
                    key='password'
                )
            )
        ),
    ],

    # Resource configuration for extraction (lighter workload)
    container_resources=get_container_resources(
        memory_request="512Mi",
        memory_limit="2Gi",
        cpu_request="500m",
        cpu_limit="2000m"
    ),

    # Volume mounts
    volume_mounts=get_volume_mounts(),
    volumes=get_volumes(),

    # Security context to ensure proper permissions on mounted volumes
    security_context=get_security_context(),

    # Kubernetes configuration
    service_account_name="airflow-worker",
    is_delete_operator_pod=False,  # Keep pods for debugging - allows manual log inspection
    get_logs=True,  # Enable log fetching for visibility in Airflow UI
    log_events_on_failure=True,  # Enable log events for debugging
    image_pull_policy="IfNotPresent",  # Use cached image if available
    startup_timeout_seconds=600,  # 10 minutes to start
    # Note: KubernetesExecutor bug may cause retries even on success
    # Logs will be available for inspection in Airflow UI

    # Connection settings
    kubernetes_conn_id="kubernetes_default",
    in_cluster=True,
    config_file=None,

    dag=dag,
)

# Data Analysis Task - processes all selected tenants
analyze_task = KubernetesPodOperator(
    task_id='analyze_data',
    name='analyze-all-tenants',
    namespace=NAMESPACE,
    image=DOCKER_IMAGE,
    cmds=["python", "process_all_tenants.py"],
    arguments=[],

    # Environment variables for analysis
    env_vars=[
        k8s.V1EnvVar(name='MODE', value='analyze'),
        k8s.V1EnvVar(name='TENANT_IDS', value='{{ ti.xcom_pull(task_ids="start_pipeline", key="tenant_ids") | tojson }}'),
        k8s.V1EnvVar(name='DATA_DIR', value='/data'),
        k8s.V1EnvVar(name='OUTPUT_DIR', value='/output'),
        k8s.V1EnvVar(name='EXECUTION_DATE', value="{{ dag_run.logical_date.strftime('%Y-%m-%d') if dag_run.logical_date else dag_run.start_date.strftime('%Y-%m-%d') }}"),
        k8s.V1EnvVar(name='ENVIRONMENT', value='production'),
        k8s.V1EnvVar(name='DEBUG_MODE', value='false'),
        k8s.V1EnvVar(name='DB_PORT', value='5432'),
        # Database configuration from ConfigMap
        k8s.V1EnvVar(
            name='DB_HOST',
            value_from=k8s.V1EnvVarSource(
                config_map_key_ref=k8s.V1ConfigMapKeySelector(name='egov-config', key='db-host')
            )
        ),
        k8s.V1EnvVar(
            name='DB_NAME',
            value_from=k8s.V1EnvVarSource(
                config_map_key_ref=k8s.V1ConfigMapKeySelector(name='egov-config', key='db-name')
            )
        ),
        # Database credentials from existing 'db' Secret (created by cluster-configs)
        k8s.V1EnvVar(
            name='DB_USER',
            value_from=k8s.V1EnvVarSource(
                secret_key_ref=k8s.V1SecretKeySelector(name='db', key='username')
            )
        ),
        k8s.V1EnvVar(
            name='DB_PASSWORD',
            value_from=k8s.V1EnvVarSource(
                secret_key_ref=k8s.V1SecretKeySelector(name='db', key='password')
            )
        ),
    ],

    # Resource configuration for analysis (heavier workload)
    container_resources=get_container_resources(
        memory_request="1Gi",
        memory_limit="4Gi",
        cpu_request="1000m",
        cpu_limit="4000m"
    ),

    # Volume mounts
    volume_mounts=get_volume_mounts(),
    volumes=get_volumes(),

    # Security context to ensure proper permissions on mounted volumes
    security_context=get_security_context(),

    # Kubernetes configuration
    service_account_name="airflow-worker",
    is_delete_operator_pod=False,  # Keep pods for debugging - allows manual log inspection
    get_logs=True,  # Enable log fetching for visibility in Airflow UI
    log_events_on_failure=True,  # Enable log events for debugging
    image_pull_policy="IfNotPresent",  # Use cached image if available
    startup_timeout_seconds=600,  # 10 minutes to start
    # Note: KubernetesExecutor bug may cause retries even on success
    # Logs will be available for inspection in Airflow UI

    # Connection settings
    kubernetes_conn_id="kubernetes_default",
    in_cluster=True,
    config_file=None,

    dag=dag,
)

# Upload to Filestore Task - uploads analysis results
upload_task = KubernetesPodOperator(
    task_id='upload_to_filestore',
    name='upload-to-filestore',
    namespace=NAMESPACE,
    image=DOCKER_IMAGE,
    cmds=["python", "upload_to_filestore.py"],
    arguments=[],

    # Environment variables for filestore upload
    env_vars=[
        k8s.V1EnvVar(name='OUTPUT_DIR', value='/output'),
        k8s.V1EnvVar(name='TENANT_IDS', value='{{ ti.xcom_pull(task_ids="start_pipeline", key="tenant_ids") | tojson }}'),
        k8s.V1EnvVar(name='EXECUTION_DATE', value="{{ dag_run.logical_date.strftime('%Y-%m-%d') if dag_run.logical_date else dag_run.start_date.strftime('%Y-%m-%d') }}"),
        k8s.V1EnvVar(name='ENVIRONMENT', value='production'),

        # Filestore configuration
        k8s.V1EnvVar(name='FILESTORE_ENABLED', value='true'),
        k8s.V1EnvVar(name='FILESTORE_TYPE', value='http'),
        # Filestore URL from egov-service-host ConfigMap
        k8s.V1EnvVar(
            name='FILESTORE_URL',
            value_from=k8s.V1EnvVarSource(
                config_map_key_ref=k8s.V1ConfigMapKeySelector(
                    name='egov-service-host',
                    key='egov-filestore'
                )
            )
        ),
        k8s.V1EnvVar(name='FILESTORE_AUTH_TOKEN', value=''),
        k8s.V1EnvVar(name='FILESTORE_TENANT_ID', value='pb'),
        k8s.V1EnvVar(name='FILESTORE_MODULE', value='punjab-analysis'),
    ],

    # Resource configuration for upload (light workload)
    container_resources=get_container_resources(
        memory_request="512Mi",
        memory_limit="2Gi",
        cpu_request="500m",
        cpu_limit="2000m"
    ),

    # Volume mounts - need output and secrets
    volume_mounts=[
        k8s.V1VolumeMount(
            name="output-storage",
            mount_path="/output",
            read_only=True,  # Read-only since we're only uploading
        ),
        k8s.V1VolumeMount(
            name="secrets-storage",
            mount_path="/app/secrets",
            read_only=True,
        ),
    ],
    volumes=[
        k8s.V1Volume(
            name="output-storage",
            persistent_volume_claim=k8s.V1PersistentVolumeClaimVolumeSource(
                claim_name="punjab-output-pvc"
            ),
        ),
        k8s.V1Volume(
            name="secrets-storage",
            persistent_volume_claim=k8s.V1PersistentVolumeClaimVolumeSource(
                claim_name="punjab-secrets-pvc"
            ),
        ),
    ],

    # Security context to ensure proper permissions on mounted volumes
    security_context=get_security_context(),

    # Kubernetes configuration
    service_account_name="airflow-worker",
    is_delete_operator_pod=False,  # Keep pods for debugging - allows manual log inspection
    get_logs=True,  # Enable log fetching for visibility in Airflow UI
    log_events_on_failure=True,  # Enable log events for debugging
    image_pull_policy="IfNotPresent",  # Use cached image if available
    startup_timeout_seconds=600,  # 10 minutes to start
    # Note: KubernetesExecutor bug may cause retries even on success
    # Logs will be available for inspection in Airflow UI

    # Connection settings
    kubernetes_conn_id="kubernetes_default",
    in_cluster=True,
    config_file=None,

    dag=dag,
)

# Completion task
complete_task = PythonOperator(
    task_id='pipeline_complete',
    python_callable=lambda **context: logger.info(
        f"🎉 Punjab Analysis Kubernetes Pipeline completed! "
        f"Execution date: {context['ds']}, Run ID: {context['run_id']}"
    ),
    dag=dag,
)

# Cleanup task using KubernetesPodOperator
cleanup_task = KubernetesPodOperator(
    task_id='cleanup_processed_data',
    name='cleanup-data',
    namespace=NAMESPACE,
    image=DOCKER_IMAGE,
    cmds=["python", "cleanup_data.py"],
    arguments=[],

    # Environment variables for cleanup
    env_vars=[
        k8s.V1EnvVar(name='DATA_DIR', value='/data'),
        k8s.V1EnvVar(name='TENANT_IDS', value='{{ ti.xcom_pull(task_ids="start_pipeline", key="tenant_ids") | tojson }}'),
        k8s.V1EnvVar(name='CLEANUP_MODE', value='immediate'),  # Clean current run data
        k8s.V1EnvVar(name='MAX_AGE_HOURS', value='0'),  # Clean immediately, not after 24 hours
        k8s.V1EnvVar(name='DRY_RUN', value='false'),
        k8s.V1EnvVar(name='ENVIRONMENT', value='production'),
        k8s.V1EnvVar(name='EXECUTION_DATE', value="{{ dag_run.logical_date.strftime('%Y-%m-%d') if dag_run.logical_date else dag_run.start_date.strftime('%Y-%m-%d') }}"),
    ],

    # Resource configuration for cleanup (light workload)
    container_resources=get_container_resources(
        memory_request="256Mi",
        memory_limit="512Mi",
        cpu_request="100m",
        cpu_limit="500m"
    ),

    # Volume mounts (only need data and secrets)
    volume_mounts=[
        k8s.V1VolumeMount(
            name="data-storage",
            mount_path="/data",
        ),
        k8s.V1VolumeMount(
            name="secrets-storage",
            mount_path="/app/secrets",
            read_only=True,
        ),
    ],
    volumes=[
        k8s.V1Volume(
            name="data-storage",
            persistent_volume_claim=k8s.V1PersistentVolumeClaimVolumeSource(
                claim_name="punjab-data-pvc"
            ),
        ),
        k8s.V1Volume(
            name="secrets-storage",
            persistent_volume_claim=k8s.V1PersistentVolumeClaimVolumeSource(
                claim_name="punjab-secrets-pvc"
            ),
        ),
    ],

    # Kubernetes configuration
    service_account_name="airflow-worker",
    is_delete_operator_pod=False,  # Keep pods for debugging - allows manual log inspection
    get_logs=True,  # Enable log fetching for visibility in Airflow UI
    log_events_on_failure=True,  # Enable log events for debugging
    image_pull_policy="IfNotPresent",  # Use cached image if available
    startup_timeout_seconds=600,  # 10 minutes to start
    # Note: KubernetesExecutor bug may cause retries even on success
    # Logs will be available for inspection in Airflow UI

    # Connection settings
    kubernetes_conn_id="kubernetes_default",
    in_cluster=True,
    config_file=None,

    dag=dag,
)

# Set up linear dependency chain
# Analysis outputs are uploaded to Filestore, then pipeline completes, then cleanup
start_task >> extract_task >> analyze_task >> upload_task >> complete_task >> cleanup_task
