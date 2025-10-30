"""
Filestore Uploader Module
Uploads analysis results to various filestore services
Supports: Google Cloud Storage, AWS S3, Azure Blob, MinIO, HTTP API
"""

import os
import logging
import mimetypes
from pathlib import Path
from datetime import datetime
from typing import Optional, List
import requests
from config_loader import ConfigLoader

logger = logging.getLogger(__name__)

class FilestoreUploader:
    """Generic filestore uploader supporting multiple backends"""

    def __init__(self):
        """Initialize uploader with configuration"""
        config_path = os.getenv('CONFIG_PATH', '/app/secrets/db_config.yaml')
        environment = os.getenv('ENVIRONMENT', 'development')

        self.config_loader = ConfigLoader(config_path=config_path, environment=environment)

        # Get filestore configuration
        self.filestore_type = os.getenv('FILESTORE_TYPE', 'local').lower()
        self.filestore_enabled = os.getenv('FILESTORE_ENABLED', 'false').lower() == 'true'

        logger.info(f"Filestore uploader initialized: type={self.filestore_type}, enabled={self.filestore_enabled}")

    def upload_file(self, local_file_path: str, remote_path: Optional[str] = None) -> dict:
        """
        Upload a file to filestore

        Args:
            local_file_path: Path to local file
            remote_path: Optional remote path (auto-generated if not provided)

        Returns:
            Dictionary with upload results (for http) or success status (for other types)
        """
        if not self.filestore_enabled:
            logger.info(f"Filestore disabled, skipping upload: {local_file_path}")
            return {'success': True, 'skipped': True, 'reason': 'filestore_disabled'}

        if not os.path.exists(local_file_path):
            logger.error(f"Local file not found: {local_file_path}")
            return {'success': False, 'error': 'file_not_found', 'local_file': local_file_path}

        # Generate remote path if not provided
        if not remote_path:
            filename = os.path.basename(local_file_path)
            tenant_name = os.getenv('TENANT_ID', 'unknown').split('.')[-1]
            date_str = datetime.now().strftime('%Y/%m/%d')
            remote_path = f"punjab-analysis/{tenant_name}/{date_str}/{filename}"

        logger.info(f"Uploading {local_file_path} to {remote_path}")

        try:
            if self.filestore_type == 'gcs':
                success = self._upload_to_gcs(local_file_path, remote_path)
                return {'success': success, 'type': 'gcs', 'local_file': local_file_path, 'remote_path': remote_path}
            elif self.filestore_type == 's3':
                success = self._upload_to_s3(local_file_path, remote_path)
                return {'success': success, 'type': 's3', 'local_file': local_file_path, 'remote_path': remote_path}
            elif self.filestore_type == 'azure':
                success = self._upload_to_azure(local_file_path, remote_path)
                return {'success': success, 'type': 'azure', 'local_file': local_file_path, 'remote_path': remote_path}
            elif self.filestore_type == 'minio':
                success = self._upload_to_minio(local_file_path, remote_path)
                return {'success': success, 'type': 'minio', 'local_file': local_file_path, 'remote_path': remote_path}
            elif self.filestore_type == 'http':
                result = self._upload_via_http(local_file_path, remote_path)
                result['type'] = 'http'
                return result
            else:
                logger.warning(f"Unknown filestore type: {self.filestore_type}")
                return {'success': False, 'error': 'unknown_filestore_type', 'type': self.filestore_type}

        except Exception as e:
            logger.error(f"Upload failed: {e}")
            return {'success': False, 'error': str(e), 'local_file': local_file_path}

    def _upload_to_gcs(self, local_file_path: str, remote_path: str) -> bool:
        """Upload to Google Cloud Storage"""
        try:
            from google.cloud import storage

            bucket_name = os.getenv('GCS_BUCKET_NAME')
            credentials_path = os.getenv('GOOGLE_APPLICATION_CREDENTIALS', '/app/secrets/gcs-credentials.json')

            if credentials_path and os.path.exists(credentials_path):
                client = storage.Client.from_service_account_json(credentials_path)
            else:
                client = storage.Client()

            bucket = client.bucket(bucket_name)
            blob = bucket.blob(remote_path)

            blob.upload_from_filename(local_file_path)

            logger.info(f"✅ Uploaded to GCS: gs://{bucket_name}/{remote_path}")
            return True

        except ImportError:
            logger.error("google-cloud-storage not installed. Run: pip install google-cloud-storage")
            return False
        except Exception as e:
            logger.error(f"GCS upload failed: {e}")
            return False

    def _upload_to_s3(self, local_file_path: str, remote_path: str) -> bool:
        """Upload to AWS S3"""
        try:
            import boto3

            bucket_name = os.getenv('S3_BUCKET_NAME')
            aws_access_key = os.getenv('AWS_ACCESS_KEY_ID')
            aws_secret_key = os.getenv('AWS_SECRET_ACCESS_KEY')
            region = os.getenv('AWS_REGION', 'us-east-1')

            s3_client = boto3.client(
                's3',
                aws_access_key_id=aws_access_key,
                aws_secret_access_key=aws_secret_key,
                region_name=region
            )

            s3_client.upload_file(local_file_path, bucket_name, remote_path)

            logger.info(f"✅ Uploaded to S3: s3://{bucket_name}/{remote_path}")
            return True

        except ImportError:
            logger.error("boto3 not installed. Run: pip install boto3")
            return False
        except Exception as e:
            logger.error(f"S3 upload failed: {e}")
            return False

    def _upload_to_azure(self, local_file_path: str, remote_path: str) -> bool:
        """Upload to Azure Blob Storage"""
        try:
            from azure.storage.blob import BlobServiceClient

            connection_string = os.getenv('AZURE_STORAGE_CONNECTION_STRING')
            container_name = os.getenv('AZURE_CONTAINER_NAME')

            blob_service_client = BlobServiceClient.from_connection_string(connection_string)
            blob_client = blob_service_client.get_blob_client(container=container_name, blob=remote_path)

            with open(local_file_path, 'rb') as data:
                blob_client.upload_blob(data, overwrite=True)

            logger.info(f"✅ Uploaded to Azure Blob: {container_name}/{remote_path}")
            return True

        except ImportError:
            logger.error("azure-storage-blob not installed. Run: pip install azure-storage-blob")
            return False
        except Exception as e:
            logger.error(f"Azure upload failed: {e}")
            return False

    def _upload_to_minio(self, local_file_path: str, remote_path: str) -> bool:
        """Upload to MinIO (S3-compatible)"""
        try:
            from minio import Minio

            endpoint = os.getenv('MINIO_ENDPOINT', 'localhost:9000')
            access_key = os.getenv('MINIO_ACCESS_KEY')
            secret_key = os.getenv('MINIO_SECRET_KEY')
            bucket_name = os.getenv('MINIO_BUCKET_NAME')
            secure = os.getenv('MINIO_SECURE', 'false').lower() == 'true'

            client = Minio(
                endpoint,
                access_key=access_key,
                secret_key=secret_key,
                secure=secure
            )

            # Ensure bucket exists
            if not client.bucket_exists(bucket_name):
                client.make_bucket(bucket_name)

            client.fput_object(bucket_name, remote_path, local_file_path)

            logger.info(f"✅ Uploaded to MinIO: {bucket_name}/{remote_path}")
            return True

        except ImportError:
            logger.error("minio not installed. Run: pip install minio")
            return False
        except Exception as e:
            logger.error(f"MinIO upload failed: {e}")
            return False

    def _upload_via_http(self, local_file_path: str, remote_path: str) -> dict:
        """
        Upload via Digit Filestore HTTP API

        Returns:
            Dictionary with upload result including fileStoreId
        """
        try:
            # Get Digit Filestore configuration
            upload_url = os.getenv('FILESTORE_URL', 'http://localhost:8089/filestore/v1/files')
            auth_token = os.getenv('FILESTORE_AUTH_TOKEN', '')
            tenant_id = os.getenv('FILESTORE_TENANT_ID', os.getenv('TENANT_ID', 'pb'))
            module = os.getenv('FILESTORE_MODULE', 'punjab-analysis')

            # Determine MIME type based on file extension to match Apache Tika expectations
            # Reference: allowed.formats.map from filestore service application.properties
            filename = os.path.basename(local_file_path)
            file_ext = filename.lower().split('.')[-1] if '.' in filename else ''

            # MIME type mapping matching filestore's Apache Tika expectations
            mime_type_map = {
                'csv': 'text/plain',                      # csv:{'text/plain'}
                'txt': 'text/plain',                      # txt:{'text/plain'}
                'xlsx': 'application/x-tika-ooxml',       # xlsx:{'application/x-tika-ooxml','application/x-tika-msoffice'}
                'xls': 'application/x-tika-msoffice',     # xls:{'application/x-tika-ooxml','application/x-tika-msoffice'}
                'pdf': 'application/pdf',                 # pdf:{'application/pdf'}
                'jpg': 'image/jpeg',                      # jpg:{'image/jpg','image/jpeg'}
                'jpeg': 'image/jpeg',                     # jpeg:{'image/jpeg','image/jpg'}
                'png': 'image/png',                       # png:{'image/png'}
                'docx': 'application/x-tika-ooxml',       # docx:{'application/x-tika-msoffice','application/x-tika-ooxml',...}
                'doc': 'application/x-tika-msoffice',     # doc:{'application/x-tika-msoffice','application/x-tika-ooxml',...}
                'odt': 'application/vnd.oasis.opendocument.text',       # odt:{'application/vnd.oasis.opendocument.text'}
                'ods': 'application/vnd.oasis.opendocument.spreadsheet', # ods:{'application/vnd.oasis.opendocument.spreadsheet'}
            }

            mime_type = mime_type_map.get(file_ext, 'application/octet-stream')

            # Prepare headers
            headers = {}
            if auth_token:
                headers['auth-token'] = auth_token

            # Prepare files with explicit MIME type matching Tika expectations
            with open(local_file_path, 'rb') as f:
                files = {
                    'file': (filename, f, mime_type)
                }
                data = {
                    'tenantId': tenant_id,
                    'module': module
                }

                logger.info(f"Uploading to Filestore: {upload_url}")
                logger.info(f"  File: {filename}")
                logger.info(f"  MIME Type: {mime_type}")
                logger.info(f"  Tenant: {tenant_id}, Module: {module}")

                response = requests.post(
                    upload_url,
                    files=files,
                    data=data,
                    headers=headers,
                    timeout=300
                )

                logger.info(f"  Response Status: {response.status_code}")
                logger.info(f"  Request Content-Type: {response.request.headers.get('Content-Type', 'not set')}")

                if response.status_code in [200, 201]:
                    result = response.json()
                    logger.info(f"✅ Uploaded to Filestore successfully")

                    # Extract fileStoreId from response
                    file_store_ids = []
                    if 'files' in result:
                        for file_info in result['files']:
                            if 'fileStoreId' in file_info:
                                file_store_ids.append(file_info['fileStoreId'])
                                logger.info(f"  FileStoreId: {file_info['fileStoreId']}")

                    return {
                        'success': True,
                        'fileStoreIds': file_store_ids,
                        'response': result,
                        'local_file': local_file_path,
                        'remote_path': remote_path
                    }
                else:
                    logger.error(f"Filestore upload failed: {response.status_code}")
                    logger.error(f"Response: {response.text}")
                    return {
                        'success': False,
                        'error': f"HTTP {response.status_code}: {response.text}",
                        'local_file': local_file_path
                    }

        except Exception as e:
            logger.error(f"Filestore upload failed: {e}")
            return {
                'success': False,
                'error': str(e),
                'local_file': local_file_path
            }

    def upload_directory(self, local_dir: str, remote_prefix: str = "") -> dict:
        """
        Upload all files in a directory

        Returns:
            Dictionary with upload results including fileStoreIds
        """
        results = {
            'total': 0,
            'success': 0,
            'failed': 0,
            'files': [],
            'fileStoreIds': []
        }

        if not os.path.exists(local_dir):
            logger.error(f"Directory not found: {local_dir}")
            return results

        for root, dirs, files in os.walk(local_dir):
            for file in files:
                local_file = os.path.join(root, file)
                relative_path = os.path.relpath(local_file, local_dir)
                remote_path = os.path.join(remote_prefix, relative_path).replace('\\', '/')

                results['total'] += 1

                upload_result = self.upload_file(local_file, remote_path)

                if upload_result.get('success'):
                    results['success'] += 1
                    file_info = {
                        'file': file,
                        'status': 'success',
                        'remote_path': remote_path,
                        'local_path': local_file
                    }

                    # Add fileStoreIds if available (for HTTP/Digit Filestore)
                    if 'fileStoreIds' in upload_result:
                        file_info['fileStoreIds'] = upload_result['fileStoreIds']
                        results['fileStoreIds'].extend(upload_result['fileStoreIds'])

                    results['files'].append(file_info)
                else:
                    results['failed'] += 1
                    results['files'].append({
                        'file': file,
                        'status': 'failed',
                        'remote_path': remote_path,
                        'local_path': local_file,
                        'error': upload_result.get('error', 'unknown_error')
                    })

        logger.info(f"Upload summary: {results['success']}/{results['total']} successful")
        if results['fileStoreIds']:
            logger.info(f"Generated {len(results['fileStoreIds'])} fileStoreIds")

        return results


if __name__ == "__main__":
    # Test uploader
    uploader = FilestoreUploader()

    # Example: Upload a file
    test_file = "/output/test.csv"
    if os.path.exists(test_file):
        uploader.upload_file(test_file)
    else:
        logger.info("No test file found")
