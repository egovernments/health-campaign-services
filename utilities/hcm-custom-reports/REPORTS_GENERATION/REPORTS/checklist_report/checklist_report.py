from openpyxl import Workbook
import json
import os, sys
import warnings
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


file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

from COMMON_UTILS.common_utils import get_resp
from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

workbook = Workbook()
sheet = workbook.active

ES_SERVICE_TASK_SEARCH = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/service-task-v1/_search?scroll=10m"
ES_SCROLL_URL = 'http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/_search/scroll'

# ES_SERVICE_TASK_SEARCH = "https://localhost:9200/service-task-v1/_search?scroll=10m"
# ES_SCROLL_URL = 'https://localhost:9200/_search/scroll'

checklist_file = open("REPORTS_SCRIPTS/checklist_report/checklist_questions.json")
checklist_question_codes = json.load(checklist_file)

lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"📋 Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"📋 Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")

def extract_datav2(role: str, checklist_name: str):
    query = {
        "size": 10000,
        "query": {
            "bool": {
                "must": [
                    {"match": {"Data.supervisorLevel.keyword": role}},
                    {"match": {"Data.checklistName.keyword": checklist_name}},
                    {"range":{"Data.@timestamp":{"gte":gteTime,"lte":lteTime}}},
                    {
                        "term" : {
                            CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                        }
                    }
                ]
            }
        }
    }
    response = get_resp(ES_SERVICE_TASK_SEARCH, query, True)
    response_json = response.json()
    scroll_id = response_json['_scroll_id']
    hits = response_json['hits']['hits']
    all_hits = hits

    while hits:
        # Scroll to the next batch
        scroll_data = {
            "scroll": "10m",
            "scroll_id": scroll_id
        }
        response = get_resp(ES_SCROLL_URL, scroll_data, True)
        print("whileRes", response)
        if response is None:
            break

        response_json = response.json()
        scroll_id = response_json['_scroll_id']
        hits = response_json['hits']['hits']
        print("hitsLen", len(hits))

        if len(response_json["hits"]["hits"]) == 0:
            scroll_id = None
            break

        # Add current batch of hits to all_hits
        all_hits.extend(hits)

    checklist_documents = []
    for document in all_hits:
        doc = document.get("_source").get("Data")
        checklist_documents.append(doc)
    return checklist_documents


def extract_checklists(role):
    result = []

    for key in checklist_question_codes[role]:
        result.append(key)
    print(result)
    return result


def generate_checklist_excel(workbook, role):
    checklists = extract_checklists(role)
    for checklist in checklists:
        sheet = workbook.create_sheet(title=f"{role} - {checklist}")
        data = extract_datav2(role, checklist)

        # Add a header row
        header_row = ["Province", "District", "Administrative Province", "Username"] + list(
            checklist_question_codes[role][checklist].keys())
        header_row_questions = ["Province", "District", "Administrative Province", "Username"] + list(
            checklist_question_codes[role][checklist].values())
        sheet.append(header_row_questions)

        # Add data from the specified role
        for entry in data:
            username = entry.get("userName", "")
            province = entry.get("boundaryHierarchy", "").get("province", "")
            district = entry.get("boundaryHierarchy", "").get("district", "")
            administrativeProvince = entry.get("boundaryHierarchy", "").get("administrativeProvince", "")
            attribute_values = {}

            # Extract attribute values from the "attributes" array
            for attribute in entry.get("attributes", []):
                attribute_code = attribute.get("attributeCode", "")
                value = attribute.get("value", {}).get("value", "")
                if isinstance(value, list):
                    # Convert the list to a comma-separated string
                    value = ', '.join(value)
                attribute_values[attribute_code] = value

            # Create a row with username and attribute values
            row_data = [province, district, administrativeProvince, username] + [attribute_values.get(code, "") for code in
                                                                header_row[4:]]
            print("roww", row_data)
            sheet.append(row_data)
        print(f"Sheet '{role} - {checklist} Checklist' created successfully.")


def generate_checklist_report(workbook):
    individual_roles = ["DISTRICT_SUPERVISOR"]
    for role in individual_roles:
        generate_checklist_excel(workbook, role)

    print("Generated Checklist report")


generate_checklist_report(workbook)

workbook.save(f"{FILE_NAME}.xlsx")