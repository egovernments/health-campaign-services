import pandas as pd
from elasticsearch import Elasticsearch
from geopy.distance import geodesic
import concurrent.futures
import time
import os
import sys
import argparse
import urllib3

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


ES_PROJECT_TASK_SEARCH = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/project-task-index-v1/_search"

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
# es = Elasticsearch(["https://localhost:9200"], http_auth=('elastic', ELASTIC_CLIENT_PASSWORD), verify_certs=False)


file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)
from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from COMMON_UTILS.common_utils import get_resp

DISTANCE = 5
NEARBY_POINTS_LIMIT = 6

s_time = time.time()

lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"📋 Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"📋 Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")


def fetch_all_active_users():
    users_query = {
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
                        "term" : {
                            CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                        }
                    }
                ]
            }
        },
        "aggs": {
            "NAME": {
                "terms": {
                    "field": "Data.userName.keyword",
                    "size": 10000
                }
            }
        }
    }

    users_resp = get_resp(ES_PROJECT_TASK_SEARCH, users_query, True).json()

    users = [item['key'] for item in users_resp['aggregations']['NAME']['buckets']]
    print("FETCHED ACTIVE USERS")
    return users


user_list = fetch_all_active_users()


def fetch_distance(point1, point2):
    lat1 = point1[1]
    lat2 = point2[1]
    lon1 = point1[0]
    lon2 = point2[0]
    distance = geodesic((lat1, lon1), (lat2, lon2)).meters
    return distance


def points_within_range(point, points, range_distance):
    nearby_points = [p for p in points if p != point and fetch_distance(p, point) < range_distance]
    return nearby_points


def fetch_minute_administrations_for_user(user):
    query = {
        "size": 1,
        "query": {
            "bool": {
                "must": [
                    {
                        "term": {
                            "Data.userName.keyword": {
                                "value": user
                            }
                        }
                    },
                    {
                        "terms": {
                            "Data.administrationStatus.keyword": [
                                "ADMINISTRATION_SUCCESS"
                            ]
                        }
                    },
                    {
                        "range": {
                            "Data.@timestamp": {
                                "gte": gteTime,
                                "lte": lteTime
                            }
                        }
                    },
                    {
                        "term" : {
                            CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                        }
                    }
                ]
            }
        },
        "aggs": {
            "NAME": {
                "date_histogram": {
                    "field": "Data.@timestamp",
                    "fixed_interval": "1m",
                    "order": {
                        "_count": "desc"
                    },
                    "min_doc_count": 1
                }
            }
        }
    }

    minutes_resp = get_resp(ES_PROJECT_TASK_SEARCH, query, True).json()

    minutes_buckets = minutes_resp['aggregations']['NAME']['buckets']
    if len(minutes_resp['hits']['hits']) == 0:
        return
    bh = minutes_resp['hits']['hits'][0]['_source']['Data']['boundaryHierarchy']

    max_administrations = minutes_buckets[0]['doc_count']

    if len(minutes_buckets) == 0:
        return

    three_administration_in_a_min_buckets = [item['key_as_string'] for item in minutes_buckets if
                                             item['doc_count'] >= 3]

    list_max_administration_time = [item['key_as_string'] for item in minutes_buckets if
                                    item['doc_count'] == max_administrations]
    result = {
        "Province": bh['province'],
        "District": bh['district'],
        "userName": user,
        "total administrations by user": minutes_resp['hits']['total']['value'],
        "max administrations in 1 min": max_administrations,
        "no. of times user administered >= 3 individuals in 1 min": len(three_administration_in_a_min_buckets),
        "time of user administered >=3 individuals in 1 min": ", ".join(three_administration_in_a_min_buckets)
    }

    return result


result_obj = {}

