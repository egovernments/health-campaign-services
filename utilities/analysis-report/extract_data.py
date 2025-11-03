"""
Punjab Data Extraction Module
Extracts property tax data from PostgreSQL database to CSV files
"""

import os
import pandas as pd
import psycopg2
from tqdm import tqdm
import logging
import time
import socket
from config_loader import ConfigLoader

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def format_size(size_bytes):
    """Format file size in human-readable format"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.2f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.2f} TB"

def get_file_size(file_path):
    """Get file size and return formatted string"""
    try:
        size = os.path.getsize(file_path)
        return size, format_size(size)
    except Exception as e:
        logger.warning(f"Could not get size for {file_path}: {e}")
        return 0, "unknown"

def get_directory_size(directory):
    """Get total size of all files in directory"""
    total_size = 0
    for dirpath, dirnames, filenames in os.walk(directory):
        for filename in filenames:
            filepath = os.path.join(dirpath, filename)
            try:
                total_size += os.path.getsize(filepath)
            except Exception as e:
                logger.warning(f"Could not get size for {filepath}: {e}")
    return total_size, format_size(total_size)

class PunjabDataExtractor:
    def __init__(self):
        """Initialize extractor using ConfigLoader for secure credential management"""
        # Load configuration from YAML file
        config_path = os.getenv('CONFIG_PATH', '/app/secrets/db_config.yaml')
        environment = os.getenv('ENVIRONMENT', 'development')

        self.config_loader = ConfigLoader(config_path=config_path, environment=environment)
        self.config_loader.validate_config()

        # Get database configuration (no hardcoded credentials!)
        self.db_config = self.config_loader.get_database_config()

        # Get application settings
        self.tenant_id = os.getenv('TENANT_ID', 'pb.adampur')
        data_dirs = self.config_loader.get_data_dirs()
        self.output_dir = os.getenv('OUTPUT_DIR', data_dirs['data_dir'])

        # Get tenant-specific or global settings
        self.chunk_size = self.config_loader.get_setting('chunk_size', default=50000, tenant_id=self.tenant_id)
        self.createdtime_limit = int(os.getenv('CREATEDTIME_LIMIT', '1757269799000'))

        logger.info(f"🔧 Initialized extractor for tenant: {self.tenant_id}")
        logger.info(f"🌍 Environment: {environment}")
        logger.info(f"📁 Output directory: {self.output_dir}")
        logger.info(f"📊 Chunk size: {self.chunk_size}")
        logger.info(f"🔐 Database: {self.db_config['user']}@{self.db_config['host']}:{self.db_config['port']}/{self.db_config['database']}")

        # Lock file path for this tenant
        self.lock_file = os.path.join(self.output_dir, f".lock_{self.tenant_id.replace('.', '_')}")

    def acquire_tenant_lock(self):
        """
        Acquire a lock for this tenant to prevent concurrent extractions.
        Returns True if lock acquired, False if tenant is already being processed.
        """
        try:
            if os.path.exists(self.lock_file):
                # Check if lock is stale (older than 2 hours)
                lock_age = time.time() - os.path.getmtime(self.lock_file)
                if lock_age > 7200:  # 2 hours
                    logger.warning(f"⚠️ Removing stale lock file (age: {lock_age/3600:.1f} hours)")
                    os.remove(self.lock_file)
                else:
                    # Read lock info
                    try:
                        with open(self.lock_file, 'r') as f:
                            lock_info = f.read().strip()
                        logger.warning(f"🔒 Tenant {self.tenant_id} is already being processed")
                        logger.warning(f"   Lock info: {lock_info}")
                        logger.warning(f"   Skipping this tenant to avoid conflicts")
                        return False
                    except:
                        pass

            # Create lock file with metadata
            hostname = socket.gethostname()
            pid = os.getpid()
            timestamp = time.strftime('%Y-%m-%d %H:%M:%S')
            lock_content = f"{hostname}:{pid} at {timestamp}"

            with open(self.lock_file, 'w') as f:
                f.write(lock_content)

            logger.info(f"🔓 Acquired lock for tenant: {self.tenant_id}")
            logger.info(f"   Lock file: {self.lock_file}")
            return True

        except Exception as e:
            logger.warning(f"Failed to acquire lock: {e}")
            return False

    def release_tenant_lock(self):
        """Release the tenant lock"""
        try:
            if os.path.exists(self.lock_file):
                os.remove(self.lock_file)
                logger.info(f"🔓 Released lock for tenant: {self.tenant_id}")
        except Exception as e:
            logger.warning(f"Failed to release lock: {e}")

    def get_connection(self):
        """Create and return database connection"""
        try:
            conn = psycopg2.connect(**self.db_config)
            logger.info("Database connection established")
            return conn
        except Exception as e:
            logger.error(f"Database connection failed: {e}")
            raise

    def validate_tenant_data(self, conn):
        """
        Check if tenant exists in database and has data
        Fails early with clear error message if no data found
        """
        logger.info(f"Validating data availability for tenant: {self.tenant_id}")

        try:
            cursor = conn.cursor()

            # Check if tenant has any property records
            cursor.execute(
                "SELECT COUNT(*) FROM eg_pt_property WHERE tenantid = %s",
                [self.tenant_id]
            )
            property_count = cursor.fetchone()[0]

            if property_count == 0:
                cursor.close()
                raise ValueError(
                    f"❌ No data found for tenant: {self.tenant_id}\n"
                    f"   Please verify the tenant ID is correct.\n"
                    f"   Tenant must exist in the database with property records.\n"
                    f"   Available tenants can be queried from eg_pt_property table."
                )

            # Check if tenant has ACTIVE properties
            cursor.execute(
                "SELECT COUNT(*) FROM eg_pt_property WHERE tenantid = %s AND status = 'ACTIVE'",
                [self.tenant_id]
            )
            active_count = cursor.fetchone()[0]

            logger.info(f"✅ Tenant validation passed:")
            logger.info(f"   Total properties: {property_count:,}")
            logger.info(f"   Active properties: {active_count:,}")

            if active_count == 0:
                logger.warning(f"⚠️  Warning: Tenant has {property_count} properties but NONE are ACTIVE")

            cursor.close()
            return True

        except ValueError:
            # Re-raise validation errors
            raise
        except Exception as e:
            logger.error(f"Failed to validate tenant data: {e}")
            raise ValueError(f"Database validation failed for tenant {self.tenant_id}: {e}")

    def create_output_directory(self):
        """Create tenant-specific output directory"""
        tenant_name = self.tenant_id.split('.')[-1]  # Extract 'adampur' from 'pb.adampur'
        self.tenant_dir = os.path.join(self.output_dir, tenant_name)
        os.makedirs(self.tenant_dir, exist_ok=True)
        logger.info(f"Created output directory: {self.tenant_dir}")

    def extract_small_tables(self, conn):
        """Extract smaller tables that don't need chunking - ACTIVE records only"""
        # Define table-specific queries with status filters
        table_queries = {
            'eg_pt_property': """
                SELECT * FROM eg_pt_property
                WHERE tenantid = %s AND status = 'ACTIVE'
            """,
            'eg_pt_owner': """
                SELECT * FROM eg_pt_owner
                WHERE tenantid = %s AND status = 'ACTIVE'
            """,
            'eg_pt_unit': """
                SELECT * FROM eg_pt_unit
                WHERE tenantid = %s AND active = true
            """
        }

        for table, query in table_queries.items():
            logger.info(f"Extracting table: {table} (ACTIVE records only)")

            try:
                df = pd.read_sql_query(query, conn, params=[self.tenant_id])
                output_file = os.path.join(self.tenant_dir, f"{table}.csv")
                df.to_csv(output_file, index=False)

                # Log file size
                file_size_bytes, file_size_str = get_file_size(output_file)
                logger.info(f"✅ Saved {len(df)} ACTIVE records to {table}.csv (Size: {file_size_str})")

            except Exception as e:
                logger.error(f"Failed to extract {table}: {e}")
                raise

    def extract_demand_table(self, conn):
        """Extract egbs_demand_v1 with PT businessservice and ACTIVE status filters"""
        table = 'egbs_demand_v1'
        logger.info(f"Extracting {table} (PT businessservice, ACTIVE status only)")

        # Create subdirectory for chunks
        table_dir = os.path.join(self.tenant_dir, table)
        os.makedirs(table_dir, exist_ok=True)

        # Get total count for progress tracking
        count_query = f"""
        SELECT COUNT(*) FROM {table}
        WHERE tenantid = %s
          AND businessservice = 'PT'
          AND status = 'ACTIVE'
        """

        cursor = conn.cursor()
        cursor.execute(count_query, [self.tenant_id])
        total_records = cursor.fetchone()[0]
        logger.info(f"Total ACTIVE PT records for {self.tenant_id} in {table}: {total_records:,}")

        # Extract in chunks
        chunk_number = 0
        offset = 0

        with tqdm(total=total_records, desc=f"Extracting {table}") as pbar:
            while offset < total_records:
                chunk_query = f"""
                SELECT * FROM {table}
                WHERE tenantid = %s
                  AND businessservice = 'PT'
                  AND status = 'ACTIVE'
                ORDER BY id
                LIMIT %s OFFSET %s
                """

                df_chunk = pd.read_sql_query(
                    chunk_query,
                    conn,
                    params=[self.tenant_id, self.chunk_size, offset]
                )

                if df_chunk.empty:
                    break

                # Ensure directory exists before writing (safeguard against any issues)
                os.makedirs(table_dir, exist_ok=True)
                output_file = os.path.join(table_dir, f"output_{chunk_number}.csv")
                df_chunk.to_csv(output_file, index=False)

                # Log chunk with size
                chunk_size_bytes, chunk_size_str = get_file_size(output_file)
                logger.info(f"Chunk {chunk_number}: {len(df_chunk)} records → output_{chunk_number}.csv ({chunk_size_str})")

                offset += self.chunk_size
                chunk_number += 1
                pbar.update(len(df_chunk))

        # Log total size for this table
        table_size_bytes, table_size_str = get_directory_size(table_dir)
        logger.info(f"✅ {table} complete: {chunk_number} chunks, Total size: {table_size_str}")

        cursor.close()

    def extract_demanddetail_table(self, conn):
        """Extract egbs_demanddetail_v1 using id-based pagination (no status filter)"""
        table = 'egbs_demanddetail_v1'
        logger.info(f"Extracting {table} (all records, id-based pagination)")

        # Create subdirectory for chunks
        table_dir = os.path.join(self.tenant_dir, table)
        os.makedirs(table_dir, exist_ok=True)

        # Get total count for progress tracking
        count_query = f"""
        SELECT COUNT(*) FROM {table}
        WHERE tenantid = %s
        """

        cursor = conn.cursor()
        cursor.execute(count_query, [self.tenant_id])
        total_records = cursor.fetchone()[0]
        logger.info(f"Total records for {self.tenant_id} in {table}: {total_records:,}")

        # Extract in chunks using id-based pagination
        # Note: id column is VARCHAR, so initialize as empty string
        chunk_number = 0
        last_id = ''

        with tqdm(total=total_records, desc=f"Extracting {table}") as pbar:
            while True:
                chunk_query = f"""
                SELECT * FROM {table}
                WHERE tenantid = %s
                  AND id > %s
                ORDER BY id
                LIMIT %s
                """

                df_chunk = pd.read_sql_query(
                    chunk_query,
                    conn,
                    params=[self.tenant_id, last_id, self.chunk_size]
                )

                if df_chunk.empty:
                    break

                # Ensure directory exists before writing (safeguard against any issues)
                os.makedirs(table_dir, exist_ok=True)
                output_file = os.path.join(table_dir, f"output_{chunk_number}.csv")
                df_chunk.to_csv(output_file, index=False)

                # Update last_id for next iteration
                last_id = df_chunk['id'].max()

                # Log chunk with size
                chunk_size_bytes, chunk_size_str = get_file_size(output_file)
                logger.info(f"Chunk {chunk_number}: {len(df_chunk)} records (last_id: {last_id}) → output_{chunk_number}.csv ({chunk_size_str})")

                chunk_number += 1
                pbar.update(len(df_chunk))

        # Log total size for this table
        table_size_bytes, table_size_str = get_directory_size(table_dir)
        logger.info(f"✅ {table} complete: {chunk_number} chunks, Total size: {table_size_str}")

        cursor.close()

    def run_extraction(self):
        """Main extraction process with tenant locking"""
        logger.info(f"Starting data extraction for tenant: {self.tenant_id}")

        # Acquire lock for this tenant
        if not self.acquire_tenant_lock():
            logger.warning(f"⏭️ Skipping {self.tenant_id} - already being processed by another task")
            return

        try:
            # Create output directory
            self.create_output_directory()

            # Get database connection
            conn = self.get_connection()

            # VALIDATE: Check if tenant exists and has data
            # This will fail fast with a clear error if tenant doesn't exist
            self.validate_tenant_data(conn)

            # Extract small tables (ACTIVE records only)
            self.extract_small_tables(conn)

            # Extract demand table (PT businessservice, ACTIVE status)
            self.extract_demand_table(conn)

            # Extract demand detail table (all records, id-based pagination)
            self.extract_demanddetail_table(conn)

            # Close connection
            conn.close()

            # Log total extraction size
            total_size_bytes, total_size_str = get_directory_size(self.tenant_dir)
            logger.info("")
            logger.info("=" * 70)
            logger.info(f"📦 EXTRACTION COMPLETE - Total data size: {total_size_str}")
            logger.info(f"📁 Location: {self.tenant_dir}")
            logger.info("=" * 70)

        except ValueError as e:
            # Validation errors - these are user-facing
            logger.error(f"❌ Validation failed: {e}")
            raise
        except Exception as e:
            logger.error(f"❌ Extraction failed: {e}")
            raise
        finally:
            # Always release the lock, even if extraction failed
            self.release_tenant_lock()

if __name__ == "__main__":
    extractor = PunjabDataExtractor()
    extractor.run_extraction()