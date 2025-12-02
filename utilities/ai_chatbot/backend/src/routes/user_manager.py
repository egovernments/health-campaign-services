"""user_manager.py: File contains logic for user apis """


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
from datetime import datetime
from flask import request, Blueprint, Response
from src.models.user_info import UserInfo
from src.models.user_favorite import UserFavorite
from src.repos.tag_repo import TagRepo
from src.repos.userinfo_repo import UserInfoRepo
from src.repos.user_favorite_repo import UserFavoriteRepo
from src.utils.constants import DEFAULT_ADMIN
from src.utils.util import AlchemyHelper, CustomEncoder, convert_to_int_list
from src.auth.error_handler.auth_exception import LoginException
from error_handler.custom_exception import ResourceCreateException, ResourceUpdateException,  \
    InvalidParamException, ResourceNotFoundException, ActionNotAllowedException, ResourceGetException, \
    InvalidUserDetailsException
from src.auth.auth_provider import get_auth_provider, check_user_role
from src.auth.base_auth_provider import UserRoles
from src.config.logger_config import logger

users_bp = Blueprint('users', __name__)

# This method executes before any API request
@users_bp.before_request
def before_request():
    logger.debug('Entering users service.')


# This method executes after every API request.
@users_bp.after_request
def after_request(response):
    logger.debug('Exiting users service.')
    return response


