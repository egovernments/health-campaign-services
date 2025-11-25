import os
import sys
import warnings
import pandas as pd
from datetime import datetime
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--campaign_number', required=True)
parser.add_argument('--start_date', default='')
parser.add_argument('--end_date', default='')
parser.add_argument('--file_name', required=True)
args = parser.parse_args()

CAMPAIGN_NUMBER = args.campaign_number
START_DATE = args.start_date
END_DATE = args.end_date
FILE_NAME = args.file_name

# === PATH SETUP ===
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

from COMMON_UTILS.common_utils import get_resp
from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

# === CONSTANTS ===
ES_HOUSEHOLD_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/household-index-v1/_search"
ES_HOUSEHOLD_MEMBER_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/household-member-index-v1/_search"
ES_INDIVIDUAL_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/individual-index-v1/_search"
ES_PROJECT_BENEFICIARY_INDEX = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/project-beneficiary-index-v1/_search"
ES_SCROLL_API = "http://elasticsearch-master.es-upgrade.svc.cluster.local:9200/_search/scroll"

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports(START_DATE, END_DATE)
start_date = datetime.strptime(start_date_str, "%Y-%m-%d %H:%M:%S%z")
end_date = datetime.strptime(end_date_str, "%Y-%m-%d %H:%M:%S%z")
date_folder = f"{start_date.strftime('%Y-%m-%d')}_to_{end_date.strftime('%Y-%m-%d')}"

# === UTILITY FUNCTIONS ===
def decrypt_mobile_number(encrypted_mobile):
    """
    Decrypt mobile number from format: phoneNumber|encryptedData
    Returns the phone number part before the pipe
    """
    try:
        if not encrypted_mobile or encrypted_mobile == "":
            return ""

        # Format is: phoneNumber|encryptedData
        # We just need to extract the phone number part
        if "|" in encrypted_mobile:
            phone_number = encrypted_mobile.split("|")[0]
            return phone_number

        return encrypted_mobile
    except Exception:
        return ""


def convert_epoch_to_datetime(epoch_ms):
    """Convert epoch milliseconds to formatted date string"""
    try:
        dt = datetime.fromtimestamp(epoch_ms / 1000)
        return dt.strftime('%d-%m-%y %H:%M:%S')
    except Exception:
        return ""


def calculate_age_from_dob(dob_str):
    """Calculate age in years from DD/MM/YYYY format"""
    try:
        dob = datetime.strptime(dob_str, "%d/%m/%Y")
        today = datetime.now()
        age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
        return age
    except Exception:
        return ""


# === FETCH REGISTRAR MOBILE NUMBERS ===
def fetch_registrar_mobile_numbers(user_ids):
    """Fetch mobile numbers for registrars (system users) from individual index using isSystemUser=true"""
    if not user_ids:
        return {}

    print(f"🔄 Fetching mobile numbers for {len(user_ids)} registrars...")

    # Split into chunks to avoid request size limits
    chunk_size = 5000
    all_mobile_data = {}

    for i in range(0, len(user_ids), chunk_size):
        chunk = user_ids[i:i + chunk_size]

        query = {
            "size": len(chunk),
            "_source": ["userUuid", "mobileNumber"],
            "query": {
                "bool": {
                    "must": [
                        {
                            "terms": {
                                "userUuid.keyword": chunk
                            }
                        },
                        {
                            "term": {
                                "isSystemUser": True
                            }
                        }
                    ]
                }
            }
        }

        try:
            resp = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()
            hits = resp.get("hits", {}).get("hits", [])

            for hit in hits:
                src = hit["_source"]
                userUuid = src.get("userUuid", "")
                encrypted_mobile = src.get("mobileNumber", "")
                # decrypted_mobile = decrypt_mobile_number(encrypted_mobile)

                if userUuid:
                    all_mobile_data[userUuid] = encrypted_mobile

        except Exception as e:
            print(f"⚠️ Error fetching registrar mobile numbers: {e}")
            continue

    print(f"✅ Retrieved {len(all_mobile_data)} registrar mobile numbers.")
    return all_mobile_data


