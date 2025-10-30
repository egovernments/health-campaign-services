"""
Punjab Analysis System - Main Orchestrator
Routes execution between extraction and analysis modes
"""

import os
import sys
import logging
from extract_data import PunjabDataExtractor
from analyze_data import PunjabDataAnalyzer

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def main():
    """Main entry point - routes based on MODE environment variable"""

    mode = os.getenv('MODE', '').lower()
    tenant_id = os.getenv('TENANT_ID', 'pb.adampur')

    logger.info("="*50)
    logger.info("PUNJAB ANALYSIS SYSTEM v2.0")
    logger.info("="*50)
    logger.info(f"Mode: {mode}")
    logger.info(f"Tenant: {tenant_id}")
    logger.info("="*50)

    if mode == 'extract':
        logger.info("🔄 Starting DATA EXTRACTION phase...")
        try:
            extractor = PunjabDataExtractor()
            extractor.run_extraction()
            logger.info("✅ Data extraction completed successfully")
        except Exception as e:
            logger.error(f"❌ Data extraction failed: {e}")
            sys.exit(1)

    elif mode == 'analyze':
        logger.info("📊 Starting DATA ANALYSIS phase...")
        try:
            analyzer = PunjabDataAnalyzer()
            analyzer.run_analysis()
            logger.info("✅ Data analysis completed successfully")
        except Exception as e:
            logger.error(f"❌ Data analysis failed: {e}")
            sys.exit(1)

    else:
        logger.error(f"❌ Invalid MODE: {mode}")
        logger.error("Valid modes: 'extract' or 'analyze'")
        logger.error("Example: MODE=extract python main.py")
        sys.exit(1)

if __name__ == "__main__":
    main()