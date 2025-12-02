"""base_auth_provider.py: Base auth provider class """


__author__ = "Umesh"
__copyright__ = "Copyright 2023, Prescience Decision Solutions"

import os
import requests
from src.config.logger_config import logger
from src.auth.base_auth_provider import AuthProvider, UserRoles
from src.auth.error_handler.auth_exception import LoginException, InvalidTokenException


# base class for auth
class EGovAuthProvider(AuthProvider):

    def __init__(self):
        self.auth_endpoint = os.getenv('EGOV_AUTH_ENDPOINT')
        self.tenant_id = os.getenv('EGOV_TENANT_ID', 'dev')
        self.user_type = os.getenv('EGOV_USER_TYPE', 'EMPLOYEE')
        self.usr_srv_endpoint = os.getenv('EGOV_USER_SRV_ENDPOINT')


    def get_token(self, user_name, password):
        try:
            header = {
                        'authorization': 'Basic ZWdvdi11c2VyLWNsaWVudDo=', 
                        'Content-Type': 'application/x-www-form-urlencoded'
                    }
            
            data = {
                    'username':user_name,
                    'password':password,
                    'grant_type':'password',
                    'scope':'read',
                    'tenantId':self.tenant_id,
                    'userType':self.user_type
                }
            
            response = requests.post(self.auth_endpoint, headers=header, data=data)
            if response.status_code > 200:
                logger.error(f"Invalid username or password. {response.text}")
                raise LoginException(f"Invalid username or password.")
            
            response_json = response.json()
            access_tkn = response_json['access_token']
            refresh_tkn = response_json['refresh_token']
            logger.debug(f"Token data = {response_json}")
            # logger.debug(f"Token = {access_tkn}")
            # logger.debug(f"Refresh Token = {refresh_tkn}")
            user_guid = response_json['UserRequest']['uuid']
            name = response_json['UserRequest']['name']
            emailId = response_json['UserRequest']['emailId']
            user_roles = response_json['UserRequest']['roles']
            return (access_tkn, refresh_tkn, user_guid, name, emailId, user_roles)
        except (LoginException) as ex:
            logger.error(ex.args)
            raise ex
        except Exception as ex:
            logger.error(ex.args)
            raise ex
        

    def refresh_token(self, token:str):
        pass
    

    def check_token(self, token:str):
        try:
            # TODO
            # Remove this code once we deploy it in eGov environment
            # this is only for dev environment testing
            if(self.usr_srv_endpoint is None or self.usr_srv_endpoint == ''):
                return self.mock_user()
            
            # this is internal user service which is not exposed outside of gateway
            endpoint = f"{self.usr_srv_endpoint}?access_token={token}"
            response = requests.post(endpoint)
            if response.status_code > 200:
                logger.error(f"Invalid token or service response. {response.text}")
                raise InvalidTokenException
            
            response_json = response.json()
            #TODO
            # get user info from response
            user = {
                "roles": None,
                "user_name": None,
                "user_id": None
            }
            return user
        except Exception as ex:
            logger.error(ex.args)
            raise ex

    # Note
    # this is only temporary method, once API is provided then we should remove it
    def mock_user(self):
        user = {
                "roles": [UserRoles.ADMIN.value],
                "user_name": 'Chatbot-Admin-1',
                "user_id": '4fbd163e-da1e-4368-9893-2553de9f6090'
            }
        return user