@users_bp.route('/users/login', methods=['POST'])
def auth_user():
    try:
        logger.info("Entering auth_user.")
        data = request.json
        user_name = data['user_name']
        password = data['password']

        if(user_name is None or user_name == '' or password is None or password == ''):
            raise InvalidParamException('username or password')

        #logger.debug(f"user = {user_name}, password = {password}")
        access_tkn, refresh_tkn, user_guid, name, emailId, roles = get_auth_provider().get_token(user_name, password)

        # if authentication is success, add this also to our user table
        usr_info = UserInfo(user_guid, user_name, [], True, DEFAULT_ADMIN, datetime.now())
        # saving user info to DB
        item = UserInfoRepo().check_and_save(usr_info)
        if not item:
            raise ResourceCreateException("user", data['user_name'])

        resp_msg = {"access_token" : access_tkn, "refresh_token" : refresh_tkn, 
                    "user_name": user_name, "guid" : user_guid, "roles" : roles}
        #logger.debug(jwt)
        return Response(json.dumps(resp_msg), status=200, mimetype='application/json')
    except (LoginException, InvalidParamException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Invalid username or password.')
    finally:
        logger.info("Exiting auth_user.")


@users_bp.route('/users', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_all_users(user_info):
    try:
        logger.info("Entering get_all_users.")
        page = request.args.get('page', type=int, default=-1)
        per_page = request.args.get('per_page', type=int, default=-1)
                
        items = UserInfoRepo().find_all(page, per_page)
        if not items:
            return Response("", status=200, mimetype='application/json')
        
        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot get users.")
    finally:
        logger.info("Exiting get_all_users.")   

# Note
# this api can be called for user as well as admin both
@users_bp.route('/users/guid/<string:guid>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_user_by_guid(user_info, guid):
    try:
        logger.info(f"Entering get_user_by_guid {guid}.")
        
        user = request.user
        user_name = user['user_name']
        user_id = user['user_id']
        is_admin = user['is_admin']

        logger.debug(f"user_name = {user_name}, user_id = {user_id}, is_admin = {is_admin}")

        if(is_admin or (user_id == guid)):
            item = UserInfoRepo().find_by_guid(guid)
        else:
            raise ActionNotAllowedException(f'Cannot get details for user with guid {guid}.')
        
        items = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException, ActionNotAllowedException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot get user details.")
    finally:
        logger.info("Exiting get_user_by_guid.")   


# this api is only for admins
@users_bp.route('/users/<int:id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_user_by_id(user_info, id):
    try:
        logger.info(f"Entering get_user_by_id {id}.")
        item = UserInfoRepo().find_by_id(id)
                        
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        #logger.debug(jsonstr)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot get user.")
    finally:
        logger.info("Exiting get_user_by_id.")   


@users_bp.route('/users/<guid>/favorites', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_favorites(user_info, guid):
    try:
        logger.info(f"Entering get_favorites {guid}.")
        page = request.args.get('page', type=int, default=-1)
        per_page = request.args.get('per_page', type=int, default=-1)

        item = UserInfoRepo().find_by_guid(guid)

        items = UserFavoriteRepo().find_all_by_user(item.id, page, per_page)
        if not items:
            return Response("", status=200, mimetype='application/json')
        
        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot get user favorites.")
    finally:
        logger.info("Exiting get_favorites.")   


@users_bp.route('/users/<guid>/favorites/<id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_favorite(user_info, guid, id):
    try:
        logger.info(f"Entering get_favorite {id}.")
        UserInfoRepo().find_by_guid(guid)

        item = UserFavoriteRepo().find_by_id(id)
        if not item:
            raise ResourceGetException("favorite")
        
        return Response("Success", status=200, mimetype='application/json')
    except (InvalidParamException, ResourceGetException, ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot get user favorite.')
    finally:
        logger.info("Exiting get_favorite.")


@users_bp.route('/users/<guid>/favorites', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def add_favorite(user_info, guid):
    try:
        logger.info(f"Entering add_favorite {guid}.")
        data = request.json
        user = request.user
        # logged in user name
        user_name = user['user_name']
        
        item = UserInfoRepo().find_by_guid(guid)

        fav = UserFavorite(data['name'], data['description'], None, data['type'], item.id, user_name, datetime.now())
        fav.validate()

        item = UserFavoriteRepo().save(fav)
        if not item:
            raise ResourceCreateException("favorite", data['name'])
        
        return Response("Success", status=201, mimetype='application/json')
    except (InvalidParamException, ResourceCreateException, ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot add user favorite.')
    finally:
        logger.info("Exiting add_favorite.")


@users_bp.route('/users/<guid>/favorites/<id>', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def update_favorite(user_info, guid, id):
    try:
        logger.info(f"Entering update_favorite {id}.")
        data = request.json
        user = request.user
        # logged in user name
        user_name = user['user_name']
        
        item = UserInfoRepo().find_by_guid(guid)

        fav = UserFavorite(data['name'], data['description'], None, data['type'], item.id, user_name, datetime.now())
        fav.validate()

        item = UserFavoriteRepo().modify(id, fav)
        if not item:
            raise ResourceUpdateException("favorite", data['name'])
        
        return Response("Success", status=200, mimetype='application/json')
    except (InvalidParamException, ResourceUpdateException, ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot update user favorite.')
    finally:
        logger.info("Exiting update_favorite.")


@users_bp.route('/users/<guid>/favorites/<fav_id>', methods=['DELETE'])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def delete_favorite(user_info, guid, fav_id):
    try:
        logger.info(f"Entering delete_favorite {guid} {fav_id}.")
        
        item = UserInfoRepo().find_by_guid(guid)

        # deleting user favorite from db
        UserFavoriteRepo().delete_by_user_and_id(item.id, fav_id)
        return Response("", status=204, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot delete user favorite.')
    finally:
        logger.info("Exiting delete_favorite.")


def __get_user_info(data, key_id, user_name):
    tag_ids = data['tag_ids']
    # get tag ids in the list
    tag_list = convert_to_int_list(tag_ids)
    tags = TagRepo().find_by_id_list(tag_list)

    usr_info = UserInfo(key_id, data['user_name'], tags, True, user_name, datetime.now())
    #validating config before saving it
    usr_info.validate()
    return usr_info


@users_bp.route('/users/<id>', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_user(user_info, id):
    try:
        logger.info(f"Entering update_user {id}.")
        data = request.json
        user = request.user
        # logged in user name
        user_name = user['user_name']
        
        item = UserInfoRepo().find_by_id(id)

        usr_info = __get_user_info(data, item.guid, user_name)

        # saving user info to DB
        item = UserInfoRepo().modify(id, usr_info, user_name)
        if not item:
            raise ResourceUpdateException("user", data['user_name'])

        return Response("Success", status=200, mimetype='application/json')
    except (InvalidParamException, InvalidUserDetailsException, ResourceUpdateException, ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot update user.')
    finally:
        logger.info("Exiting update_user.")
