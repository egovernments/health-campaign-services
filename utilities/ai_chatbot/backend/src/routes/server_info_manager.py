"""server_info_manager.py: File contains logic for service health """


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
import os
from flask import jsonify, Blueprint, Response, request
from src.repos.product_repo import ProductRepo
from src.utils.constants import ProductFeature
from src.utils.util import AlchemyHelper, CustomEncoder
from src.auth.auth_provider import check_user_role
from src.auth.base_auth_provider import UserRoles
from error_handler.custom_exception import ResourceGetException
from src.config.logger_config import logger

server_info_bp = Blueprint('server_info', __name__)


# This method executes before any API request
@server_info_bp.before_request
def before_request():
    logger.debug('Entering server info service.')


# This method executes after every API request.
@server_info_bp.after_request
def after_request(response):
    logger.debug('Exiting server info service.')
    return response


@server_info_bp.route('/health', methods=['GET'])
def get_health():
    try:
        logger.info("Entering get_health.")
        stats = {
            "server": "OK"
        }

        return jsonify(stats)
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot get server health.')
    finally:
        logger.info("Exiting get_health.")


@server_info_bp.route('/version', methods=['GET'])
def get_version():
    try:
        logger.info("Entering get_version.")
        item = ProductRepo().find_product_info()
        if not item:
            return Response("", status=200, mimetype='application/json')

        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get version information.')
    finally:
        logger.info("Exiting get_version.")


def __load_product_features():
    features = []
    features.append(ProductFeature.AIChatbot.value)

    superset_host = os.getenv('SUPERSET_SERVER', None)
    if(superset_host is not None):
        features.append(ProductFeature.Dashboards.value)
    
    return features


@server_info_bp.route('/features', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_features(user_info):
    try:
        logger.info("Entering get_features.")
        feature_name = request.args.get('name', type=str, default=None)
        features = __load_product_features()
        if (feature_name is None or feature_name == ''):
            return jsonify(features)
        else:
            is_exists = feature_name in features
            return jsonify(is_exists)
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get features.')
    finally:
        logger.info("Exiting get_features.")
