"""model_info_manager.py: File contains logic for llm models """


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
from datetime import datetime
from flask import Blueprint, Response, request
from src.utils.constants import ModelStatus, SupportedLLM, KeyMap
from src.llm.llm_base import LLM_TO_SETTINGS_MAPPING
from src.auth.auth_provider import check_user_role
from src.auth.base_auth_provider import UserRoles
from src.models.model_info import LLMModelInfo
from src.repos.model_info_repo import ModelInfoRepo
from src.utils.util import AlchemyHelper, CustomEncoder, base64_to_string
from error_handler.custom_exception import ResourceNotFoundException, InvalidParamException, ResourceCreateException, \
    ResourceUpdateException, UnsupportedModelException, ResourceDeleteException, ResourceGetException, ActionNotAllowedException
from src.config.logger_config import logger

model_info_bp = Blueprint('model_info', __name__)


# This method executes before any API request
@model_info_bp.before_request
def before_request():
    logger.debug('Entering model info service.')


# This method executes after every API request.
@model_info_bp.after_request
def after_request(response):
    logger.debug('Exiting model info service.')
    return response


@model_info_bp.route('/models/supported', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_supported(user_info):
    try:
        logger.info("Entering get_supported.")
        types = []
        for type in SupportedLLM:
            types.append(KeyMap(type.name, type.value).__dict__)

        jsonstr = json.dumps(types)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get supported LLM types.')
    finally:
        logger.info("Exiting get_supported.")


@model_info_bp.route('/models/getSettingsTemplate', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_model_settings_template(user_info):
    try:
        logger.info("Entering get_model_settings_template.")
        type = request.args.get("type", default=SupportedLLM.AzureOpenAI.value, type=str)
        jsonstr = ''
        if type in [item.value for item in SupportedLLM]:
            jsonstr = json.dumps(LLM_TO_SETTINGS_MAPPING[type])

        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get supported LLM settings template.')
    finally:
        logger.info("Exiting get_model_settings_template.")


@model_info_bp.route('/models', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_all_models(user_info):
    try:
        logger.info("Entering get_all_models.")
        items = ModelInfoRepo().find_all()
        if not items:
            return Response("", status=200, mimetype='application/json')
        
        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot get model(s) metadata.')
    finally:
        logger.info("Exiting get_all_models.")


@model_info_bp.route('/models/<id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_configuration(user_info, id):
    try:
        logger.info(f"Entering get_configuration {id}.")
        item = ModelInfoRepo().find_by_id(id)

        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot get model configuration.")
    finally:
        logger.info("Exiting get_configuration.")   


def __get_model_config(data, user_name):
    name = data['name'].strip()
    description = data['description'].strip()
    type = data['type']
    is_active = data['is_active']
    is_default = data['is_default']

    # want to make sure UI is always setting it    
    settings = data['settings']
    if(settings is not None and settings != ''):
        base64_to_string(settings)

    logger.debug(f"Conf name = {name}, user name = {user_name}")
    conf = LLMModelInfo(name, type, description, settings, ModelStatus.INIT_COMPLETE.value, 
                            is_default, user_name, is_active, datetime.now())
    #validating config before saving it
    conf.validate()
    return conf


@model_info_bp.route('/models', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def create_configuration(user_info):
    try:
        logger.info("Entering create_configuration.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid configuration data.')
        
        logger.debug(data)

        user = request.user
        user_name = user['user_name']

        # make first one as default
        count = ModelInfoRepo().get_count()
        if(count == 0):
            data['is_default'] = True
        else:
            data['is_default'] = False

        config = __get_model_config(data, user_name)

        # writing to DB connection details
        item = ModelInfoRepo().save(config)
        if not item:
            raise ResourceCreateException("model configuration", config.name)
        
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=201, mimetype='application/json')
    except (InvalidParamException, ResourceCreateException, UnsupportedModelException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceCreateException('model configuration')
    finally:
        logger.info("Exiting create_configuration.")


@model_info_bp.route('/models/<id>', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_configuration(user_info, id):
    try:
        logger.info(f"Entering update_configuration {id}.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid configuration data.')
        
        user = request.user
        user_name = user['user_name']
        
        config = __get_model_config(data, user_name)
        config.modified_by = user_name
        
        item = ModelInfoRepo().modify(id, config)
        if not item:
            raise ResourceUpdateException("model configuration", config.name)

        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (InvalidParamException, ResourceNotFoundException, ResourceUpdateException, UnsupportedModelException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot update model configuration. {ex.args}')
    finally:
        logger.info("Exiting update_configuration.")


@model_info_bp.route('/models/<id>/updateStatus', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_status(user_info, id):
    try:
        logger.info(f"Entering update_status {id}.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid configuration data.')
        
        user = request.user
        user_name = user['user_name']
        status = data['status']
        item = ModelInfoRepo().modify_status(id, status, user_name)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceUpdateException("model configuration", id)
    finally:
        logger.info("Exiting update_status.")


@model_info_bp.route('/models/default', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_default(user_info):
    try:
        logger.info("Entering get_default.")
        item = ModelInfoRepo().find_default()
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get default model configuration.')
    finally:
        logger.info("Exiting get_default.")


@model_info_bp.route('/models/<id>/default', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def set_default(user_info, id):
    try:
        logger.info(f"Entering set_default for {id}.")
        user = request.user
        user_name = user['user_name']

        item = ModelInfoRepo().find_by_id(id)
        if(item.is_default == False):
            item = ModelInfoRepo().modify_default_for_all(id, user_name)

        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceUpdateException(f'default output location')
    finally:
        logger.info("Exiting set_default.")


@model_info_bp.route('/models/<id>', methods=['DELETE'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def delete_configuration(user_info, id):
    try:
        logger.info(f"Entering delete_configuration {id}.")
        total_count = ModelInfoRepo().get_count()

        item = ModelInfoRepo().find_by_id(id)
        if(item.is_default and total_count > 1):
            raise ActionNotAllowedException('Default model configuration cannot be deleted.')
        
        # TODO
        # need to check if this configuration is used in any Dataset configuration
        # if so throw error
        ModelInfoRepo().delete_by_id(id)
        return Response("", status=204, mimetype='application/json')
    except (ResourceNotFoundException, ResourceDeleteException, ActionNotAllowedException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot delete model configuration.')
    finally:
        logger.info("Exiting delete_configuration.")