# === FETCH HOUSEHOLD DATA ===
def fetch_household_data():
    """Fetch household data with registrar and boundary information"""
    query = {
        "size": 5000,
        "_source": [
            "Data.boundaryHierarchy",
            "Data.household.clientReferenceId",
            "Data.household.clientAuditDetails.createdBy",
            "Data.nameOfUser",
            "Data.userName",
            "Data.household.memberCount",
            "Data.household.clientAuditDetails.createdTime",
            "Data.syncedTimeStamp",
            "Data.household.address"
        ],
        "query": {
            "bool": {
                "must" : [
                    {
                        "term" : {
                            "Data.campaignNumber.keyword" : CAMPAIGN_NUMBER
                        }
                    }
                ],
                "filter": [
                    {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}}
                ]
            }
        }
    }
    household_client_reference_ids = []

    scroll_id = None
    """
    SUGGESTION : Make the scroll time dynamic according to the campaign target (read from helm)
    some campaigns can have more coverage while other will have less
    and hence some campaign might need scroll duration of more than 5 mins 
    """
    initial_scroll_url = ES_HOUSEHOLD_INDEX + "?scroll=5m"
    all_data = []

    previous_scroll_id = None
    while True:
        if scroll_id is None:
            resp = get_resp(initial_scroll_url, query, True).json()
        else:
            resp = get_resp(ES_SCROLL_API, {"scroll": "5m", "scroll_id": scroll_id}, True).json()

        new_scroll_id = resp.get("_scroll_id")
        hits = resp.get("hits", {}).get("hits", [])

        if not hits or new_scroll_id == previous_scroll_id:
            break

        previous_scroll_id = new_scroll_id
        scroll_id = new_scroll_id

        for hit in hits:
            data = hit["_source"].get("Data", {})
            boundary = data.get("boundaryHierarchy", {})
            household = data.get("household", {})
            audit_details = household.get("clientAuditDetails", {})
            address = household.get("address", {})

            user_id = audit_details.get("createdBy", "")
            household_client_reference_ids.append(household.get("clientReferenceId", ""))
            row = {
                "Household Client Reference ID": household.get("clientReferenceId", ""),
                "Province": boundary.get("province", ""),
                "District": boundary.get("district", ""),
                "Administrative Province": boundary.get("administrativeProvince", ""),
                "Locality": boundary.get("locality", ""),
                "Village": boundary.get("village", ""),
                "User id": user_id,
                "Registrar Name": data.get("nameOfUser", ""),
                "Number of People Living in HH": household.get("memberCount", ""),
                "Created Time": convert_epoch_to_datetime(audit_details.get("createdTime", 0)),
                "Synced Time": data.get("syncedTimeStamp", ""),
                "Location Accuracy" : address.get("locationAccuracy", 0),
                "Latitude" : address.get("latitude", 0),
                "Longitude" : address.get("longitude", 0)
            }
            all_data.append(row)

    return all_data, household_client_reference_ids


"""
Fetch household heads for only the households you're fetching data for (falling in the time range)
that way you won't be fetching household head info of unrelated households
there's a one to one mapping between household and household head (1 household head for 1 household)
"""

# === FETCH HOUSEHOLD MEMBER DATA (HEAD OF HOUSEHOLD) ===
# def fetch_household_head_data(household_clientreferenceids):
#     """Fetch household member data to identify household heads"""
#     query = {
#         "size": 5000,
#         "_source": [
#             "Data.householdMember.householdClientReferenceId",
#             "Data.householdMember.individualClientReferenceId",
#             "Data.householdMember.isHeadOfHousehold"
#         ],
#         "query": {
#             "bool": {
#                 "must": [
#                     {"term": {"Data.householdMember.isHeadOfHousehold": True}},
#                     {"terms": {"Data.householdMember.householdClientReferenceId": chunk}}
#                 ]
#             }
#         }
#     }

#     scroll_id = None
#     initial_scroll_url = ES_HOUSEHOLD_MEMBER_INDEX + "?scroll=5m"
#     all_data = []

#     previous_scroll_id = None
#     while True:
#         if scroll_id is None:
#             resp = get_resp(initial_scroll_url, query, True).json()
#         else:
#             resp = get_resp(ES_SCROLL_API, {"scroll": "5m", "scroll_id": scroll_id}, True).json()

#         new_scroll_id = resp.get("_scroll_id")
#         hits = resp.get("hits", {}).get("hits", [])

#         if not hits or new_scroll_id == previous_scroll_id:
#             break

