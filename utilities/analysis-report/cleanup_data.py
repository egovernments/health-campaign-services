"""
Punjab Data Cleanup Module
Removes old extraction data to manage disk space
Supports both time-based and immediate cleanup modes
"""

import os
import shutil
import time
import logging
import json
from datetime import datetime, timedelta
from config_loader import ConfigLoader

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class PunjabDataCleanup:
    def __init__(self):
        """Initialize cleanup using ConfigLoader for consistent configuration"""
        # Load configuration from YAML file
        config_path = os.getenv('CONFIG_PATH', '/app/secrets/db_config.yaml')
        environment = os.getenv('ENVIRONMENT', 'development')

        self.config_loader = ConfigLoader(config_path=config_path, environment=environment)

        # Get cleanup configuration
        cleanup_config = self.config_loader.get_cleanup_config()
        data_dirs = self.config_loader.get_data_dirs()

        # Apply environment variable overrides if present
        self.data_dir = os.getenv('DATA_DIR', data_dirs['data_dir'])
        self.max_age_hours = int(os.getenv('MAX_AGE_HOURS', str(cleanup_config['max_age_hours'])))
        self.dry_run = os.getenv('DRY_RUN', str(cleanup_config['dry_run'])).lower() == 'true'

        # New: Cleanup mode configuration
        self.cleanup_mode = os.getenv('CLEANUP_MODE', 'time-based')  # 'immediate' or 'time-based'

        # New: Specific tenant IDs to clean (for immediate mode)
        tenant_ids_str = os.getenv('TENANT_IDS', '[]')
        try:
            self.tenant_ids = json.loads(tenant_ids_str)
        except json.JSONDecodeError:
            logger.warning(f"Failed to parse TENANT_IDS: {tenant_ids_str}, using empty list")
            self.tenant_ids = []

        logger.info(f"🧹 Cleanup initialized")
        logger.info(f"🌍 Environment: {environment}")
        logger.info(f"📁 Data directory: {self.data_dir}")
        logger.info(f"🔧 Cleanup mode: {self.cleanup_mode}")
        if self.cleanup_mode == 'immediate':
            logger.info(f"📋 Target tenants: {self.tenant_ids}")
        else:
            logger.info(f"⏰ Max age: {self.max_age_hours} hours")
        logger.info(f"🔍 Dry run mode: {self.dry_run}")

    def tenant_id_to_dir_name(self, tenant_id):
        """Convert tenant ID to directory name by stripping pb. prefix

        Args:
            tenant_id: Tenant ID like 'pb.amloh' or 'pb.adampur'

        Returns:
            Directory name like 'amloh' or 'adampur'
        """
        if tenant_id.startswith('pb.'):
            return tenant_id[3:]  # Remove 'pb.' prefix
        return tenant_id

    def is_directory_old(self, dir_path):
        """Check if directory is older than max_age_hours"""
        try:
            # Get directory modification time
            dir_mtime = os.path.getmtime(dir_path)
            dir_age = datetime.fromtimestamp(dir_mtime)

            # Calculate cutoff time
            cutoff_time = datetime.now() - timedelta(hours=self.max_age_hours)

            is_old = dir_age < cutoff_time

            logger.info(f"Directory: {dir_path}")
            logger.info(f"  Last modified: {dir_age}")
            logger.info(f"  Cutoff time: {cutoff_time}")
            logger.info(f"  Is old: {is_old}")

            return is_old

        except Exception as e:
            logger.error(f"Error checking directory age for {dir_path}: {e}")
            return False

    def get_directory_size(self, dir_path):
        """Get directory size in bytes"""
        total_size = 0
        try:
            for dirpath, dirnames, filenames in os.walk(dir_path):
                for filename in filenames:
                    file_path = os.path.join(dirpath, filename)
                    if os.path.exists(file_path):
                        total_size += os.path.getsize(file_path)
        except Exception as e:
            logger.error(f"Error calculating size for {dir_path}: {e}")

        return total_size

    def format_size(self, size_bytes):
        """Format bytes as human readable string"""
        for unit in ['B', 'KB', 'MB', 'GB']:
            if size_bytes < 1024.0:
                return f"{size_bytes:.1f} {unit}"
            size_bytes /= 1024.0
        return f"{size_bytes:.1f} TB"

    def cleanup_tenant_data(self, tenant_name, force_cleanup=False):
        """Clean up data for a specific tenant

        Args:
            tenant_name: Name of the tenant directory to clean
            force_cleanup: If True, cleanup regardless of age (immediate mode)
        """
        tenant_dir = os.path.join(self.data_dir, tenant_name)

        if not os.path.exists(tenant_dir):
            logger.info(f"Tenant directory does not exist: {tenant_dir}")
            return

        logger.info(f"Checking cleanup for tenant: {tenant_name}")

        # Determine if we should delete this directory
        should_delete = force_cleanup or self.is_directory_old(tenant_dir)

        if should_delete:
            dir_size = self.get_directory_size(tenant_dir)
            size_str = self.format_size(dir_size)

            if force_cleanup:
                logger.info(f"🎯 Immediate cleanup for {tenant_dir} (size: {size_str})")
            else:
                logger.info(f"⏰ Directory {tenant_dir} is old (size: {size_str})")

            if self.dry_run:
                logger.info(f"[DRY RUN] Would delete: {tenant_dir} ({size_str})")
            else:
                try:
                    shutil.rmtree(tenant_dir)
                    logger.info(f"✅ Deleted data: {tenant_dir} ({size_str} freed)")
                except Exception as e:
                    logger.error(f"❌ Failed to delete {tenant_dir}: {e}")
        else:
            logger.info(f"Directory {tenant_dir} is still fresh, keeping it")

    def run_cleanup(self):
        """Main cleanup process"""
        logger.info(f"🧹 Starting data cleanup process")
        logger.info(f"Target directory: {self.data_dir}")

        if not os.path.exists(self.data_dir):
            logger.warning(f"Data directory does not exist: {self.data_dir}")
            return

        # Get total size before cleanup
        total_size_before = self.get_directory_size(self.data_dir)
        logger.info(f"Total data size before cleanup: {self.format_size(total_size_before)}")

        # Determine cleanup strategy based on mode
        if self.cleanup_mode == 'immediate':
            # Immediate mode: Clean only specified tenants
            logger.info(f"🎯 Running immediate cleanup for specified tenants")
            if not self.tenant_ids:
                logger.warning("No tenant IDs specified for immediate cleanup")
                return

            logger.info(f"Cleaning {len(self.tenant_ids)} tenant(s): {self.tenant_ids}")

            for tenant_id in self.tenant_ids:
                try:
                    # Convert tenant ID to directory name (pb.amloh -> amloh)
                    dir_name = self.tenant_id_to_dir_name(tenant_id)
                    logger.info(f"Converting tenant ID '{tenant_id}' to directory name '{dir_name}'")

                    # Force cleanup regardless of age
                    self.cleanup_tenant_data(dir_name, force_cleanup=True)
                except Exception as e:
                    logger.error(f"Error cleaning up {tenant_id}: {e}")

        else:
            # Time-based mode: Clean all tenants older than max_age_hours
            logger.info(f"⏰ Running time-based cleanup (age > {self.max_age_hours} hours)")

            # Get all tenant directories
            tenant_dirs = [d for d in os.listdir(self.data_dir)
                          if os.path.isdir(os.path.join(self.data_dir, d))]

            logger.info(f"Found {len(tenant_dirs)} tenant directories: {tenant_dirs}")

            # Clean up each tenant based on age
            for tenant_name in tenant_dirs:
                try:
                    self.cleanup_tenant_data(tenant_name, force_cleanup=False)
                except Exception as e:
                    logger.error(f"Error cleaning up {tenant_name}: {e}")

        # Get total size after cleanup
        total_size_after = self.get_directory_size(self.data_dir)
        space_freed = total_size_before - total_size_after

        logger.info(f"Total data size after cleanup: {self.format_size(total_size_after)}")
        if space_freed > 0:
            logger.info(f"🎉 Space freed: {self.format_size(space_freed)}")

        logger.info("✅ Cleanup process completed")

if __name__ == "__main__":
    cleanup = PunjabDataCleanup()
    cleanup.run_cleanup()