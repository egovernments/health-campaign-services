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

# Add project root path
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

# Import custom utility functions
from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports
from COMMON_UTILS.common_utils import get_resp

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

# Elasticsearch index
ES_REFERRAL_INDEX = 'http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/referral-index-v1/_search'

# Get report time window
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)

if IDENTIFIER_TYPE == "projectTypeId":
    CAMPAIGN_FILTER_FIELD = "Data.projectTypeId.keyword"
    print(f"📋 Using projectTypeId filter: {CAMPAIGN_IDENTIFIER}")
else:
    CAMPAIGN_FILTER_FIELD = "Data.campaignNumber.keyword"
    print(f"📋 Using campaignNumber filter: {CAMPAIGN_IDENTIFIER}")


def get_user_agg():
    # Initial query template
    query = {
        "size": 0,
        "query": {
            "bool": {
                "must": [
                    {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}},
                    {
                        "term" : {
                            CAMPAIGN_FILTER_FIELD : CAMPAIGN_IDENTIFIER
                        }
                    }
                ]
            }
        },
        "aggs": {
            "users": {
                "composite": {
                    "size": 1000,
                    "sources": [
                        {"userName": {"terms": {"field": "Data.userName.keyword"}}}
                    ]
                },
                "aggs": {
                    "age_groups": {
                        "filters": {
                            "filters": {
                                "age_3_11": {"range": {"Data.age": {"gte": 3, "lte": 11}}},
                                "age_12_59": {"range": {"Data.age": {"gte": 12, "lte": 59}}}
                            }
                        },
                        "aggs": {
                            "record_count": {"value_count": {"field": "Data.referral.clientReferenceId.keyword"}}
                        }
                    },
                    "boundaryHierarchy": {
                        "top_hits": {
                            "size": 1,
                            "_source": {"includes": ["Data.boundaryHierarchy"]}
                        }
                    },
                    "referral_reasons": {
                        "terms": {
                            "field": "Data.referral.reasons.keyword",
                            "size": 10
                        }
                    }
                }
            }
        }
    }

    data_map = {}

    def extract_data(buckets):
        for bucket in buckets:
            user = bucket['key']['userName']
            boundary = bucket['boundaryHierarchy']['hits']['hits'][0]['_source']['Data']['boundaryHierarchy']

            # Initialize reason flags
            reasons_present = {reason: "" for reason in ["SICK", "MALARIA_CHECK", "BENEFICIARY_REFERRED", "MALARIA_DOSE_CHECK"]}
            for reason_bucket in bucket.get('referral_reasons', {}).get('buckets', []):
                reason_key = reason_bucket['key']
                if reason_key in reasons_present:
                    reasons_present[reason_key] = "Yes"

            data_map[user] = {
                'province': boundary.get('province', ''),
                'district': boundary.get('district', ''),
                'administrativeProvince': boundary.get('administrativeProvince', ''),
                'age_3_11_adrs_count': bucket['age_groups']['buckets']['age_3_11']['doc_count'],
                'age_12_59_adrs_count': bucket['age_groups']['buckets']['age_12_59']['doc_count'],
                **reasons_present
            }

    # Pagination loop using after_key
    while True:
        resp = get_resp(ES_REFERRAL_INDEX, query, True).json()
        buckets = resp['aggregations']['users']['buckets']
        if not buckets:
            print("No data returned for current query!")
        extract_data(buckets)

        after_key = resp['aggregations']['users'].get('after_key')
        if not after_key:
            break
        query["aggs"]["users"]["composite"]["after"] = after_key

    return data_map


def generate_report():
    data_map = get_user_agg()

    if not data_map:
        print("No user data found. Excel will be empty.")
    
    # Prepare DataFrame
    df = pd.DataFrame([
        {
            "Province": data['province'],
            "District": data['district'],
            "Administrative Province": data['administrativeProvince'],
            "CDD Username": username,
            "Number of children referred for ADVERSE DRUG REACTIONS to SPAQ (3 - 11 months)": data['age_3_11_adrs_count'],
            "Number of children referred for ADVERSE DRUG REACTIONS to SPAQ (12 - 59 months)": data['age_12_59_adrs_count'],
            "SICK": data['SICK'],
            "MALARIA_CHECK": data['MALARIA_CHECK'],
            "BENEFICIARY_REFERRED": data['BENEFICIARY_REFERRED'],
            "MALARIA_DOSE_CHECK": data['MALARIA_DOSE_CHECK']
        }
        for username, data in data_map.items()
    ])

    # Save to Excel
    output_file = f"{FILE_NAME}.xlsx"
    df.to_excel(output_file, index=False)
    print(f"Excel file created: {output_file}")


# Run report generation
generate_report()
