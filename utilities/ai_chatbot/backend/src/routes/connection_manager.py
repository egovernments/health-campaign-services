"""connection_manager.py: File contains logic for connections API"""

__author__ = "Praful Nigam"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import json
from datetime import datetime
from flask import request, Blueprint, Response
from src.db.db_factory import get_db_factory
from src.auth.base_auth_provider import UserRoles
from src.auth.auth_provider import check_user_role
from src.repos.config_repo import ConfigRepo
from src.repos.connection_repo import ConnectionRepo
from src.models.connection_info import ConnectionInfo
from src.utils.constants import TestStatus, AllowedConnectionType, KeyMap
from src.utils.util import AlchemyHelper, CustomEncoder, is_allowed_connection
from src.config.logger_config import logger
from error_handler.custom_exception import ( ResourceCreateException, ResourceUpdateException, ResourceNotFoundException,
    ResourceGetException, InvalidParamException, InvalidDatabaseCredentialsException, DatabaseConnectionException, TestConnectionException, UnsupportedPlatformException,
    ResourceInUseException
)

connections_bp = Blueprint('connections', __name__)


@connections_bp.before_request
def before_request():
    logger.debug("Entering connections service.")


@connections_bp.after_request
def after_request(response):
    logger.debug("Exiting connections service.")
    return response


def __get_connection(data, user_name):
    name = data['name'].strip()
    description = data['description'].strip()
    is_active = data['is_active']
    type = data['type'].strip() 
    data = data['data']
    test_status = True
    tested_at = datetime.now()

    connection = ConnectionInfo( name, description, type, json.dumps(data), tested_at, test_status, 
                                user_name, is_active, datetime.now())
    connection.validate()
    return connection


@connections_bp.route('/connections/supported', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_supported(user_info):
    try:
        logger.info("Entering get_supported.")
        types = []
        for type in AllowedConnectionType:
            types.append(KeyMap(type.name, type.value).__dict__)

        jsonstr = json.dumps(types)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get supported connection type.')
    finally:
        logger.info("Exiting get_supported.")


@connections_bp.route('/connections', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def create_connection(user_info):
    try:
        logger.info("Entering create_connection.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid configuration data.')
        
        logger.debug(data)

        user = request.user
        user_name = user['user_name']

        conn_info = data['data']
        conn_type = data['type']
        conn_info['db_type'] = conn_type

        if not is_allowed_connection(conn_type):
            raise UnsupportedPlatformException('Unsupported connection type.')

        is_success = get_db_factory(conn_type).test_connection(conn_info)
        if not is_success:
            raise InvalidDatabaseCredentialsException
        
        conn = __get_connection(data, user_name)

        item = ConnectionRepo().save(conn)
        if not item:
            raise ResourceCreateException("connection", conn.name)
        
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=201, mimetype='application/json')
    except (InvalidParamException, ResourceCreateException, InvalidDatabaseCredentialsException, DatabaseConnectionException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceCreateException("connection")
    finally:
        logger.info("Exiting create_connection.")


@connections_bp.route('/connections', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_all_connections(user_info):
    try:
        logger.info("Entering get_all_connections.")
        page = request.args.get('page', type=int, default=-1)
        per_page = request.args.get('per_page', type=int, default=-1)
        items = ConnectionRepo().find_all(page, per_page)
        if not items:
            return Response("", status=200, mimetype='application/json')
        
        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException("Cannot get connection(s).")
    finally:
        logger.info("Exiting get_all_connections.")


@connections_bp.route('/connections/<id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_connection(user_info, id):
    try:
        logger.info(f"Entering get_connection {id}.")
        item = ConnectionRepo().find_by_id(id)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f"Cannot get connection with id {id}.")
    finally:
        logger.info("Exiting get_connection.")


@connections_bp.route('/connections/<id>', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_connection(user_info, id):
    try:
        logger.info(f"Entering update_connection {id}.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid tag data.')
        
        user = request.user
        user_name = user.get('user_name')

        conn_info = data['data']
        conn_type = data['type']
        conn_info['db_type'] = conn_type

        if not is_allowed_connection(conn_type):
            raise UnsupportedPlatformException('Unsupported connection type.')
        
        is_success = get_db_factory(conn_type).test_connection(conn_info)
        if not is_success:
            raise InvalidDatabaseCredentialsException
        
        conn = __get_connection(data, user_name)
        conn.modified_by = user_name

        item = ConnectionRepo().modify(id, conn)
        if not item:
            raise ResourceUpdateException("connection", conn.name)
        
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (InvalidParamException, ResourceNotFoundException, ResourceUpdateException, InvalidDatabaseCredentialsException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f"Cannot update connection. {ex.args}")
    finally:
        logger.info("Exiting update_connection.")


@connections_bp.route('/connections/<id>', methods=['DELETE'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def delete_connection(user_info, id):
    try:
        logger.info(f"Entering delete_connection {id}.")
        # checking if it is used in any configuration before deleting it.
        items = ConfigRepo().find_all()
        for item in items:
            json_data = json.loads(item.data)
            if int(json_data.get('conn_id')) == int(id):
                raise ResourceInUseException('Cannot delete connection, it is already in use.')

        ConnectionRepo().delete_by_id(id)
        return Response("", status=204, mimetype='application/json')
    except (ResourceInUseException, ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot delete connection.")
    finally:
        logger.info("Exiting delete_connection.")


@connections_bp.route('/connections/<id>/test', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def test_connection(user_info, id):
    try:
        logger.info(f"Entering test_connection {id}.")
        user = request.user
        user_name = user.get('user_name')

        item = ConnectionRepo().find_by_id(id)

        conn = json.loads(item.data)

        is_success = get_db_factory(conn['db_type']).test_connection(conn)
        logger.debug(f"Test status = {is_success}")
        if not is_success:
            raise InvalidDatabaseCredentialsException
        
        test_msg = TestStatus.FAILED.value
        if(is_success == True):
            test_msg = TestStatus.SUCCESS.value

        #modifying the testing information
        item = ConnectionRepo().modify_test_result(id, datetime.now(), test_msg, user_name)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)

        try:
            # update status to Failed
            ConnectionRepo().modify_test_result(id, datetime.now(), TestStatus.FAILED.value, user_name)
        except Exception as ex:
            logger.error(ex.args)
            # ignore it

        raise TestConnectionException
    finally:
        logger.info("Exiting test_connection.")