#         previous_scroll_id = new_scroll_id
#         scroll_id = new_scroll_id

#         for hit in hits:
#             data = hit["_source"].get("Data", {})
#             member = data.get("householdMember", {})
#             all_data.append({
#                 "Household Client Reference ID": member.get("householdClientReferenceId", ""),
#                 "Head Individual Client Reference ID": member.get("individualClientReferenceId", "")
#             })

#     return all_data


def fetch_household_head_data(household_clientReferenceIds) :
    print(f"{len(household_clientReferenceIds)} number of clientReferenceIds provided to fetch household heads") 
    all_data = []
    individual_clientReferenceIds = []

    # Split clientReferenceIds into chunks of 10000 or less
    chunk_size = 10000
    for i in range(0, len(household_clientReferenceIds), chunk_size):
        chunk = household_clientReferenceIds[i:i + chunk_size]
        query = {
            "size" : len(chunk),
            "query": {
                "bool": {
                    "must": [
                                {
                                "terms": {
                                    "Data.householdMember.householdClientReferenceId.keyword": chunk
                            }
                        },
                        {
                            "match": {
                                "Data.householdMember.isHeadOfHousehold": True
                            }
                        }
                    ]
                }
            },
            "_source": ["Data.householdMember.individualClientReferenceId", "Data.householdMember.householdClientReferenceId",
            "Data.householdMember.isHeadOfHousehold"]
        }
        docs = get_resp(ES_HOUSEHOLD_MEMBER_INDEX, query, True).json()["hits"]["hits"]
        for item in docs:
            data = item["_source"].get("Data", {})
            member = data.get("householdMember", {})
            all_data.append({
                "Household Client Reference ID": member.get("householdClientReferenceId", ""),
                "Head Individual Client Reference ID": member.get("individualClientReferenceId", "")
            })
            individual_clientReferenceIds.append(item["_source"]["Data"]["householdMember"]["individualClientReferenceId"])
    # print(data)
    print(f"{len(all_data)} number of modified objects from household member index")
    return individual_clientReferenceIds, all_data


# === FETCH ALL HOUSEHOLD MEMBERS (FOR BENEFICIARIES) ===
def fetch_all_household_members():
    """Fetch all household members"""
    query = {
        "size": 5000,
        "_source": [
            "Data.householdMember.householdClientReferenceId",
            "Data.householdMember.individualClientReferenceId"
        ],
        "query": {"match_all": {}}
    }

    scroll_id = None
    initial_scroll_url = ES_HOUSEHOLD_MEMBER_INDEX + "?scroll=5m"
    all_data = []

    previous_scroll_id = None
    while True:
        if scroll_id is None:
            resp = get_resp(initial_scroll_url, query, True).json()
        else:
            resp = get_resp(ES_SCROLL_API, {"scroll": "5m", "scroll_id": scroll_id}, True).json()

        new_scroll_id = resp.get("_scroll_id")
        hits = resp.get("hits", {}).get("hits", [])

        if not hits or new_scroll_id == previous_scroll_id:
            break

        previous_scroll_id = new_scroll_id
        scroll_id = new_scroll_id

        for hit in hits:
            data = hit["_source"].get("Data", {})
            member = data.get("householdMember", {})
            all_data.append({
                "Household Client Reference ID": member.get("householdClientReferenceId", ""),
                "Beneficiary Individual Client Reference ID": member.get("individualClientReferenceId", "")
            })

    return all_data


# === FETCH INDIVIDUAL DATA ===
# def fetch_individual_data():
#     """Fetch individual data for both heads and beneficiaries"""
#     query = {
#         "size": 5000,
#         "_source": [
#             "clientReferenceId",
#             "name.givenName",
#             "name.familyName",
#             "gender",
#             "dateOfBirth",
#             "mobileNumber",
#             "address.latitude",
#             "address.longitude",
#             "address.locationAccuracy"
#         ],
#         "query": {"match_all": {}}
#     }

#     scroll_id = None
#     initial_scroll_url = ES_INDIVIDUAL_INDEX + "?scroll=5m"
#     all_data = []

#     previous_scroll_id = None
#     while True:
#         if scroll_id is None:
#             resp = get_resp(initial_scroll_url, query, True).json()
#         else:
#             resp = get_resp(ES_SCROLL_API, {"scroll": "5m", "scroll_id": scroll_id}, True).json()

