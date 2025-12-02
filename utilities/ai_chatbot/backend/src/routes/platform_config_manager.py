"""platform_config_manager.py: File contains logic for platform configs api """


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
from datetime import datetime
from flask import request, Blueprint, Response
from src.models.platform_config import PlatformConfiguration
from src.repos.platform_config_repo import PlatformConfigRepo
from src.auth.auth_provider import check_user_role
from src.auth.base_auth_provider import UserRoles
from src.utils.util import AlchemyHelper, CustomEncoder
from src.utils.constants import ConfigDataType
from error_handler.custom_exception import ResourceUpdateException, ResourceGetException, InvalidParamException
from src.config.logger_config import logger

pltconfigs_bp = Blueprint('pltconfigs', __name__)


# This method executes before any API request
@pltconfigs_bp.before_request
def before_request():
    logger.debug('Entering platform configs service.')


# This method executes after every API request.
@pltconfigs_bp.after_request
def after_request(response):
    logger.debug('Exiting platform configs service.')
    return response


@pltconfigs_bp.route('/configs', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_all_platform_configs(user_info):
    try:
        logger.info("Entering get_all_platform_configs.")
        page = request.args.get('page', type=int, default=-1)
        per_page = request.args.get('per_page', type=int, default=-1)
        items = PlatformConfigRepo().find_all(page, per_page)
        if not items:
            return Response("", status=200, mimetype='application/json')

        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get configuration(s).')
    finally:
        logger.info("Exiting get_all_platform_configs.")


@pltconfigs_bp.route('/configs/<id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_platform_config(user_info, id):
    try:
        logger.info(f"Entering get_platform_config {id}.")
        item = PlatformConfigRepo().find_by_id(id)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get configuration with id {id}.')
    finally:
        logger.info("Exiting get_platform_config.")


@pltconfigs_bp.route('/configs/uiSettings', methods=['GET'])
# Note
# it's open API to get the details of UI setting details
def get_ui_settings():
    name = 'UISettings'
    try:
        logger.info(f"Entering get_ui_settings {name}.")
        item = PlatformConfigRepo().find_by_name(name)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get configuration with name {name}.')
    finally:
        logger.info("Exiting get_ui_settings.")


def __get_platform_config(data, user_id) :
    name = data['name']
    display_name = data['display_name']
    type = data['type']
    value = data['value']

    if((name is None) or (name == '') or (display_name is None) or (display_name == '') or (type is None) or (type == '')) :
        raise InvalidParamException

    #when data type is Json then converting to Json String
    if(type == ConfigDataType.Json.name):
        value = json.dumps(value)
    
    return PlatformConfiguration(name, display_name, type, value, False, user_id, True, datetime.now())


@pltconfigs_bp.route('/configs/<name>', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_platform_config(user_info, name):
    try:
        logger.info(f"Entering update_platform_config {name}.")
        data = request.json
        logger.debug(f"data = {data}")
        user = request.user
        user_name = user['user_name']
        
        conf = __get_platform_config(data, user_name)
        conf.modified_by = user_name
        
        item = PlatformConfigRepo().modify_by_name(name, conf)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceUpdateException("configuration", name)
    finally:
        logger.info("Exiting update_platform_config.")
