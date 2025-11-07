#!/usr/bin/env python3
"""
Upload Analysis Results to Google Drive
Uploads all output files from the analysis to Google Drive
"""

import os
import sys
import json
import logging
from pathlib import Path
from datetime import datetime
from google_drive_uploader import GoogleDriveUploader

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def main():
    """Main function to upload output files to Google Drive"""
    logger.info("=" * 80)
    logger.info("🚀 Starting Google Drive Upload Process")
    logger.info("=" * 80)

    # Get configuration from environment variables
    output_dir = os.getenv('OUTPUT_DIR', '/output')
    tenant_ids_json = os.getenv('TENANT_IDS', '[]')
    execution_date = os.getenv('EXECUTION_DATE', datetime.now().strftime('%Y-%m-%d'))
    google_drive_enabled = os.getenv('GOOGLE_DRIVE_ENABLED', 'false').lower() == 'true'

    # Optional: Create a dated folder for organization
    create_dated_folder = os.getenv('GOOGLE_DRIVE_CREATE_DATED_FOLDER', 'true').lower() == 'true'

    logger.info(f"Configuration:")
    logger.info(f"  Output Directory: {output_dir}")
    logger.info(f"  Execution Date: {execution_date}")
    logger.info(f"  Google Drive Enabled: {google_drive_enabled}")
    logger.info(f"  Create Dated Folder: {create_dated_folder}")

    # Check if Google Drive is enabled
    if not google_drive_enabled:
        logger.warning("⚠️  Google Drive upload is DISABLED. Skipping upload.")
        logger.info("Set GOOGLE_DRIVE_ENABLED=true to enable Google Drive uploads")
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

    # Initialize Google Drive uploader
    try:
        uploader = GoogleDriveUploader()
    except Exception as e:
        logger.error(f"❌ Failed to initialize Google Drive uploader: {e}")
        logger.error("\nPlease ensure:")
        logger.error("1. Google Drive credentials JSON file exists at /app/secrets/google_drive_credentials.json")
        logger.error("2. GOOGLE_DRIVE_CREDENTIALS_PATH environment variable is set (if using custom path)")
        logger.error("3. Service Account has been created with Google Drive API enabled")
        return 1

    # Create dated folder if requested
    target_folder_id = None
    if create_dated_folder:
        folder_name = f"Punjab_Analysis_{execution_date}"
        logger.info(f"\n📁 Creating dated folder: {folder_name}")

        folder_result = uploader.create_folder(
            folder_name=folder_name,
            parent_folder_id=os.getenv('GOOGLE_DRIVE_FOLDER_ID')  # Parent folder if specified
        )

        if folder_result.get('success'):
            target_folder_id = folder_result['folder_id']
            logger.info(f"✅ Folder created: {folder_result['web_view_link']}")
        else:
            logger.warning(f"⚠️  Failed to create folder: {folder_result.get('error')}")
            logger.info("Continuing with upload to root/parent folder...")

    # Collect files to upload - only recent analysis output files
    import time
    current_time = time.time()
    max_age_seconds = int(os.getenv('UPLOAD_FILE_MAX_AGE_MINUTES', '10')) * 60

    # Expected filename patterns for current run
    expected_patterns = [
        'Punjab_Data_Analysis_',  # Analysis output files
    ]

    files_to_upload = []
    for root, dirs, files in os.walk(output_dir):
        for file in files:
            # Skip hidden files, system files, and result files
            if file.startswith('.') or file.endswith('.tmp') or file.endswith('_results.json'):
                continue

            # Only upload files that match expected patterns
            matches_pattern = any(pattern in file for pattern in expected_patterns)
            if not matches_pattern:
                logger.debug(f"Skipping file (doesn't match expected pattern): {file}")
                continue

            file_path = os.path.join(root, file)

            # Check file modification time
            file_mtime = os.path.getmtime(file_path)
            file_age_seconds = current_time - file_mtime

            if file_age_seconds > max_age_seconds:
                logger.debug(f"Skipping old file (age: {file_age_seconds/60:.1f} min): {file}")
                continue

            file_size = os.path.getsize(file_path)
            files_to_upload.append({
                'path': file_path,
                'filename': file,
                'relative_path': os.path.relpath(file_path, output_dir),
                'size': file_size,
                'age_minutes': file_age_seconds / 60
            })

    if not files_to_upload:
        logger.warning("⚠️  No files found to upload")
        logger.info(f"Checked directory: {output_dir}")
        logger.info(f"Looking for files matching patterns: {expected_patterns}")
        logger.info(f"Maximum file age: {max_age_seconds/60:.0f} minutes")
        return 0

    logger.info(f"\n📦 Found {len(files_to_upload)} files to upload:")
    total_size = sum(f['size'] for f in files_to_upload)
    for file_info in files_to_upload:
        size_mb = file_info['size'] / (1024 * 1024)
        age_str = f"{file_info['age_minutes']:.1f} min ago" if file_info['age_minutes'] < 60 else f"{file_info['age_minutes']/60:.1f} hrs ago"
        logger.info(f"  - {file_info['filename']} ({size_mb:.2f} MB, created {age_str})")
    logger.info(f"  Total size: {total_size / (1024 * 1024):.2f} MB")

    # Upload files
    logger.info("\n🔄 Starting upload process...")
    upload_results = {
        'total': len(files_to_upload),
        'success': 0,
        'failed': 0,
        'files': [],
        'folder_link': folder_result.get('web_view_link') if target_folder_id else None
    }

    for file_info in files_to_upload:
        logger.info(f"\n📤 Uploading: {file_info['filename']}")

        # Upload file
        result = uploader.upload_file(
            local_file_path=file_info['path'],
            remote_filename=file_info['filename'],
            folder_id=target_folder_id
        )

        if result.get('success'):
            upload_results['success'] += 1
            upload_results['files'].append({
                'file': file_info['filename'],
                'status': 'success',
                'file_id': result.get('file_id'),
                'web_view_link': result.get('web_view_link'),
                'web_content_link': result.get('web_content_link')
            })
            logger.info(f"  ✅ Success! View at: {result.get('web_view_link')}")
        else:
            upload_results['failed'] += 1
            upload_results['files'].append({
                'file': file_info['filename'],
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

    if upload_results['folder_link']:
        logger.info(f"\n📁 Folder Link: {upload_results['folder_link']}")

    if upload_results['success'] > 0:
        logger.info(f"\n📋 Uploaded Files:")
        for file_info in upload_results['files']:
            if file_info['status'] == 'success':
                logger.info(f"  ✅ {file_info['file']}")
                logger.info(f"     View: {file_info['web_view_link']}")
                logger.info(f"     Download: {file_info['web_content_link']}")

    # Save upload results to file
    results_file = os.path.join(output_dir, 'google_drive_upload_results.json')
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
        logger.info("\n✨ All files uploaded successfully to Google Drive!")
        return 0


if __name__ == "__main__":
    try:
        exit_code = main()
        sys.exit(exit_code)
    except Exception as e:
        logger.error(f"❌ Fatal error: {e}", exc_info=True)
        sys.exit(1)
