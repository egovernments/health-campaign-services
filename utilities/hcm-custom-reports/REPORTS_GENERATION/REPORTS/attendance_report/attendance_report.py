import warnings
import pandas as pd
import os
import sys
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

if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "projectTypeId.keyword"
    print(f"📋 Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "campaignNumber.keyword"
    print(f"📋 Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from COMMON_UTILS.common_utils import get_resp

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

ES_TRANSFORMED_ATTENDANCE_REGISTER_INDEX = 'https://elasticsearch-data.es-cluster-v8:9200/transformed-attendance-register-index-v1/_search'
ES_ATTENDANCE_LOG_INDEX = 'https://elasticsearch-data.es-cluster-v8:9200/attendance-log-index-v1/_search'
ES_PROJECT_STAFF_INDEX = "https://elasticsearch-data.es-cluster-v8:9200/project-staff-index-v1/_search"

# ES_ATTENDANCE_LOG_INDEX = "https://localhost:9200/attendance-log-index-v1/_search"
# ES_TRANSFORMED_ATTENDANCE_REGISTER_INDEX = "https://localhost:9200/transformed-attendance-register-index-v1/_search"
# ES_PROJECT_STAFF_INDEX = "https://localhost:9200/project-staff-index-v1/_search"


# lte_time, gte_time, start_date_str, end_date_str = get_custom_dates_of_reports()

def fetch_transformed_register():
    query = {
        "size": 10000,
        "query": {
            "match_all": {
            }
        }
    }
    resp = get_resp(ES_TRANSFORMED_ATTENDANCE_REGISTER_INDEX, query, True).json()["hits"]["hits"]
    return resp


transformed_register = fetch_transformed_register()

registerId_attendeesInfo_map = {}
for register in transformed_register:
    registerId = register["_source"]["attendanceRegister"]["id"]
    registerId_attendeesInfo_map[registerId] = register["_source"]["attendeesInfo"]


def fetch_present_attendees():
    query = {
        "size": 0,
        "query": {
            "bool": {
                "must": [
                    #TODO: add this filter back when attendance indexes have campaign number or project type info
                    # {
                    #     "term" : {
                    #         CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                    #     }
                    # }
                ]
            }
        },
        "aggs": {
            "composite_agg": {
                "composite": {
                    "size": 10000,
                    "sources": [
                        {
                            "name": {
                                "terms": {
                                    "script": {
                                        "source": """
                                        String givenName = doc.containsKey('attendeeName.givenName.keyword') && !doc['attendeeName.givenName.keyword'].empty ? doc['attendeeName.givenName.keyword'].value : 'null';
                                        String familyName = doc.containsKey('attendeeName.familyName.keyword') && !doc['attendeeName.familyName.keyword'].empty ? doc['attendeeName.familyName.keyword'].value : 'null';
                                        return givenName + '#' + familyName;
                                    """
                                    }
                                }
                            }
                        },
                        {
                            "username": {
                                "terms": {
                                    "field": "userName.keyword"
                                }
                            }
                        },
                        {
                            "attendance_type": {
                                "terms": {
                                    "field": "attendanceLog.type.keyword"
                                }
                            }
                        },
                        {
                            "attendee_individual_id": {
                                "terms": {
                                    "field": "attendanceLog.individualId.keyword"
                                }
                            }
                        }
                    ]
                },
                "aggs": {
                    "total_docs": {
                        "date_histogram": {
                            "field": "@timestamp",
                            "calendar_interval": "day",
                            "format": "yyyy-MM-dd",
                            "min_doc_count": 1
                        }
                    }
                }
            }
        }
    }
    # resp = get_resp(ES_ATTENDANCE_LOG_INDEX, query, True).json()["aggregations"]["composite_agg"]["buckets"]
    resp = []
    while True:
        resp1 = get_resp(ES_ATTENDANCE_LOG_INDEX, query, True).json()['aggregations']['composite_agg']
        
        after_key = resp1.get("after_key", "")
        print("afterKey",after_key)

        if len(resp1.get("buckets", [])) == 0:
            break
        # extract_data(resp1.get("buckets", []))
        resp.extend(resp1.get("buckets", []))
        query["aggs"]["composite_agg"]["composite"]["after"] = after_key
    
    return resp


attendance_date_map = {}
usernames_list = []
present_attendees = fetch_present_attendees()
for item in present_attendees:
    attendee_individual_id = item["key"]["attendee_individual_id"]
    attendance_type = item["key"]["attendance_type"]
    name, user_id = item["key"]["name"].split("#")
    username = item["key"]["username"]

    if attendee_individual_id not in attendance_date_map:
        attendance_date_map[attendee_individual_id] = {}

    if attendance_type == "ENTRY":
        for date_bucket in item["total_docs"]["buckets"]:
            date = date_bucket["key_as_string"]
            attendance_date_map[attendee_individual_id]['name'] = name if name is not None else 'null'
            attendance_date_map[attendee_individual_id]['username'] = username if username is not None else 'null'
            attendance_date_map[attendee_individual_id]['user_id'] = user_id if user_id is not None else 'null'
            # attendance_date_map[attendee_individual_id][date] = "FULL" if date_bucket["doc_count"] >= 2 else "HALF" if date_bucket[
            #                                                                                         "doc_count"] == 1 else "ABSENT"
            attendance_date_map[attendee_individual_id][date] = "PRESENT" if date_bucket["doc_count"] >= 1 else "ABSENT"
            if username is not None:
                usernames_list.append(username)

missing_names = []
for registerId, attendees_info in registerId_attendeesInfo_map.items():
    for attendee_individual_id, attendee_name in attendees_info.items():
        # Check if the individual id exists in attendance_map
        if attendee_individual_id not in attendance_date_map:
            missing_names.append(
                str(attendee_name.get('givenName', '')) + '#' + str(attendee_name.get('familyName', '')))
            attendance_date_map[attendee_individual_id] = {}

print("missing - ", missing_names)


def fetch_boundary(usernames):
    usernameVsBoundary_map = {}
    for i in range(0, len(usernames), 5000):
        query = {
            "size": 6000,
            "_source": ["Data.userName", "Data.boundaryHierarchy"],
            "query": {
                "terms": {
                    "Data.userName.keyword": usernames
                }
            }
        }

        resp = get_resp(ES_PROJECT_STAFF_INDEX, query, True).json()["hits"]["hits"]
        for item in resp:
            username = item["_source"]["Data"]["userName"]
            if username not in usernameVsBoundary_map:
                usernameVsBoundary_map[username] = item["_source"]["Data"]["boundaryHierarchy"]
    return usernameVsBoundary_map


usernameVsBoundary_map = fetch_boundary(usernames_list)

# fetch all dates when attendance is marked
all_dates = set()
print("attendee_records", attendance_date_map.values())
for attendee_records in attendance_date_map.values():
    all_dates.update(key for key in attendee_records.keys() if key not in ['name', 'user_id'])

sorted_dates = sorted(all_dates)

for attendee_individual_id, attendee_records in attendance_date_map.items():
    sorted_records = []

    for date in sorted_dates:
        if date in attendee_records:
            sorted_records.append((date, attendee_records[date]))
        else:
            sorted_records.append((date, "ABSENT"))

    try:
        sorted_records.append(('name', attendee_records['name']))
    except KeyError:
        sorted_records.append(('name', 'null'))

    try:
        sorted_records.append(('user_id', attendee_records['user_id']))
    except KeyError:
        sorted_records.append(('user_id', 'null'))
    try:
        sorted_records.append(('username', attendee_records['username']))
    except KeyError:
        sorted_records.append(('username', 'null'))

    attendance_date_map[attendee_individual_id] = dict(sorted_records)

for attendee_individual_id, attendance in attendance_date_map.items():
    # cumulative_value = sum(1 if value == 'FULL' else 0.5 if value == 'HALF' else 0 for value in attendance.values())
    cumulative_value = sum(1 if value == 'PRESENT' else 0 for value in attendance.values())
    attendance_date_map[attendee_individual_id]['Cumulative'] = cumulative_value

dfs = []

for attendee_id, record in attendance_date_map.items():
    if record['username'] not in usernameVsBoundary_map:
        continue
    df = pd.DataFrame.from_dict({k: [v] for k, v in record.items() if k not in ['name', 'user_id']})
    df['Name'] = record['name']
    df['User ID'] = record['user_id']
    df['Username'] = record['username']
    df['Province'] = usernameVsBoundary_map[record['username']].get('province', 'null')
    df['District'] = usernameVsBoundary_map[record['username']].get('district', 'null')
    df['Administrative Province'] = usernameVsBoundary_map[record['username']].get('administrativeProvince', 'null')
    df = df[['Province', 'District', 'Administrative Province', 'Name', 'Username'] + sorted(
        df.columns.difference(['Province', 'District', 'Administrative Province', 'Name', 'User ID', 'Username', 'Cumulative'])) + [
                'Cumulative']]
    # Rename date columns
    date_columns = [col for col in df.columns if
                    col not in ['Province', 'District', 'Administrative Province', 'Name', 'User ID', 'Username', 'LGA', 'Cumulative']]
    for i, col in enumerate(date_columns, start=1):
        df.rename(columns={col: f"Day {i} - {col}"}, inplace=True)
    dfs.append(df)

result = pd.concat(dfs, ignore_index=True)

result.to_excel(f"{FILE_NAME}.xlsx", index=False)

print("Excel file created successfully.")