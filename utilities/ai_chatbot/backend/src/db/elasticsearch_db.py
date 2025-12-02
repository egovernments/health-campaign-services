"""elasticsearch_db.py: Elasticsearch DB utility functions """

__author__ = "Umesh"
__copyright__ = "Copyright 2025, Prescience Decision Solutions"

import json
from src.db.db_base import DBBase
from src.config.logger_config import logger
from error_handler.custom_exception import DatabaseConnectionException, QueryRunException
from elasticsearch import Elasticsearch
from elasticsearch import exceptions

class ESDBWrapper(DBBase):

    def get_db_connection(self, conn):
        db_type = conn["db_type"]
        host = conn["host"]
        port = conn["port"]

        # default to False
        ssl_secure = conn.get("is_secure", False)

        # optional params, default is basic auth
        auth_type = conn.get("auth_type", "http_auth")
        username = conn.get("user_name", None)
        password = conn.get("password", None)
        api_key_id = conn.get("api_key_id", None)
        api_key_value = conn.get("api_key_value", None)
        
        if(port is None or port == ''):
            host_url = f"http://{host}"

            if ssl_secure:
                host_url = f"https://{host}"
        else:    
            host_url = f"http://{host}:{port}"

            if ssl_secure:
                host_url = f"https://{host}:{port}"

        try:
            if auth_type == "http_auth":
                es = Elasticsearch(
                        [host_url],
                        http_auth=(username, password),   # username, password
                        verify_certs=False                      # skip cert verification if using self-signed cert
                        #ssl_show_warn=False
                    )
            elif auth_type == "basic_auth":
                es = Elasticsearch(
                        [host_url],
                        basic_auth=(username, password),   # username, password
                        verify_certs=False                      # skip cert verification if using self-signed cert
                    )
            else:
                es = Elasticsearch(
                        [host_url],
                        api_key=(api_key_id, api_key_value),  # (api_key_id, api_key_value)
                        verify_certs=False                      # skip cert verification if using self-signed cert
                    )
            
            # to make sure connection is established
            es.info()
            return es
        except Exception as ex:
            logger.error(ex.args)
            raise DatabaseConnectionException(f'Cannot get {db_type} db connection. {ex.args}')


    def test_connection(self, conn):
        try:
            es = self.get_db_connection(conn)
            info = es.info()
            logger.debug("Connected successfully to Elasticserach server.")
            return True
        except Exception as ex:
            logger.error(f"Connection failed to Elasticsearch server. {ex.args}")
            return False


    def exec_query_and_read_data_as_json(self, data, conn_data, query):
        try:
            es = self.get_db_connection(conn_data)
            #index_names = data['index_name']
            query = json.loads(query)
            index_names = query.pop("index")
            logger.debug(f"Elasticsearch index is {index_names}")
            indices = [item.strip() for item in index_names.split(",")]
            response = es.search(index=indices, body=query)
            #logger.debug(f"DB Response is {response}")
            return response
        except (DatabaseConnectionException) as ex:
            logger.error(f"Error in connecting Elasticsearch DB. {ex.args}")
            raise ex
        except Exception as ex:
            logger.error(f"Error in reading data from Elasticsearch index. {ex.args}")
            raise QueryRunException
    

    def get_metadata(self, data, conn_data):
        try:
            es = self.get_db_connection(conn_data)
            index_names = data['index_name']
            indices = [item.strip() for item in index_names.split(",")]
            mappings = es.indices.get_mapping(index=indices)
            return mappings
        except Exception as ex:
            logger.error(f"Error in getting index metadata. {ex.args}")
            return None
    

    def validate_query(self, conn, query):
        try:
            es = self.get_db_connection(conn)
            #index_name = data['index_name']         
            full_body = json.loads(query)
            index_name = full_body.pop("index")

            # first checking for index
            if not es.indices.exists(index=index_name):
                return False, f'Index {index_name} does not exist.'
        
            if "aggs" in full_body and "query" not in full_body:
                # setting size to 0 to avoid retrieving data
                vdict = full_body.get("aggs")
                response = es.search(index=index_name, size=0, query={"match_all": {}}, aggs=vdict)
            else:
                vdict={"query":full_body.get("query")}
                response = es.indices.validate_query(index=index_name, body=vdict, explain=True)
                return response['valid'], 'Success'
    
            return True, 'Success'
        except exceptions.ElasticsearchException as e:
            # Convert to dict to safely access JSON fields
            error_data = e.info if hasattr(e, "info") else None
            try:
                if error_data and "error" in error_data:
                    reason = error_data["error"]["root_cause"][0].get("reason", "Unknown reason")
                    return False, reason
                else:
                    return False, 'Unexpected error'
            except Exception as ex:
                return False, 'Unexpected error'
        except DatabaseConnectionException as ex:
            logger.error(f"DB connection error. {ex.args}")
            raise ex
        except Exception as ex:
            logger.error(f"Unexpected error. {ex.args}")
            return False, str(ex)