#         new_scroll_id = resp.get("_scroll_id")
#         hits = resp.get("hits", {}).get("hits", [])

#         if not hits or new_scroll_id == previous_scroll_id:
#             break

#         previous_scroll_id = new_scroll_id
#         scroll_id = new_scroll_id

#         for hit in hits:
#             src = hit["_source"]

#             given_name = src.get("name", {}).get("givenName") or ""
#             family_name = src.get("name", {}).get("familyName") or ""
#             full_name = (given_name + " " + family_name).strip()

#             dob_str = src.get("dateOfBirth", "")
#             age = calculate_age_from_dob(dob_str) if dob_str else ""

#             address_list = src.get("address", [])
#             address = address_list[0] if address_list else {}

#             row = {
#                 "Individual Client Reference ID": src.get("clientReferenceId", ""),
#                 "Individual Name": full_name,
#                 "Individual Age": age,
#                 "Individual Gender": src.get("gender", ""),
#                 "Individual Mobile Number": src.get("mobileNumber", ""),
#                 "Individual Latitude": address.get("latitude", ""),
#                 "Individual Longitude": address.get("longitude", ""),
#                 "Individual Location Accuracy": address.get("locationAccuracy", "")
#             }
#             all_data.append(row)

#     return all_data


def fetch_individual_data(individualClientReferenceIds):
    print(f"{len(individualClientReferenceIds)} number of individualClientReferenceIds provided to fetch individuals") 
    all_data = []

    # Split individualClientReferenceIds into chunks of 10000 or less
    chunk_size = 10000
    for i in range(0, len(individualClientReferenceIds), chunk_size):
        chunk = individualClientReferenceIds[i:i + chunk_size]
        query = {
            "size": len(chunk),
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
                "address.locationAccuracy"
            ]
        }
        docs = get_resp(ES_INDIVIDUAL_INDEX, query, True).json()["hits"]["hits"]
        for item in docs:
            src = item["_source"]

            given_name = src.get("name", {}).get("givenName") or ""
            family_name = src.get("name", {}).get("familyName") or ""
            full_name = (given_name + " " + family_name).strip()

            dob_str = src.get("dateOfBirth", "")
            age = calculate_age_from_dob(dob_str) if dob_str else ""

            address_list = src.get("address", [])
            address = address_list[0] if address_list else {}

            row = {
                "Individual Client Reference ID": src.get("clientReferenceId", ""),
                "Individual Name": full_name,
                "Individual Age": age,
                "Individual Gender": src.get("gender", ""),
                "Individual Mobile Number": src.get("mobileNumber", ""),
                "Individual Latitude": address.get("latitude", ""),
                "Individual Longitude": address.get("longitude", ""),
                "Individual Location Accuracy": address.get("locationAccuracy", "")
            }
            all_data.append(row)
    # print(data)
    print(f"{len(all_data)} number of modified objects from individual index")
    return all_data


# === FETCH PROJECT BENEFICIARY DATA (VOUCHER) ===
# def fetch_voucher_data():
#     """Fetch voucher codes from project beneficiary"""
#     query = {
#         "size": 5000,
#         "_source": [
#             "beneficiaryClientReferenceId",
#             "tag"
#         ],
#         "query": {"match_all": {}}
#     }

#     scroll_id = None
#     initial_scroll_url = ES_PROJECT_BENEFICIARY_INDEX + "?scroll=5m"
#     all_data = []

#     previous_scroll_id = None
#     while True:
#         if scroll_id is None:
#             resp = get_resp(initial_scroll_url, query, True).json()
#         else:
#             resp = get_resp(ES_SCROLL_API, {"scroll": "5m", "scroll_id": scroll_id}, True).json()

#         new_scroll_id = resp.get("_scroll_id")
#         hits = resp.get("hits", {}).get("hits", [])

#         if not hits or new_scroll_id == previous_scroll_id:
#             break

#         previous_scroll_id = new_scroll_id
#         scroll_id = new_scroll_id

#         for hit in hits:
#             src = hit.get("_source", {})
#             all_data.append({
#                 "Household Client Reference ID": src.get("beneficiaryClientReferenceId", ""),
#                 "Serial Number of Voucher": src.get("tag", "")
#             })

#     return all_data


