import warnings
import requests
import json
import time
import copy
import datetime
from openpyxl import Workbook
import os
import sys
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.append(file_path)
file_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
sys.path.append(file_path)

print("file_path",file_path)
ES_PROJECT_SEARCH="http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/project-index-v1/_search"
LOCALIZATION_URL = "https://burundi-uat.digit.org/localization/messages/v1/_search?locale=fr_BI&tenantId=bi&module=rainmaker-boundary-admin"
HEADERS_JSON = {"Content-Type": "application/json"}
HEADERS_ES = {
    "Content-Type": "application/json",
    "Authorization": "Basic ZWxhc3RpYzpTUTAwTmNrZ2ZnTms3VkZISE9ZbFRYSk0="
}

max_retries = 1
retry_delay = 5
def get_resp(url, data, es=False):
    # ZWxhc3RpYzpHZDE5OWZubzkyV29pUW52RkVOVzNLUWo=
    failed = False
    for _ in range(max_retries):
        if failed:
            print(_, f" retry count where max retry count is {max_retries}")
        try:
            print(url, data)
            headers = HEADERS_ES if es else HEADERS_JSON
            response = requests.post(url, data=json.dumps(data), headers=headers, verify=False)
            
            print(response)
            if response.status_code == 200:
                print(response)
                return response
        except requests.exceptions.ConnectionError:
            print(f"Connection error. Retrying in {retry_delay} seconds...")
            print(f"Retrying connecting to {url}")
            failed = True
        time.sleep(retry_delay)


localityVsBoundaryMap = {}
def fetch_boundary_info():
    project_query = {
  "size": 0,
  "aggs": {
    "group_by_locality": {
      "terms": {
        "field": "Data.localityCode.keyword",
        "size": 1000
      },
      "aggs": {
        "boundaryHierarchy": {
          "top_hits": {
            "size": 1,
            "_source": ["Data.boundaryHierarchy"]
          }
        }
      }
    }
  }
}
    resp = get_resp(ES_PROJECT_SEARCH, project_query, True).json()["aggregations"]["group_by_locality"]["buckets"]
    for bucket in resp:
        boundaryHierarchy = bucket["boundaryHierarchy"]["hits"]["hits"][0]["_source"]["Data"]["boundaryHierarchy"]
        if bucket["key"] not in localityVsBoundaryMap:
            localityVsBoundaryMap[bucket["key"]] = [
                boundaryHierarchy.get("country"), boundaryHierarchy.get("province"), boundaryHierarchy.get("district"), 
                boundaryHierarchy.get("POSTADMINISTRATIVE"), boundaryHierarchy.get("locality"), 
                boundaryHierarchy.get("village")
                ]
    print("kfkfh",localityVsBoundaryMap)
fetch_boundary_info()
with open("BOUNDARY_bi_prod.json", "w") as file:
    json.dump(localityVsBoundaryMap, file, indent=4)

def fetch_localization_data():
    payload = {
        "locale": "fr_BI",
        "tenantId": "bi",
        "module": "rainmaker-boundary-admin"
    }
    
    response = get_resp(LOCALIZATION_URL,None).json()
    if response:
        with open("localization_data.json", "w", encoding="utf-8") as file:
          json.dump(response, file, indent=4, ensure_ascii=False)
        print("Localization data saved successfully.")

fetch_localization_data()