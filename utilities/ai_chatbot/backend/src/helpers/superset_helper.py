"""superset_helper.py: Superset wrapper"""

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"


import os
import json
import urllib.parse
from src.utils.util import make_http_request
from src.classes.dashboard import DashboardInfo
from src.config.logger_config import logger

SERVER_HOST = os.getenv('SUPERSET_SERVER')
SERVER_PORT = os.getenv('SUPERSET_PORT')
SUPERSET_API_URL = f'http://{SERVER_HOST}:{SERVER_PORT}/api/v1'

ADMIN_USER = os.getenv("SUPERSET_ADMIN") 
ADMIN_USER_PWD = os.getenv("SUPERSET_PASSWORD")

SUPERSET_USER_PROVIDER = "db"


def __make_api_request(url, method="GET", headers=None, payload=None, query=None):
    return make_http_request(url, method, headers, payload, query)


def __get_superset_token():
    token_response = __make_api_request(url=f"{SUPERSET_API_URL}/security/login",
        method="POST",
        payload={
            "username": ADMIN_USER,
            "password": ADMIN_USER_PWD,
            "provider": SUPERSET_USER_PROVIDER,
            "refresh": True,
        },
    )

    if token_response and "access_token" in token_response:
        return token_response["access_token"]
    else:
        logger.error("Failed to retrieve Superset token.")
        raise Exception("Cannot get superset access token.")
    

def get_dashboards(tags):
    try:
        logger.debug(f"Entering get_dashboards {tags}.")
        access_token = __get_superset_token()

        url = f"{SUPERSET_API_URL}/dashboard/"
        headers = {"Authorization": f"Bearer {access_token}"}

        all_dashboards = []
        if(tags is None or len(tags) <= 0):
            response = __make_api_request(url, method="GET", headers=headers)
            if(response is not None and response["result"] is not None):
                all_dashboards = response["result"]
        else:
            for tag in tags:
                logger.debug(f"Getting dashboards for tag {tag}.")
                json_data = {
                        "filters": [
                            {"col": "tags", "opr": "dashboard_tags", "value": tag}
                        ]
                    }
                
                query_params = {"q": json.dumps(json_data)}
                url_encoded_params = urllib.parse.urlencode(query_params)
                
                # we don't have filter to pass all tags together so need to do it one by one
                response = __make_api_request(url, method="GET", headers=headers, query=url_encoded_params)
                #logger.debug(response)
                if(response is not None and response["result"] is not None):
                    all_dashboards.extend(response["result"])
        
        #total_dash = len(all_dashboards)
        #logger.debug(f'Total dashboards = {total_dash}')

        dashboards_info = []
        for dashboard in all_dashboards:
            id = dashboard['id']
            name = dashboard['dashboard_title']
            
            logger.debug(f"Dashboard Id = {id}, Name = {name}")

            embed_dashboard = get_embedded_dashboard_by_id(id, access_token)
            if(embed_dashboard is not None):
                embed_guid = embed_dashboard["result"]["uuid"]
                ds_info = DashboardInfo(id, name, embed_guid)
                if not any(ds_info.embed_guid == tmp_obj.embed_guid for tmp_obj in dashboards_info):
                    dashboards_info.append(ds_info)

        return dashboards_info
    except Exception as ex:
        logger.error(f"Failed to retrieve Superset dashboards. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting get_dashboards.")


def get_embedded_dashboard_by_id(id, access_token=None):
    try:
        logger.debug(f"Entering get_embedded_dashboard_by_id {id}.")
        url = f"{SUPERSET_API_URL}/dashboard/{id}/embedded"
        headers = {"Authorization": f"Bearer {access_token}"}
        response = __make_api_request(url, method="GET", headers=headers)
        logger.debug(response)
        return response
    except Exception as ex:
        logger.error(f"Failed to retrieve Superset embedded dashboard. {ex.args}")
        return None
    finally:
        logger.debug("Exiting get_embedded_dashboard_by_id.")