def fetch_voucher_data(household_clientreferenceids):
    print(f"{len(household_clientreferenceids)} number of household clientReferenceIds provided to fetch voucher codes") 
    all_data = []

    chunk_size = 10000
    for i in range(0, len(household_clientreferenceids), chunk_size):
        chunk = household_clientreferenceids[i:i + chunk_size]
        query = {
            "size": len(chunk),
            "query": {
                "terms": {
                    "beneficiaryClientReferenceId.keyword": chunk
                }
            },
            "_source": ["tag", "beneficiaryClientReferenceId"]
        }
        docs = get_resp(ES_PROJECT_BENEFICIARY_INDEX, query, True).json()["hits"]["hits"]
        for item in docs:
            src = item.get("_source", {})
            all_data.append({
                "Household Client Reference ID": src.get("beneficiaryClientReferenceId", ""),
                "Serial Number of Voucher": src.get("tag", "")
            })

    print(f"{len(all_data)} number of modified objects from project beneficiary index")
    return all_data


required_columns_for_individual = [
    "Individual Client Reference ID",
    "Individual Name",
    "Individual Age",
    "Individual Gender",
    "Individual Mobile Number",
    "Individual Latitude",
    "Individual Longitude",
    "Individual Location Accuracy"
]

required_columns_for_hhm = [
    "Household Client Reference ID",
    "Head Individual Client Reference ID"
]

required_columns_for_beneficiary = [
    "Household Client Reference ID",
    "Serial Number of Voucher"
]


# === MAIN ===
print("🔄 Fetching household data...")
household_data, household_clientreferenceids = fetch_household_data()
df_household = pd.DataFrame(household_data)

# Extract unique user IDs for registrar phone numbers
if not df_household.empty:
    unique_user_ids = df_household["User id"].dropna().unique().tolist()
    registrar_mobiles = fetch_registrar_mobile_numbers(unique_user_ids)

    # Add phone numbers to household data
    df_household["Phone number"] = df_household["User id"].map(registrar_mobiles).fillna("")
else:
    registrar_mobiles = {}

print("🔄 Fetching household head data...")
individual_clientReferenceIds, household_head_data = fetch_household_head_data(household_clientreferenceids)
df_household_head = pd.DataFrame(household_head_data)

for col in required_columns_for_hhm:
    if col not in df_household_head.columns:
        df_household_head[col] = ""

# print("🔄 Fetching all household members (beneficiaries)...")
# all_members_data = fetch_all_household_members()
# df_all_members = pd.DataFrame(all_members_data)

print("🔄 Fetching individual data...")
individual_data = fetch_individual_data(individual_clientReferenceIds)
df_individual = pd.DataFrame(individual_data)

for col in required_columns_for_individual:
    if col not in df_individual.columns:
        df_individual[col] = ""

# Decrypt mobile numbers for individuals
# df_individual["Individual Mobile Number"] = df_individual["Individual Mobile Number"].apply(decrypt_mobile_number)


print("🔄 Fetching voucher data...")
voucher_data = fetch_voucher_data(household_clientreferenceids)
df_voucher = pd.DataFrame(voucher_data)

for col in required_columns_for_beneficiary:
    if col not in df_voucher.columns:
        df_voucher[col] = ""

if df_household.empty:
    print("⚠️ No household records found. Creating empty report...")
    # Create empty DataFrame with required columns
    column_order = [
        "Province", "District", "Administrative Province", "Locality", "Village",
        "User id", "Phone number", "Registrar Name", "Beneficiary Name", "Age (Beneficiary)",
        "Gender (Beneficiary)", "Id (Beneficiary)", "Household Head Name", "Age (Household Head)",
        "Gender (Household Head)", "Id (Household Head)", "Mobile Number",
        "Number of People Living in HH", "Serial Number of Voucher",
        "Latitude", "Longitude", "Location Accuracy", "Created Time", "Synced Time"
    ]
    df_final = pd.DataFrame(columns=column_order)
