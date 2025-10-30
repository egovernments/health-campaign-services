"""
Punjab Analysis System - Configuration Loader
Loads configuration from YAML secret files for production deployment
"""

import os
import yaml
import logging

logger = logging.getLogger(__name__)

class ConfigLoader:
    """Load and manage configuration from YAML files"""

    def __init__(self, config_path=None, environment=None):
        """
        Initialize config loader

        Args:
            config_path: Path to YAML config file (defaults to /app/secrets/db_config.yaml)
            environment: Environment name (development/staging/production)
        """
        self.config_path = config_path or os.getenv('CONFIG_PATH', '/app/secrets/db_config.yaml')
        self.environment = environment or os.getenv('ENVIRONMENT', 'development')
        self.config = self._load_config()

    def _load_config(self):
        """Load configuration from YAML file"""
        try:
            with open(self.config_path, 'r') as f:
                config = yaml.safe_load(f)

            logger.info(f"✅ Loaded configuration from {self.config_path}")
            logger.info(f"🌍 Environment: {self.environment}")

            # Apply environment-specific overrides
            if 'environments' in config and self.environment in config['environments']:
                env_config = config['environments'][self.environment]
                config = self._merge_configs(config, env_config)
                logger.info(f"📝 Applied {self.environment} environment overrides")

            return config

        except FileNotFoundError:
            logger.error(f"❌ Configuration file not found: {self.config_path}")
            raise ValueError(f"Configuration file not found: {self.config_path}")
        except yaml.YAMLError as e:
            logger.error(f"❌ Invalid YAML format: {e}")
            raise ValueError(f"Invalid YAML configuration: {e}")
        except Exception as e:
            logger.error(f"❌ Failed to load configuration: {e}")
            raise

    def _merge_configs(self, base_config, override_config):
        """Merge environment-specific overrides with base config"""
        merged = base_config.copy()

        for key, value in override_config.items():
            if key in merged and isinstance(merged[key], dict) and isinstance(value, dict):
                merged[key] = {**merged[key], **value}
            else:
                merged[key] = value

        return merged

    def get_database_config(self):
        """Get database connection configuration"""
        try:
            db_config = self.config['database'].copy()

            # Validate required fields
            required_fields = ['host', 'port', 'name', 'user', 'password']
            missing_fields = [field for field in required_fields if not db_config.get(field)]

            if missing_fields:
                raise ValueError(f"Missing required database fields: {missing_fields}")

            # Convert to format expected by psycopg2
            psycopg2_config = {
                'host': db_config['host'],
                'port': int(db_config['port']),
                'database': db_config['name'],
                'user': db_config['user'],
                'password': db_config['password']
            }

            # Add optional connection parameters
            if 'connection_timeout' in db_config:
                psycopg2_config['connect_timeout'] = db_config['connection_timeout']

            logger.info(f"🔗 Database config loaded: {db_config['user']}@{db_config['host']}:{db_config['port']}/{db_config['name']}")

            return psycopg2_config

        except KeyError as e:
            raise ValueError(f"Missing database configuration section: {e}")

    def get_settings(self):
        """Get application settings"""
        return self.config.get('settings', {})

    def get_tenant_config(self, tenant_id):
        """Get tenant-specific configuration"""
        tenants = self.config.get('tenants', {})
        return tenants.get(tenant_id, {})

    def get_setting(self, key, default=None, tenant_id=None):
        """
        Get a specific setting value with fallback hierarchy:
        1. Tenant-specific setting (if tenant_id provided)
        2. Global setting
        3. Default value
        """
        # Check tenant-specific setting first
        if tenant_id:
            tenant_config = self.get_tenant_config(tenant_id)
            if key in tenant_config:
                return tenant_config[key]

        # Check global settings
        settings = self.get_settings()
        if key in settings:
            return settings[key]

        # Return default
        return default

    def get_data_dirs(self):
        """Get configured data directories"""
        settings = self.get_settings()
        return {
            'data_dir': settings.get('data_dir', '/data'),
            'output_dir': settings.get('output_dir', '/output'),
            'reports_dir': settings.get('reports_dir', '/local-reports')
        }

    def get_cleanup_config(self):
        """Get cleanup configuration"""
        settings = self.get_settings()
        return {
            'max_age_hours': settings.get('max_age_hours', 24),
            'dry_run': settings.get('dry_run', False)
        }

    def validate_config(self):
        """Validate configuration completeness"""
        logger.info("🔍 Validating configuration...")

        # Validate database config
        try:
            db_config = self.get_database_config()
            logger.info("✅ Database configuration valid")
        except Exception as e:
            logger.error(f"❌ Database configuration invalid: {e}")
            raise

        # Validate settings
        settings = self.get_settings()
        if not settings:
            logger.warning("⚠️ No application settings found, using defaults")
        else:
            logger.info("✅ Application settings loaded")

        logger.info("🎉 Configuration validation completed successfully")

# Example usage and testing
if __name__ == "__main__":
    # Test the configuration loader
    try:
        # Test with local file (development)
        config_loader = ConfigLoader(
            config_path='/home/admin1/Desktop/airflow-punjab-analysis/k8s/secrets/db_config.yaml',
            environment='development'
        )

        config_loader.validate_config()

        # Test database config
        db_config = config_loader.get_database_config()
        print(f"Database: {db_config}")

        # Test tenant-specific settings
        adampur_chunk_size = config_loader.get_setting('chunk_size', tenant_id='pb.adampur')
        print(f"Adampur chunk size: {adampur_chunk_size}")

        # Test cleanup config
        cleanup_config = config_loader.get_cleanup_config()
        print(f"Cleanup config: {cleanup_config}")

    except Exception as e:
        print(f"Configuration test failed: {e}")