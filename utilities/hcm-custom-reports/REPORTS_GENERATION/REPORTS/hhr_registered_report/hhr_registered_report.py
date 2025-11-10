import os
import sys
import warnings
import pandas as pd
from datetime import datetime

# === PATH SETUP ===
file_path = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.append(file_path)

from COMMON_UTILS.common_utils import get_resp
from COMMON_UTILS.custom_date_utils import get_custom_dates_of_reports

warnings.filterwarnings("ignore", message="Unverified HTTPS request is being made.*")

# === CONSTANTS ===
ES_HOUSEHOLD_INDEX = "http://elasticsearch-master-0.es-upgrade:9201/household-index-v1/_search"
ES_HOUSEHOLD_MEMBER_INDEX = "http://elasticsearch-master-0.es-upgrade:9201/household-member-index-v1/_search"
ES_INDIVIDUAL_INDEX = "http://elasticsearch-master-0.es-upgrade:9201/individual-index-v1/_search"
ES_PROJECT_BENEFICIARY_INDEX = "http://elasticsearch-master-0.es-upgrade:9201/project-beneficiary-index-v1/_search"
ES_SCROLL_API = "http://elasticsearch-master-0.es-upgrade:9201/_search/scroll"

# === DATE RANGE ===
lteTime, gteTime, start_date_str, end_date_str = get_custom_dates_of_reports()
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
            "_source": ["clientReferenceId", "mobileNumber"],
            "query": {
                "bool": {
                    "must": [
                        {
                            "terms": {
                                "clientReferenceId.keyword": chunk
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
                client_ref_id = src.get("clientReferenceId", "")
                encrypted_mobile = src.get("mobileNumber", "")
                decrypted_mobile = decrypt_mobile_number(encrypted_mobile)

                if client_ref_id:
                    all_mobile_data[client_ref_id] = decrypted_mobile

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
            "Data.syncedTimeStamp"
        ],
        "query": {
            "bool": {
                "filter": [
                    {"range": {"Data.@timestamp": {"gte": gteTime, "lte": lteTime}}}
                ]
            }
        }
    }

    scroll_id = None
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

            user_id = audit_details.get("createdBy", "")

            row = {
                "Household Client Reference ID": household.get("clientReferenceId", ""),
                "Province": boundary.get("province", ""),
                "District": boundary.get("district", ""),
                "Commune": boundary.get("municipality", ""),
                "Health Facility": boundary.get("areaOfResponsibility", ""),
                "Collines": boundary.get("hills", ""),
                "Sous Collines": boundary.get("subHills", ""),
                "User id": user_id,
                "Registrar Name": data.get("nameOfUser", ""),
                "Number of People Living in HH": household.get("memberCount", ""),
                "Created Time": convert_epoch_to_datetime(audit_details.get("createdTime", 0)),
                "Synced Time": data.get("syncedTimeStamp", "")
            }
            all_data.append(row)

    return all_data


# === FETCH HOUSEHOLD MEMBER DATA (HEAD OF HOUSEHOLD) ===
def fetch_household_head_data():
    """Fetch household member data to identify household heads"""
    query = {
        "size": 5000,
        "_source": [
            "Data.householdMember.householdClientReferenceId",
            "Data.householdMember.individualClientReferenceId",
            "Data.householdMember.isHeadOfHousehold"
        ],
        "query": {
            "bool": {
                "filter": [
                    {"term": {"Data.householdMember.isHeadOfHousehold": True}}
                ]
            }
        }
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
                "Head Individual Client Reference ID": member.get("individualClientReferenceId", "")
            })

    return all_data


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
def fetch_individual_data():
    """Fetch individual data for both heads and beneficiaries"""
    query = {
        "size": 5000,
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
        ],
        "query": {"match_all": {}}
    }

    scroll_id = None
    initial_scroll_url = ES_INDIVIDUAL_INDEX + "?scroll=5m"
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
            src = hit["_source"]

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

    return all_data


# === FETCH PROJECT BENEFICIARY DATA (VOUCHER) ===
def fetch_voucher_data():
    """Fetch voucher codes from project beneficiary"""
    query = {
        "size": 5000,
        "_source": [
            "beneficiaryClientReferenceId",
            "tag"
        ],
        "query": {"match_all": {}}
    }

    scroll_id = None
    initial_scroll_url = ES_PROJECT_BENEFICIARY_INDEX + "?scroll=5m"
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
            src = hit.get("_source", {})
            all_data.append({
                "Household Client Reference ID": src.get("beneficiaryClientReferenceId", ""),
                "Serial Number of Voucher": src.get("tag", "")
            })

    return all_data


# === MAIN ===
print("🔄 Fetching household data...")
household_data = fetch_household_data()
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
household_head_data = fetch_household_head_data()
df_household_head = pd.DataFrame(household_head_data)

print("🔄 Fetching all household members (beneficiaries)...")
all_members_data = fetch_all_household_members()
df_all_members = pd.DataFrame(all_members_data)

print("🔄 Fetching individual data...")
individual_data = fetch_individual_data()
df_individual = pd.DataFrame(individual_data)

# Decrypt mobile numbers for individuals
df_individual["Individual Mobile Number"] = df_individual["Individual Mobile Number"].apply(decrypt_mobile_number)

print("🔄 Fetching voucher data...")
voucher_data = fetch_voucher_data()
df_voucher = pd.DataFrame(voucher_data)

if df_household.empty:
    print("⚠️ No household records found.")
else:
    print(f"✅ Retrieved {len(df_household)} household records.")
    print(f"✅ Retrieved {len(df_household_head)} household head records.")
    print(f"✅ Retrieved {len(df_all_members)} household member records.")
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

    # === JOIN 3: Add All Household Members (Beneficiaries) ===
    df_with_beneficiaries = pd.merge(
        df_with_head_details,
        df_all_members,
        on="Household Client Reference ID",
        how="left"
    )

    # === JOIN 4: Add Beneficiary Details from Individual ===
    df_with_beneficiary_details = pd.merge(
        df_with_beneficiaries,
        df_individual.rename(columns={
            "Individual Client Reference ID": "Beneficiary Individual Client Reference ID",
            "Individual Name": "Beneficiary Name",
            "Individual Age": "Age (Beneficiary)",
            "Individual Gender": "Gender (Beneficiary)",
            "Individual Latitude": "Latitude",
            "Individual Longitude": "Longitude",
            "Individual Location Accuracy": "Location Accuracy"
        }),
        on="Beneficiary Individual Client Reference ID",
        how="left"
    )

    # Rename beneficiary ID column and drop intermediate columns
    df_with_beneficiary_details = df_with_beneficiary_details.rename(columns={
        "Beneficiary Individual Client Reference ID": "Id (Beneficiary)"
    })
    df_with_beneficiary_details = df_with_beneficiary_details.drop(columns=[
        "Individual Mobile Number"
    ], errors='ignore')

    # === JOIN 5: Add Voucher Information ===
    df_final = pd.merge(
        df_with_beneficiary_details,
        df_voucher,
        on="Household Client Reference ID",
        how="left"
    )

    # === REORDER COLUMNS TO MATCH REQUIREMENTS ===
    column_order = [
        "Province",
        "District",
        "Commune",
        "Health Facility",
        "Collines",
        "Sous Collines",
        "User id",
        "Phone number",
        "Registrar Name",
        "Beneficiary Name",
        "Age (Beneficiary)",
        "Gender (Beneficiary)",
        "Id (Beneficiary)",
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
    sort_cols = [col for col in ["Province", "District", "Commune", "Health Facility", "Collines"] if col in df_final.columns]
    if sort_cols:
        df_final.sort_values(by=sort_cols, inplace=True)

    # === SAVE REPORT ===
    output_dir = os.path.join(file_path, "FINAL_REPORTS", date_folder)
    os.makedirs(output_dir, exist_ok=True)

    file_name = "HHR_Detailed_Registration_Report.xlsx"
    output_path = os.path.join(output_dir, file_name)

    df_final.to_excel(output_path, index=False)
    print(f"✅ Detailed registration report generated: {output_path}")