else:
    print(f"✅ Retrieved {len(df_household)} household records.")
    print(f"✅ Retrieved {len(df_household_head)} household head records.")
    # print(f"✅ Retrieved {len(df_all_members)} household member records.")
    print(f"✅ Retrieved {len(df_individual)} individual records.")
    print(f"✅ Retrieved {len(df_voucher)} voucher records.")
    print(f"✅ Retrieved {len(registrar_mobiles)} registrar phone numbers.")

    # === JOIN 1: Household + Household Head ===
    df_with_head = pd.merge(
        df_household,
        df_household_head,
        on="Household Client Reference ID",
        how="left"
    )

    # === JOIN 2: Add Household Head Details from Individual ===
    df_with_head_details = pd.merge(
        df_with_head,
        df_individual.rename(columns={
            "Individual Client Reference ID": "Head Individual Client Reference ID",
            "Individual Name": "Household Head Name",
            "Individual Age": "Age (Household Head)",
            "Individual Gender": "Gender (Household Head)",
            "Individual Mobile Number": "Mobile Number"
        }),
        on="Head Individual Client Reference ID",
        how="left"
    )

    # Drop intermediate columns and head's location data (we need beneficiary location)
    df_with_head_details = df_with_head_details.drop(columns=[
        "Head Individual Client Reference ID",
        "Individual Latitude",
        "Individual Longitude",
        "Individual Location Accuracy"
    ], errors='ignore')

    # Rename the head ID column
    df_with_head_details = df_with_head_details.rename(columns={
        "Head Individual Client Reference ID": "Id (Household Head)"
    })

    # # === JOIN 3: Add All Household Members (Beneficiaries) ===
    # df_with_beneficiaries = pd.merge(
    #     df_with_head_details,
    #     df_all_members,
    #     on="Household Client Reference ID",
    #     how="left"
    # )

    # # === JOIN 4: Add Beneficiary Details from Individual ===
    # df_with_beneficiary_details = pd.merge(
    #     df_with_beneficiaries,
    #     df_individual.rename(columns={
    #         "Individual Client Reference ID": "Beneficiary Individual Client Reference ID",
    #         "Individual Name": "Beneficiary Name",
    #         "Individual Age": "Age (Beneficiary)",
    #         "Individual Gender": "Gender (Beneficiary)",
    #         "Individual Latitude": "Latitude",
    #         "Individual Longitude": "Longitude",
    #         "Individual Location Accuracy": "Location Accuracy"
    #     }),
    #     on="Beneficiary Individual Client Reference ID",
    #     how="left"
    # )

    # # Rename beneficiary ID column and drop intermediate columns
    # df_with_beneficiary_details = df_with_beneficiary_details.rename(columns={
    #     "Beneficiary Individual Client Reference ID": "Id (Beneficiary)"
    # })
    # df_with_beneficiary_details = df_with_beneficiary_details.drop(columns=[
    #     "Individual Mobile Number"
    # ], errors='ignore')

    # === JOIN 5: Add Voucher Information ===
    df_final = pd.merge(
        df_with_head_details,
        df_voucher,
        on="Household Client Reference ID",
        how="left"
    )

    # === REORDER COLUMNS TO MATCH REQUIREMENTS ===
    column_order = [
        "Province",
        "District",
        "Administrative Province",
        "Locality",
        "Village",
        "User id",
        "Phone number",
        "Registrar Name",
        # "Beneficiary Name",
        # "Age (Beneficiary)",
        # "Gender (Beneficiary)",
        # "Id (Beneficiary)",
        "Household Head Name",
        "Age (Household Head)",
        "Gender (Household Head)",
        "Id (Household Head)",
        "Mobile Number",
        "Number of People Living in HH",
        "Serial Number of Voucher",
        "Latitude",
        "Longitude",
        "Location Accuracy",
        "Created Time",
        "Synced Time"
    ]

    # Reorder columns (only include columns that exist)
    available_columns = [col for col in column_order if col in df_final.columns]
    df_final = df_final[available_columns]

    # === SORT BY LOCATION ===
    sort_cols = [col for col in ["Province", "District", "Administrative Province", "Locality", "Village"] if col in df_final.columns]
    if sort_cols:
        df_final.sort_values(by=sort_cols, inplace=True)

# === SAVE REPORT === (save to current working directory with FILE_NAME from command line arg)
# main.py will handle moving the file to the correct PVC folder structure
file_name = f"{FILE_NAME}.xlsx"
output_path = os.path.join(os.getcwd(), file_name)

df_final.to_excel(output_path, index=False)
print(f"✅ Detailed registration report generated: {output_path} (Rows: {len(df_final)})")
