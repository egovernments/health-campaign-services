"""__init__.py: App init """

__author__ = "Umesh"
__copyright__ = "Copyright 2023, Prescience Decision Solutions"


from src.config.logger_config import logger

from .chat_manager import chats_bp
from .server_info_manager import server_info_bp
from .model_info_manager import model_info_bp
from .user_manager import users_bp
from .tag_manager import tags_bp
from .platform_config_manager import pltconfigs_bp
from .config_manager import configs_bp
from .dashboard_manager import dashboards_bp
from .connection_manager import connections_bp

API_VERSION_URL = '/api/v1'

def configure_routes(app):
    try:
        logger.debug("Entering configure_routes.")
        app.register_blueprint(server_info_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(model_info_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(pltconfigs_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(tags_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(users_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(configs_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(chats_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(dashboards_bp, url_prefix=API_VERSION_URL)
        app.register_blueprint(connections_bp, url_prefix=API_VERSION_URL)
    except Exception as ex:
        logger.error(ex.args)
        raise ex
    finally:
        logger.debug("Exiting configure_routes.")