with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
    futures = []

    for user in user_list:
        future = executor.submit(fetch_minute_administrations_for_user, user)
        futures.append(future)
    m = 0
    for future in concurrent.futures.as_completed(futures):
        result = future.result()
        if result is not None:
            result_obj[result['userName']] = result
        if m % 100 == 0:
            print(f'{m} futures completed in -- {len(user_list)}')
        m += 1


def fetch_coordinates_for_user(user, i):
    task_points_query = {
        "size": 4000,
        "query": {
            "bool": {
                "must": [
                    {
                        "term": {
                            "Data.userName.keyword": {
                                "value": user
                            }
                        }
                    },
                    {
                        "terms": {
                            "Data.administrationStatus.keyword": [
                                "ADMINISTRATION_SUCCESS"
                            ]
                        }
                    },
                    {
                        "exists": {
                            "field": "Data.geoPoint"
                        }
                    },
                    {
                        "range": {
                            "Data.@timestamp": {
                                "gte": gteTime,
                                "lte": lteTime
                            }
                        }
                    },
                    {
                        "term" : {
                            CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                        }
                    }
                ]
            }
        },
        "_source": ["Data.geoPoint", "Data.individualId", "Data.boundaryHierarchy"]
    }

    task_resp = get_resp(ES_PROJECT_TASK_SEARCH, task_points_query, True).json()

    raw_points = task_resp['hits']['hits']
    total_administrations = len(raw_points)

    if total_administrations != 0:
        bh = raw_points[0]['_source']['Data']['boundaryHierarchy']
        id_vs_coordinates = {}

        for point in raw_points:
            if point['_source']['Data']['individualId'] == None:
                continue
            id_vs_coordinates[point['_source']['Data']['individualId']] = point['_source']['Data']['geoPoint']

        temp_result_set = {}
        temp_result_set_ind_vs_coordinates = {}
        for ind in id_vs_coordinates:
            temp_center_point = id_vs_coordinates.get(ind)
            other_points = [id_vs_coordinates[id] for id in id_vs_coordinates.keys() if id != ind]
            nearby_points = points_within_range(temp_center_point, other_points, DISTANCE)
            temp_result_set[ind] = len(nearby_points)
            temp_result_set_ind_vs_coordinates[ind] = nearby_points + [temp_center_point]
        result_set_values = temp_result_set.values()
        max_points_within_radius = max(result_set_values) if len(result_set_values) != 0 else 0
        max_value_keys = [key for key, value in temp_result_set.items() if value == max_points_within_radius]

        # number_of_times_points_in_radius_exceeded_limt = len([i for i in result_set_values if i > NEARBY_POINTS_LIMIT])
        # print(f" {i} ----------------------------------- ")

        return {
            "Province": bh['province'],
            "District": bh['district'],
            "userName": user,
            "total administrations by user with coordinates": total_administrations,
            "Max points within radius 5m": max_points_within_radius + 1,
            "percentage of records falls within radius": (max_points_within_radius + 1) * 100 / total_administrations,
            "coordinates list": temp_result_set_ind_vs_coordinates[max_value_keys[0]] if len(
                max_value_keys) != 0 else []
        }


with concurrent.futures.ThreadPoolExecutor(max_workers=30) as executor:
    futures = []

    for user in user_list:
        future = executor.submit(fetch_coordinates_for_user, user, user_list.index(user))
        futures.append(future)
    m = 0
    for future in concurrent.futures.as_completed(futures):
        result = future.result()
        if result is not None:
            if result['userName'] in result_obj:
                result_obj[result['userName']] = {**result_obj[result['userName']], **result}
            else:
                result_obj[result['userName']] = result
        if m % 100 == 0:
            print(f'{m} futures completed in -- {len(user_list)}')
        m += 1

final_list = list(result_obj.values())

df = pd.DataFrame(final_list)

df_sorted = df.sort_values(by=['Province', 'District', 'userName'], ascending=[True, True, True])

df_sorted.to_excel(f"{FILE_NAME}.xlsx", index=False)

e_time = time.time()