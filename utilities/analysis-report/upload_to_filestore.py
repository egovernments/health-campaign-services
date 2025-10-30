#!/usr/bin/env python3
"""
Upload Analysis Results to Digit Filestore
Uploads all output files from the analysis to the Digit Filestore service
"""

import os
import sys
import json
import logging
from pathlib import Path
from datetime import datetime
from filestore_uploader import FilestoreUploader

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def main():
    """Main function to upload output files to Filestore"""
    logger.info("=" * 80)
    logger.info("🚀 Starting Filestore Upload Process")
    logger.info("=" * 80)

    # Get configuration from environment variables
    output_dir = os.getenv('OUTPUT_DIR', '/output')
    tenant_ids_json = os.getenv('TENANT_IDS', '[]')
    execution_date = os.getenv('EXECUTION_DATE', datetime.now().strftime('%Y-%m-%d'))
    filestore_enabled = os.getenv('FILESTORE_ENABLED', 'false').lower() == 'true'
    filestore_type = os.getenv('FILESTORE_TYPE', 'http')

    logger.info(f"Configuration:")
    logger.info(f"  Output Directory: {output_dir}")
    logger.info(f"  Execution Date: {execution_date}")
    logger.info(f"  Filestore Enabled: {filestore_enabled}")
    logger.info(f"  Filestore Type: {filestore_type}")

    # Check if filestore is enabled
    if not filestore_enabled:
        logger.warning("⚠️  Filestore upload is DISABLED. Skipping upload.")
        logger.info("Set FILESTORE_ENABLED=true to enable filestore uploads")
        return 0

    # Parse tenant IDs
    try:
        tenant_ids = json.loads(tenant_ids_json)
        if not isinstance(tenant_ids, list):
            tenant_ids = [tenant_ids]
    except json.JSONDecodeError:
        logger.warning(f"Failed to parse TENANT_IDS: {tenant_ids_json}")
        tenant_ids = []

    logger.info(f"  Tenant IDs: {tenant_ids}")

    # Check if output directory exists
    if not os.path.exists(output_dir):
        logger.error(f"❌ Output directory not found: {output_dir}")
        return 1

    # Initialize Filestore uploader
    try:
        uploader = FilestoreUploader()
    except Exception as e:
        logger.error(f"❌ Failed to initialize Filestore uploader: {e}")
        return 1

    # Collect files to upload - only files created/modified in the current run
    # Filter by modification time AND filename pattern to avoid uploading files from previous runs
    import time
    current_time = time.time()
    max_age_seconds = int(os.getenv('UPLOAD_FILE_MAX_AGE_MINUTES', '5')) * 60  # Default: 5 minutes (reduced from 10)

    # Expected filename patterns for current run (analysis outputs)
    # Format: Punjab_Data_Analysis_{tenant_name}_{timestamp}.xlsx
    expected_patterns = [
        'Punjab_Data_Analysis_',  # Analysis output files
    ]

    files_to_upload = []
    for root, dirs, files in os.walk(output_dir):
        for file in files:
            # Skip hidden files, system files, and result files
            if file.startswith('.') or file.endswith('.tmp') or file.endswith('_results.json'):
                continue

            # Only upload files that match expected patterns (current run outputs)
            matches_pattern = any(pattern in file for pattern in expected_patterns)
            if not matches_pattern:
                logger.debug(f"Skipping file (doesn't match expected pattern): {file}")
                continue

            file_path = os.path.join(root, file)

            # Check file modification time - only upload very recent files from this run
            file_mtime = os.path.getmtime(file_path)
            file_age_seconds = current_time - file_mtime

            if file_age_seconds > max_age_seconds:
                logger.debug(f"Skipping old file (age: {file_age_seconds/60:.1f} min): {file}")
                continue

            # Additional filter: check if filename contains execution date (if available)
            # This helps ensure we only upload files from the current execution

            file_size = os.path.getsize(file_path)
            files_to_upload.append({
                'path': file_path,
                'relative_path': os.path.relpath(file_path, output_dir),
                'size': file_size,
                'age_minutes': file_age_seconds / 60
            })

    if not files_to_upload:
        logger.warning("⚠️  No files found in output directory")
        return 0

    logger.info(f"\n📦 Found {len(files_to_upload)} files to upload (created in last {max_age_seconds/60:.0f} minutes):")
    total_size = sum(f['size'] for f in files_to_upload)
    for file_info in files_to_upload:
        size_mb = file_info['size'] / (1024 * 1024)
        age_str = f"{file_info['age_minutes']:.1f} min ago" if file_info['age_minutes'] < 60 else f"{file_info['age_minutes']/60:.1f} hrs ago"
        logger.info(f"  - {file_info['relative_path']} ({size_mb:.2f} MB, created {age_str})")
    logger.info(f"  Total size: {total_size / (1024 * 1024):.2f} MB")

    # Upload files
    logger.info("\n🔄 Starting upload process...")
    upload_results = {
        'total': len(files_to_upload),
        'success': 0,
        'failed': 0,
        'files': [],
        'fileStoreIds': []
    }

    for file_info in files_to_upload:
        logger.info(f"\n📤 Uploading: {file_info['relative_path']}")

        # Determine tenant ID from file path if possible
        # Expected path format: output/tenant_name/...
        path_parts = file_info['relative_path'].split(os.sep)
        if len(path_parts) > 1 and tenant_ids:
            # Try to match tenant from path
            for tenant_id in tenant_ids:
                tenant_name = tenant_id.split('.')[-1]
                if tenant_name in path_parts:
                    os.environ['TENANT_ID'] = tenant_id
                    os.environ['FILESTORE_TENANT_ID'] = tenant_id
                    break

        # Upload file
        result = uploader.upload_file(
            local_file_path=file_info['path'],
            remote_path=file_info['relative_path']
        )

        if result.get('success'):
            upload_results['success'] += 1
            upload_results['files'].append({
                'file': file_info['relative_path'],
                'status': 'success',
                'fileStoreIds': result.get('fileStoreIds', [])
            })

            if 'fileStoreIds' in result:
                upload_results['fileStoreIds'].extend(result['fileStoreIds'])
                logger.info(f"  ✅ Success! FileStoreIds: {result['fileStoreIds']}")
            else:
                logger.info(f"  ✅ Success!")
        else:
            upload_results['failed'] += 1
            upload_results['files'].append({
                'file': file_info['relative_path'],
                'status': 'failed',
                'error': result.get('error', 'unknown')
            })
            logger.error(f"  ❌ Failed: {result.get('error', 'unknown')}")

    # Print summary
    logger.info("\n" + "=" * 80)
    logger.info("📊 Upload Summary")
    logger.info("=" * 80)
    logger.info(f"Total files: {upload_results['total']}")
    logger.info(f"Successful: {upload_results['success']}")
    logger.info(f"Failed: {upload_results['failed']}")

    if upload_results['fileStoreIds']:
        logger.info(f"\n📋 Generated FileStore IDs ({len(upload_results['fileStoreIds'])} total):")
        for file_store_id in upload_results['fileStoreIds']:
            logger.info(f"  - {file_store_id}")

    # Save upload results to file
    results_file = os.path.join(output_dir, 'filestore_upload_results.json')
    try:
        with open(results_file, 'w') as f:
            json.dump(upload_results, f, indent=2)
        logger.info(f"\n💾 Upload results saved to: {results_file}")
    except Exception as e:
        logger.warning(f"⚠️  Failed to save upload results: {e}")

    # Exit with appropriate code
    if upload_results['failed'] > 0:
        logger.warning(f"\n⚠️  Upload completed with {upload_results['failed']} failures")
        return 1
    else:
        logger.info("\n✨ All files uploaded successfully!")
        return 0


if __name__ == "__main__":
    try:
        exit_code = main()
        sys.exit(exit_code)
    except Exception as e:
        logger.error(f"❌ Fatal error: {e}", exc_info=True)
        sys.exit(1)
