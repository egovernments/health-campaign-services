import concurrent.futures
import json
import os
import sys
import time
import warnings
import pandas as pd
import requests
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--campaign_identifier', required=True,
                    help='Campaign identifier (can be campaignNumber or projectTypeId)')
parser.add_argument('--identifier_type', default='campaignNumber',
                    help='Type of identifier: "campaignNumber" or "projectTypeId"')
parser.add_argument('--start_date', default='')
parser.add_argument('--end_date', default='')
parser.add_argument('--file_name', required=True)
args = parser.parse_args()

CAMPAIGN_IDENTIFIER = args.campaign_identifier  # Can be campaignNumber or projectTypeId (UUID)
IDENTIFIER_TYPE = args.identifier_type  # "campaignNumber" or "projectTypeId"
START_DATE = args.start_date
END_DATE = args.end_date
FILE_NAME = args.file_name

file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.append(file_path)

from REPORTS_GENERATION.COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from COMMON_UTILS.common_utils import get_resp

ES_HOUSEHOLD_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/household-index-v1/_search"
ES_INDIVIDUAL_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/individual-index-v1/_search"
ES_HHM_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/household-member-index-v1/_search"

max_retries = 60
retry_delay = 5

if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "projectTypeId.keyword"
    print(f"📋 Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "campaignNumber.keyword"
    print(f"📋 Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

# Filter out the specific warning messages by message
warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")



hh_id_vs_details_duplicates = {}


lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

def fetch_all_village_names():
    village_names = set()
    after_key = None

    while True:
        query = {
            "size": 0,
            "query": {
                "bool": {
                    "must": [
                        {
                            "range": {
                                "Data.@timestamp": {
                                    "gte": gteTime,
                                    "lte": lteTime
                                }
                            }
                        },
                        {
                            "term": {
                                CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER
                            }
                        }
                    ]
                }
            },
            "aggs": {
                "villages": {
                    "composite": {
                        "size": 1000,
                        "sources": [
                            {
                                "villageName": {
                                    "terms": {
                                        "field": "Data.boundaryHierarchyCode.village.keyword"
                                    }
                                }
                            }
                        ]
                    }
                }
            }
        }

        # pagination using after_key
        if after_key:
            query["aggs"]["villages"]["composite"]["after"] = after_key

        response = get_resp(
            url=ES_HHM_INDEX,
            data=query,
            es=True
        )

        resp_json = response.json()
        buckets = resp_json["aggregations"]["villages"]["buckets"]

        for bucket in buckets:
            village_names.add(bucket["key"]["villageName"])

        after_key = resp_json["aggregations"]["villages"].get("after_key")
        if not after_key:
            break

    return list(village_names)


def fetch_household_member_by_village(village): 
    individual_clientreferenceids = set()
    after_key = None

    while True:
        query = {
            "query": {
                "bool": {
                    "must": [
                        {
                            "range": {
                                "Data.@timestamp": {
                                    "gte": gteTime,
                                    "lte": lteTime
                                }
                            }
                        },
                        {
                            "term": {
                                CAMPAIGN_FILTER_FIELD: CAMPAIGN_IDENTIFIER
                            }
                        },
                        {
                            "term": {
                                "Data.householdMember.isHeadOfHousehold": {
                                    "value": True
                                }
                            }
                        },
                        {
                            "term": {
                                "Data.boundaryHierarchyCode.village.keyword": village
                            }
                        }
                    ]
                }
            },
            "aggs": {
                "individuals": {
                    "composite": {
                        "size": 1000,
                        "sources": [
                            {
                                "individual_clientreferenceids": {
                                    "terms": {
                                        "field": "Data.householdMember.individualClientReferenceId.keyword"
                                    }
                                }
                            }
                        ]
                    }
                }
            }
        }

        # pagination using after_key
        if after_key:
            query["aggs"]["individuals"]["composite"]["after"] = after_key

        response = get_resp(
            url=ES_HHM_INDEX,
            data=query,
            es=True
        )

        resp_json = response.json()
        buckets = resp_json["aggregations"]["individuals"]["buckets"]

        for bucket in buckets:
            individual_clientreferenceids.add(bucket["key"]["individual_clientreferenceids"])

        after_key = resp_json["aggregations"]["individuals"].get("after_key")
        if not after_key:
            break

    return list(individual_clientreferenceids)

def fetch_duplicates_individual(individual_clientreferenceids):
    print(f"{len(individual_clientreferenceids)} number of individualClientReferenceIds provided to fetch individuals") 
    duplicate_key_value = {}
    duplicate_individual_clientreferenceids = {}

    # Split individualClientReferenceIds into chunks of 10000 or less
    chunk_size = 3000
    for i in range(0, len(individual_clientreferenceids), chunk_size):
        chunk = individual_clientreferenceids[i:i + chunk_size]
        query = {
            "size": 10000,
            "query": {
                "terms": {
                    "clientReferenceId.keyword": chunk
                }
            },
            "_source": [
                "clientReferenceId",
                "name.givenName",
                "name.familyName",
                "gender",
                "dateOfBirth",
                "mobileNumber",
                "address.latitude",
                "address.longitude",
                "address.locality.code"
            ]
        }
        docs = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()["hits"]["hits"]
        for item in docs:
            src = item["_source"]

            given_name = src.get("name", {}).get("givenName") or ""
            family_name = src.get("name", {}).get("familyName") or ""
            full_name = (given_name + family_name).strip().lower()


            address_list = src.get("address", [])
            address = address_list[0] if address_list else {}

            boundary_code = address.get("locality", {}).get("code", "")

            key = full_name + boundary_code + src.get("gender", "")

            if key in duplicate_key_value:
                duplicate_individual_clientreferenceids[src.get("clientReferenceId")] = {
                    "latitude" : address.get("latitude", 0),
                    "longitude" : address.get("longitude", 0),
                    "family_name" : family_name,
                    "given_name" : given_name,
                    "gender" : src.get("gender", "")
                }
                ind_details = duplicate_key_value[key]
                if ind_details.get("count") == 1:
                    duplicate_individual_clientreferenceids[ind_details.get("clientreferenceid")] = {
                        "latitude": ind_details.get("latitude", 0),
                        "longitude": ind_details.get("longitude", 0),
                        "family_name": ind_details.get("family_name", ""),
                        "given_name": ind_details.get("given_name", ""),
                        "gender": ind_details.get("gender", "")
                    }
                ind_details["count"] += 1
            else :
                duplicate_key_value[key] = {
                    "count" : 1,
                    "clientreferenceid" : src.get("clientReferenceId"),
                    "latitude" : address.get("latitude", 0),
                    "longitude" : address.get("longitude", 0),
                    "family_name" : family_name,
                    "given_name" : given_name,
                    "gender" : src.get("gender", "")
                }

    # print(data)
    print(f"{len(duplicate_individual_clientreferenceids)} number of duplicate individuals")
    return duplicate_individual_clientreferenceids


def search_in_hh_for_head_duplicates(ind_list_to_fetch):
    if not ind_list_to_fetch:
        return {}

    query = {
        "query": {
            "bool": {
                "must": [
                    {
                        "term": {
                            "Data.householdMember.isHeadOfHousehold": {
                                "value": True
                            }
                        }
                    },
                    {
                        "terms": {
                            "Data.householdMember.individualClientReferenceId.keyword": ind_list_to_fetch
                        }
                    }
                ]
            }
        }
    }

    hhm_head_resp = get_resp(ES_HHM_INDEX, query, True).json()["hits"]["hits"]
    if not hhm_head_resp:
        return {}

    hh_id_list = [
        obj["_source"]["Data"]["householdMember"]["householdClientReferenceId"]
        for obj in hhm_head_resp
    ]

    hh_query = {
        "query": {
            "terms": {
                "Data.household.clientReferenceId.keyword": hh_id_list
            }
        }
    }

    hh_resp = get_resp(ES_HOUSEHOLD_INDEX, hh_query, True).json()["hits"]["hits"]

    hh_id_vs_member_count = {
        obj["_source"]["Data"]["household"]["clientReferenceId"]: {
        "member_count" : obj["_source"]["Data"]["household"]["memberCount"],
        "boundaryHierarchy" : obj["_source"]["Data"]["boundaryHierarchy"],
        "user_name" : obj["_source"]["Data"]["userName"]
        }
        for obj in hh_resp
    }

    result = {}
    for obj in hhm_head_resp:
        hh_id = obj["_source"]["Data"]["householdMember"]["householdClientReferenceId"]
        ind_id = obj["_source"]["Data"]["householdMember"]["individualClientReferenceId"]

        if hh_id in hh_id_vs_member_count:
            result[ind_id] = {
                "hh_id": hh_id,
                "mem_count": hh_id_vs_member_count[hh_id]["member_count"],
                "boundary_hierarchy" : hh_id_vs_member_count[hh_id]["boundaryHierarchy"],
                "user_name" : hh_id_vs_member_count[hh_id]["user_name"]
            }

    return result


mapping_hhid_indid = []
final_list_to_excel = []


def prepare_ind_list_and_check(duplicate_individuals):
    """
    duplicate_individuals = {
        ind_id: {
            "latitude": ...,
            "longitude": ...,
            "family_name": ...,
            "given_name": ...,
            "gender": ...
        }
    }
    """

    if not duplicate_individuals:
        return

    individual_ids = list(duplicate_individuals.keys())

    # Only HH-related lookup
    returned_ind_id_obj = search_in_hh_for_head_duplicates(individual_ids)

    if not returned_ind_id_obj:
        return

    for ind_id, ind_data in duplicate_individuals.items():
        if ind_id not in returned_ind_id_obj:
            continue

        hh_info = returned_ind_id_obj[ind_id]
        boundaryInfo = hh_info["boundary_hierarchy"]

        dummy = {
            "familyName": ind_data.get("family_name"),
            "givenName": ind_data.get("given_name"),
            "gender": ind_data.get("gender"),

            "Village": boundaryInfo.get("village", ""),
            "Province": boundaryInfo.get("province", ""),
            "District": boundaryInfo.get("district", ""),
            "Locality": boundaryInfo.get("locality", ""),
            "Administrative Province": boundaryInfo.get("administrativeProvince", ""),

            "latitude": ind_data.get("latitude"),
            "longitude": ind_data.get("longitude"),

            "ind_id": ind_id,
            "hh_id": hh_info["hh_id"],
            "mem_count": hh_info["mem_count"],

            "userName": hh_info.get("user_name", "TESTING_USER")
        }

        final_list_to_excel.append(dummy)
        hh_id_vs_details_duplicates[hh_info["hh_id"]] = ind_id


def run_duplicate_detection_pipeline():
    villages = fetch_all_village_names()
    print(f"Total villages found: {len(villages)}")

    for village in villages:
        try:
            # 1. Fetch HoH individual IDs for this village
            individual_ids = fetch_household_member_by_village(village)
            if not individual_ids:
                continue

            # 2. Find duplicate individuals + metadata (per village)
            duplicate_individuals = fetch_duplicates_individual(individual_ids)
            if not duplicate_individuals:
                continue

            # 3. HH + Household enrichment (per village)
            prepare_ind_list_and_check(duplicate_individuals)

            print(
                f"Village {village}: "
                f"{len(duplicate_individuals)} duplicates processed"
            )

        except Exception as e:
            print(f"Error processing village {village}: {e}")

    print(f"Total final rows: {len(final_list_to_excel)}")


run_duplicate_detection_pipeline()

df = pd.DataFrame(final_list_to_excel)

df.to_excel(f"{FILE_NAME}.xlsx", index=False)