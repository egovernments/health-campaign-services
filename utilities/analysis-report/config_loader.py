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
        """
        Load configuration from YAML file.
        Returns empty dict if file not found (will use environment variables instead).
        """
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
            # File not found - return empty config (will use environment variables)
            logger.warning(f"⚠️ Configuration file not found: {self.config_path}")
            logger.info(f"ℹ️ Will use environment variables for configuration")
            return {}
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
        """
        Get database connection configuration
        Priority: Environment variables (from ConfigMaps) > YAML file
        """
        # Try reading from environment variables first (from Kubernetes ConfigMaps)
        db_host = os.getenv('DB_HOST')
        db_name = os.getenv('DB_NAME')
        db_user = os.getenv('DB_USER')
        db_password = os.getenv('DB_PASSWORD')
        db_port = os.getenv('DB_PORT', '5432')

        if db_host and db_name and db_user and db_password:
            # Use environment variables (Kubernetes ConfigMaps approach)
            logger.info(f"🔗 Using database config from environment variables (ConfigMaps)")
            psycopg2_config = {
                'host': db_host,
                'port': int(db_port),
                'database': db_name,
                'user': db_user,
                'password': db_password
            }
            logger.info(f"🔗 Database config: {db_user}@{db_host}:{db_port}/{db_name}")
            return psycopg2_config

        # Fall back to YAML file
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

            logger.info(f"🔗 Database config loaded from YAML: {db_config['user']}@{db_config['host']}:{db_config['port']}/{db_config['name']}")

            return psycopg2_config

        except KeyError as e:
            raise ValueError(f"Missing database configuration (not in env vars or YAML): {e}")

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
    """
    Test the configuration loader with environment variables.

    Usage:
        # Test with environment variables (production mode):
        export DB_HOST=localhost
        export DB_NAME=testdb
        export DB_USER=postgres
        export DB_PASSWORD=password
        python config_loader.py

        # Test with config file (development mode):
        export CONFIG_PATH=/path/to/db_config.yaml
        python config_loader.py
    """
    import sys

    try:
        # Initialize with defaults (reads from environment variables)
        config_path = os.getenv('CONFIG_PATH', '/app/secrets/db_config.yaml')
        environment = os.getenv('ENVIRONMENT', 'production')

        logger.info(f"Testing ConfigLoader with environment: {environment}")
        logger.info(f"Config path: {config_path}")

        config_loader = ConfigLoader(
            config_path=config_path,
            environment=environment
        )

        # Validate configuration
        config_loader.validate_config()

        # Test database config
        db_config = config_loader.get_database_config()
        logger.info(f"✅ Database config loaded: {db_config['user']}@{db_config['host']}:{db_config['port']}/{db_config['database']}")

        # Test data directories
        data_dirs = config_loader.get_data_dirs()
        logger.info(f"✅ Data directories: {data_dirs}")

        logger.info("🎉 All tests passed!")
        sys.exit(0)

    except Exception as e:
        logger.error(f"❌ Configuration test failed: {e}")
        sys.exit(1)