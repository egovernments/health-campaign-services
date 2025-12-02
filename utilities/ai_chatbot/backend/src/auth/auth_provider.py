"""auth_provider.py: File contains logic for auth providers """


__author__ = "Umesh"
__copyright__ = "Copyright 2024, Prescience Decision Solutions"


import os
import functools
from enum import Enum
from flask import request
from functools import wraps
from .error_handler.auth_exception import InvalidAuthProvider, InvalidTokenException, InactiveTokenException, MissingRequiredRolesException, NoTokenProvidedException
from src.auth.egov_auth_provider import EGovAuthProvider
from src.auth.base_auth_provider import UserRoles
from src.config.logger_config import logger

    
class AuthProviderType(Enum):
    EGOV='EGov'


def get_auth_provider():
    auth_provider = os.getenv('AUTH_PROVIDER')
    # defaulting to Keycloak for auth provider
    if ((auth_provider is None) or (auth_provider == '') or (auth_provider == AuthProviderType.EGOV.value)):
        return EGovAuthProvider()
    
    raise InvalidAuthProvider


def __get_token():
    token = None
    if request.method == "GET":
        token = request.headers.get('auth-token')
    else:
        data = request.json
        if data is not None:
            token = data['RequestInfo']['authToken']
    
    if not token:
        raise NoTokenProvidedException
    
    return token


def __get_user_info(token):
    user = get_auth_provider().check_token(token)
    if(user is None):
        raise InvalidTokenException
    
    return user


def __check_user_roles(required_roles, user_roles):
    for user_role in user_roles:
        if user_role in required_roles:
            logger.debug(f"Required role {required_roles}, role found in user {user_roles}.")
            return True
    
    raise MissingRequiredRolesException(required_roles)


def check_user_role(required_roles=[]):
    def decorator(func):

        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            user_info = None
            try:
                access_token = __get_token()
                user_info = __get_user_info(access_token)
                roles = user_info['roles']
                __check_user_roles(required_roles, roles)
                user_info['is_admin'] = _is_admin(roles)
                request.user = user_info
            except (NoTokenProvidedException, InactiveTokenException, InvalidTokenException, MissingRequiredRolesException) as ex:
                logger.error(ex.args)
                return {'error': ex.message}, ex.code
            except Exception as ex:
                logger.error(ex.args)
                return {'error': 'Invalid user role.'}, 403
            
            return func(user_info=user_info, *args, **kwargs)
        return wrapper
    return decorator


def _is_admin(role_list):
    for role_name in role_list:
        if(role_name == UserRoles.ADMIN.value):
            return True
    
    return False
