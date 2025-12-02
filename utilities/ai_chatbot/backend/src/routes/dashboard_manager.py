"""dashboard_manager.py: File contains logic for Superset wrapper and related actions."""

__author__ = "Praful Nigam"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import json
from flask import Blueprint, Response, request
from src.auth.auth_provider import check_user_role
from src.repos.userinfo_repo import UserInfoRepo
from src.auth.base_auth_provider import UserRoles
from src.helpers.superset_helper import get_dashboards, get_guest_token_by_id
from src.utils.util import CustomEncoder
from src.config.logger_config import logger

dashboards_bp = Blueprint('dashboards', __name__)


@dashboards_bp.route("/dashboards", methods=["GET"])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_all_dashboards(user_info):
    try:
        logger.info("Entering get_all_dashboards.")
        
        user = request.user
        is_admin = user['is_admin']
        user_id = user['user_id']

        # for admin we are getting all dashboards from Superset
        final_tag_names = None
        if not is_admin:
            items = UserInfoRepo().find_tags_by_user_guid(user_id)
            if not items:
                return Response("", status=200, mimetype='application/json')

            user_tag_names = [item.name for item in items]
            if(user_tag_names is None or len(user_tag_names) <= 0):
                return Response("", status=200, mimetype='application/json')
            
            final_tag_names = user_tag_names
            
        # when final_tag_names is None or empty, we need to get all dashboards
        items = get_dashboards(final_tag_names)
        jsonstr = json.dumps(items, cls=CustomEncoder)
        return Response(jsonstr, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot get user dashboards.")
    finally:
        logger.info("Exiting get_all_dashboards.")


@dashboards_bp.route("/dashboards/<guid>/guestToken", methods=["GET"])
@check_user_role(required_roles=[UserRoles.ADMIN.value, UserRoles.USER.value])
def get_dashboard_guest_token(user_info, guid):
    try:
        logger.info(f"Entering get_dashboard_guest_token {guid}.")
        token = get_guest_token_by_id(guid)
        return Response(token, status=200, mimetype='application/json')
    except Exception as ex:
        logger.error(ex.args)
        raise Exception("Cannot get dashboard guest token.")
    finally:
        logger.info("Exiting get_dashboard_guest_token.")
