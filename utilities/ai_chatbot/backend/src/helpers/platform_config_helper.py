"""platform_config_helper.py: Config helper"""

__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

from src.repos.platform_config_repo import PlatformConfigRepo
from src.utils.constants import ConfigDataType
from src.config.logger_config import logger


def get_value(config):
    if(config.type == ConfigDataType.String.name):
        return "" if (config.value == None) else str(config.value.strip())
    elif(config.type == ConfigDataType.Integer.name):
        return -1 if (config.value == None or config.value == "") else int(config.value.strip())
    elif(config.type == ConfigDataType.Float.name):
        return float(config.value.strip())
    elif(config.type == ConfigDataType.Boolean.name):
        return True if(config.value.strip() in ["True", "true", "t", "T", "y", "Y", "Yes", "yes"]) else False
    elif(config.type == ConfigDataType.Json.name):
        return None if(config.value == '') else config.value.strip()
    else:
        return str(config.value.strip())

        
def get_config_value(config) :
    try:
        logger.debug("Entering get_config_value.")
        #Note: assuming enum will be passed here
        conf = PlatformConfigRepo().find_by_name(config.name)
        if(conf is None) :
            return None

        return get_value(conf)
    except Exception as ex:
        logger.error(ex.args)
        return None
    finally:
        logger.debug("Exiting get_config_value.")
