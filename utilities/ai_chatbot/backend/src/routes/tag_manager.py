"""tag_manager.py: File contains logic for tags api """


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"

import json
from datetime import datetime
from flask import request, Blueprint, Response
from src.config.logger_config import logger
from src.auth.auth_provider import check_user_role
from src.auth.base_auth_provider import UserRoles
from src.models.tag import Tag
from src.repos.tag_repo import TagRepo
from error_handler.custom_exception import ResourceCreateException, ResourceUpdateException, ResourceNotFoundException, ResourceGetException, \
    InvalidParamException
from src.utils.util import AlchemyHelper, CustomEncoder

tags_bp = Blueprint('tags', __name__)


# This method executes before any API request
@tags_bp.before_request
def before_request():
    logger.debug('Entering tags service.')


# This method executes after every API request.
@tags_bp.after_request
def after_request(response):
    logger.debug('Exiting tags service.')
    return response


@tags_bp.route('/tags', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_all_tags(user_info):
    try:
        logger.info("Entering get_all_tags.")
        page = request.args.get('page', type=int, default=-1)
        per_page = request.args.get('per_page', type=int, default=-1)
        items = TagRepo().find_all(page, per_page)
        if not items:
            return Response("", status=200, mimetype='application/json')

        items = AlchemyHelper(obj=items).clean()
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get tag(s).')
    finally:
        logger.info("Exiting get_all_tags.")


@tags_bp.route('/tags/<id>', methods=['GET'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def get_tag(user_info, id):
    try:
        logger.info(f"Entering get_tag {id}.")
        item = TagRepo().find_by_id(id)
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceGetException(f'Cannot get tag with id {id}.')
    finally:
        logger.info("Exiting get_tag.")


def __get_tag(data, user_name):
    name = data['name'].strip()
    desc = data['description'].strip()
    is_active = data['is_active']
        
    tag = Tag(name, desc, user_name, is_active, datetime.now())
    #validating config before saving it
    tag.validate()
    return tag


@tags_bp.route('/tags', methods=['POST'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def create_tag(user_info):
    try:
        logger.info("Entering create_tag.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid data.')
        
        user = request.user
        user_name = user['user_name']
        name = data['name']
        
        tag = __get_tag(data, user_name)

        item = TagRepo().save(tag)
        if not item:
            raise ResourceCreateException("tag", name)
        
        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=201, mimetype='application/json')
    except (InvalidParamException, ResourceCreateException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise ResourceCreateException('tag')
    finally:
        logger.info("Exiting create_tag.")


@tags_bp.route('/tags/<id>', methods=['PUT'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def update_tag(user_info, id):
    try:
        logger.info(f"Entering update_tag {id}.")
        data = request.json
        if(data is None or data == ''):
            raise Exception('Invalid tag data.')
        
        user = request.user
        user_name = user['user_name']
        name = data['name']
        
        tag = __get_tag(data, user_name)
        tag.modified_by = user_name
        
        item = TagRepo().modify(id, tag)
        if not item:
            raise ResourceUpdateException("tag", name)

        item = AlchemyHelper(obj=item).clean()
        jsonstr = json.dumps(item, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except (InvalidParamException, ResourceNotFoundException, ResourceUpdateException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot update tag. {ex.args}')
    finally:
        logger.info("Exiting update_tag.")


@tags_bp.route('/tags/<id>', methods=['DELETE'])
@check_user_role(required_roles=[UserRoles.ADMIN.value])
def delete_tag(user_info, id):
    try:
        logger.info(f"Entering delete_tag {id}.")
        TagRepo().delete_by_id(id)
        return Response("", status=204, mimetype='application/json')
    except (ResourceNotFoundException) as ex:
        logger.error(ex.args)
        raise ex
    except Exception as ex:
        logger.error(ex.args)
        raise Exception(f'Cannot delete tag.')
    finally:
        logger.info("Exiting delete_tag.")