def get_guest_token_by_id(guid):
    try:
        logger.debug(f"Entering get_guest_token_by_id {guid}.")
        access_token = __get_superset_token()

        url = f"{SUPERSET_API_URL}/security/guest_token"
        headers = {"Authorization": f"Bearer {access_token}"}
        
        payload = {
            "resources": [{"id": str(guid), "type": "dashboard"}],
            "rls": [],
            #"user" : "guest_user",
            #"user": {"first_name": "guest", "last_name": "user", "username": "guest_user"},
            "user": {"username": "guest_user"}
        }

        response = __make_api_request(url, method="POST", payload=payload, headers=headers)
        #logger.debug(response)
        if response and "token" in response:
            return response["token"]

        raise Exception('Cannot get guest token.')
    except Exception as ex:
        logger.error(f"Failed to retrieve Superset guest token. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting get_guest_token_by_id.")


def create_tag(name, description):
    try:
        logger.debug(f"Entering create_tag {name}.")
        access_token = __get_superset_token()

        url = f"{SUPERSET_API_URL}/tag/"
        headers = {"Authorization": f"Bearer {access_token}","Content-Type": "application/json"}
        
        payload = {
            "description": description,
            "name": name,
            "objects_to_tag": []
        }

        response = __make_api_request(url, method="POST", payload=payload, headers=headers)
        logger.debug(response)
    except Exception as ex:
        logger.error(f"Failed to create Superset tag {name}. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting create_tag.")


def update_tag(id, name, description):
    try:
        logger.debug(f"Entering update_tag {id} {name}.")
        access_token = __get_superset_token()

        url = f"{SUPERSET_API_URL}/tag/{id}"
        headers = {"Authorization": f"Bearer {access_token}","Content-Type": "application/json"}
        
        payload = {
            "description": description,
            "name": name,
            "objects_to_tag": []
        }

        response = __make_api_request(url, method="PUT", payload=payload, headers=headers)
        logger.debug(response)
    except Exception as ex:
        logger.error(f"Failed to update Superset tag {name}. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting update_tag.")


def get_tag_by_name(name):
    try:
        logger.debug(f"Entering get_tag_by_name {name}.")
        access_token = __get_superset_token()

        url = f"{SUPERSET_API_URL}/tag/"
        headers = {"Authorization": f"Bearer {access_token}"}
        
        json_data = {
                        "filters": [
                            {"col": "name", "opr": "eq", "value": name}
                        ]
                    }

        query_params = {"q": json.dumps(json_data)}
        url_encoded_params = urllib.parse.urlencode(query_params)
        
        response = __make_api_request(url, method="GET", headers=headers, query=url_encoded_params)
        logger.debug(response)
        if(response and response["result"]):
            tag_res = response["result"]
            return {"name" : tag_res['name'], "id" : tag_res['id'], "description" : tag_res['description']}
        
        return None
    except Exception as ex:
        logger.error(f"Failed to get Superset tag {name}. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting get_tag_by_name.")


def get_tag(id):
    try:
        logger.debug(f"Entering get_tag {id}.")
        access_token = __get_superset_token()

        url = f"{SUPERSET_API_URL}/tag/{id}"
        headers = {"Authorization": f"Bearer {access_token}"}
                
        response = __make_api_request(url, method="GET", headers=headers)
        logger.debug(response)
        if(response and response["result"]):
            tag_res = response["result"]
            return {"name" : tag_res['name'], "id" : tag_res['id'], "description" : tag_res['description']}
        
        return None
    except Exception as ex:
        logger.error(f"Failed to get Superset tag {id}. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting get_tag.")


def delete_tag(id):
    try:
        logger.debug(f"Entering delete_tag {id}.")
        access_token = __get_superset_token()

        url = f"{SUPERSET_API_URL}/tag/{id}"
        headers = {"Authorization": f"Bearer {access_token}"}
        
        response = __make_api_request(url, method="DELETE", headers=headers)
        logger.debug(response)
    except Exception as ex:
        logger.error(f"Failed to delete Superset tag {id}. {ex.args}")
        raise ex
    finally:
        logger.debug("Exiting delete_tag.")