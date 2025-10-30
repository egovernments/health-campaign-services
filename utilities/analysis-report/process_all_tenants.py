"""
Process all selected tenants - Extract and Analyze
"""

import os
import json
import logging
from extract_data import PunjabDataExtractor
from analyze_data import PunjabDataAnalyzer

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def main():
    """Process all tenants from environment variable"""

    # Get mode from environment variable (extract or analyze)
    mode = os.getenv('MODE', 'extract').lower()

    # Get tenant IDs from environment variable
    tenant_ids_json = os.getenv('TENANT_IDS', '["pb.adampur", "pb.samana", "pb.amloh"]')

    try:
        tenant_ids = json.loads(tenant_ids_json)
        if isinstance(tenant_ids, str):
            # If it's a single tenant as string, convert to list
            tenant_ids = [tenant_ids]
    except json.JSONDecodeError:
        logger.error(f"Invalid JSON for TENANT_IDS: {tenant_ids_json}")
        tenant_ids = ['pb.adampur', 'pb.samana', 'pb.amloh']

    mode_label = "EXTRACTING" if mode == 'extract' else "ANALYZING"
    logger.info("=" * 70)
    logger.info(f"🚀 {mode_label} DATA FOR {len(tenant_ids)} TENANTS")
    logger.info("=" * 70)
    logger.info(f"Mode: {mode.upper()}")
    logger.info(f"Tenants: {', '.join(tenant_ids)}")
    logger.info("=" * 70)

    processed_count = 0
    failed_count = 0

    for i, tenant_id in enumerate(tenant_ids, 1):
        logger.info("")
        logger.info(f"{'=' * 70}")
        logger.info(f"📋 TENANT {i}/{len(tenant_ids)}: {tenant_id.upper()}")
        logger.info(f"{'=' * 70}")

        try:
            # Override TENANT_ID environment variable
            os.environ['TENANT_ID'] = tenant_id

            if mode == 'extract':
                logger.info(f"📥 Extracting data for {tenant_id}...")
                extractor = PunjabDataExtractor()
                extractor.run_extraction()
                logger.info(f"✅ Extraction completed for {tenant_id}")

            elif mode == 'analyze':
                logger.info(f"📊 Analyzing data for {tenant_id}...")
                analyzer = PunjabDataAnalyzer()
                analyzer.run_analysis()
                logger.info(f"✅ Analysis completed for {tenant_id}")

            else:
                raise ValueError(f"Unknown mode: {mode}. Must be 'extract' or 'analyze'")

            logger.info(f"🎉 SUCCESS: {tenant_id} {mode} completed!")
            processed_count += 1

        except Exception as e:
            logger.error(f"❌ FAILED: Error {mode}ing {tenant_id}: {str(e)}")
            logger.exception(e)
            failed_count += 1
            continue

    # Final summary
    logger.info("")
    logger.info("=" * 70)
    logger.info(f"📊 {mode_label} SUMMARY")
    logger.info("=" * 70)
    logger.info(f"✅ Successfully processed: {processed_count}/{len(tenant_ids)} tenants")
    logger.info(f"❌ Failed: {failed_count}/{len(tenant_ids)} tenants")
    logger.info("=" * 70)

    if failed_count > 0:
        raise Exception(f"Failed to {mode} {failed_count} tenant(s)")

if __name__ == "__main__":
    main()
