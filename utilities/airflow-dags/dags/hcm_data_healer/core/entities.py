"""
DIGIT HCM entity definitions for reconciliation and recovery.

Each entity defines its DB table, ES index and id/date fields (check), its
tracer requestBody key (chain recovery), and API paths plus a payload builder
(push). Chain gap letters: A Household, B HouseholdMember, C Individual,
D ProjectBeneficiary, E ProjectTask.
"""

import pandas as pd


# Payload builders
def _safe(row, col, default=None):
    v = row.get(col)
    if v is None:
        return default
    try:
        if pd.isna(v):
            return default
    except (TypeError, ValueError):
        pass
    return v


def _client_audit(row, prefix):
    """Client audit block copied from the source row, or None if absent.

    None makes the push gate skip the record: a null clientAuditDetails would
    push it out of the date window and make it a ghost again on later runs.
    """
    by = _safe(row, f"{prefix}.createdBy")
    at = _safe(row, f"{prefix}.createdTime")
    if not by or not at:
        return None
    # Columns with nulls load as float64; cast back to int epoch ms.
    return {
        "createdBy":        str(by),
        "createdTime":      int(float(at)),
        "lastModifiedBy":   str(_safe(row, f"{prefix}.lastModifiedBy", by)),
        "lastModifiedTime": int(float(_safe(row, f"{prefix}.lastModifiedTime", at))),
    }


def build_household(row, tenant):
    return {
        "clientReferenceId": _safe(row, "Data.household.clientReferenceId"),
        "tenantId":          _safe(row, "Data.household.tenantId", tenant),
        "rowVersion":        int(_safe(row, "Data.household.rowVersion", 1) or 1),
        "isDeleted":         False,
        "memberCount":       int(_safe(row, "Data.household.memberCount", 1) or 1),
        "address": {
            "tenantId":  _safe(row, "Data.household.address.tenantId", tenant),
            "type":      _safe(row, "Data.household.address.type", "CORRESPONDENCE"),
            # The household index has used both field shapes.
            "locality":  {"code": _safe(row, "Data.household.address.locality.code")
                          or _safe(row, "Data.household.address.localityCode.code")},
            "latitude":  _safe(row, "Data.household.address.latitude"),
            "longitude": _safe(row, "Data.household.address.longitude"),
        },
        "additionalFields": {"schema": "Household", "fields": [], "version": 1},
        "clientAuditDetails": _client_audit(row, "Data.household.clientAuditDetails"),
    }


def build_hh_member(row, tenant):
    return {
        "clientReferenceId":           _safe(row, "Data.householdMember.clientReferenceId"),
        "tenantId":                    _safe(row, "Data.householdMember.tenantId", tenant),
        "householdClientReferenceId":  _safe(row, "Data.householdMember.householdClientReferenceId"),
        "individualClientReferenceId": _safe(row, "Data.householdMember.individualClientReferenceId"),
        "isHeadOfHousehold":           bool(_safe(row, "Data.householdMember.isHeadOfHousehold", False)),
        "rowVersion":                  int(_safe(row, "Data.householdMember.rowVersion", 1) or 1),
        "isDeleted":                   False,
        "additionalFields": {"schema": "HouseholdMember", "fields": [], "version": 1},
        "clientAuditDetails": _client_audit(row, "Data.householdMember.clientAuditDetails"),
    }


def build_individual(row, tenant):
    return {
        "clientReferenceId": _safe(row, "clientReferenceId"),
        "tenantId":          _safe(row, "tenantId", tenant),
        "gender":            _safe(row, "gender"),
        "dateOfBirth":       _safe(row, "dateOfBirth"),
        "isDeleted":         False,
        "rowVersion":        int(_safe(row, "rowVersion", 1) or 1),
        "name": {
            "givenName":  _safe(row, "name.givenName"),
            "familyName": _safe(row, "name.familyName"),
            "otherNames": _safe(row, "name.otherNames"),
        },
        "additionalFields": {"schema": "Individual", "fields": [], "version": 1},
        "clientAuditDetails": _client_audit(row, "clientAuditDetails"),
    }


def build_project_beneficiary(row, tenant):
    return {
        "clientReferenceId":            _safe(row, "clientReferenceId"),
        "beneficiaryClientReferenceId": _safe(row, "beneficiaryClientReferenceId"),
        "tenantId":                     _safe(row, "tenantId", tenant),
        "projectId":                    _safe(row, "projectId"),
        "dateOfRegistration":           _safe(row, "dateOfRegistration"),
        "rowVersion":                   int(_safe(row, "rowVersion", 1) or 1),
        "isDeleted":                    False,
        "clientAuditDetails": _client_audit(row, "clientAuditDetails"),
    }


def build_project_task(row, tenant):
    dose  = _safe(row, "Data.additionalDetails.doseIndex", "")
    cycle = _safe(row, "Data.additionalDetails.cycleIndex", "")
    qty   = _safe(row, "Data.quantity", 1)
    # Reconciliation keys tasks on Data.taskClientReferenceId, so push that id.
    crid = _safe(row, "Data.taskClientReferenceId") or _safe(row, "Data.clientReferenceId")
    return {
        "clientReferenceId":                   crid,
        "tenantId":                            _safe(row, "Data.tenantId", tenant),
        "projectBeneficiaryClientReferenceId": _safe(row, "Data.projectBeneficiaryClientReferenceId"),
        "projectId":                           _safe(row, "Data.projectId"),
        "status":                              _safe(row, "Data.status", "ADMINISTRATION_SUCCESS"),
        "isDeleted":                           False,
        "rowVersion":                          1,
        "resources": [{
            "productVariantId": _safe(row, "Data.productVariant"),
            "quantity":         float(qty) if qty else 1,
            "isDelivered":      True,
            "tenantId":         _safe(row, "Data.tenantId", tenant),
        }],
        "additionalFields": {
            "schema": "HCM_PROJECT_TASK",
            "fields": [
                {"key": "doseIndex",  "value": str(dose)},
                {"key": "cycleIndex", "value": str(cycle)},
            ],
            "version": 1,
        },
        # The task index has no clientAuditDetails, so task ghosts are skipped.
        "clientAuditDetails": _client_audit(row, "Data.clientAuditDetails"),
    }


def build_hf_referral(row, tenant):
    # additionalFields.fields is not reconstructed (best-effort push).
    return {
        "clientReferenceId": _safe(row, "Data.hfReferral.clientReferenceId"),
        "tenantId":          _safe(row, "Data.hfReferral.tenantId", tenant),
        "projectId":         _safe(row, "Data.hfReferral.projectId"),
        "projectFacilityId": _safe(row, "Data.hfReferral.projectFacilityId"),
        "symptom":           _safe(row, "Data.hfReferral.symptom"),
        "symptomSurveyId":   _safe(row, "Data.hfReferral.symptomSurveyId"),
        "beneficiaryId":     _safe(row, "Data.hfReferral.beneficiaryId"),
        "referralCode":      _safe(row, "Data.hfReferral.referralCode"),
        "nationalLevelId":   _safe(row, "Data.hfReferral.nationalLevelId"),
        "isDeleted":         False,
        "rowVersion":        int(_safe(row, "Data.hfReferral.rowVersion", 1) or 1),
        "additionalFields":  {"schema": "HFReferral", "fields": [], "version": 1},
        "clientAuditDetails": _client_audit(row, "Data.hfReferral.clientAuditDetails"),
    }


# Base definitions; tenant, index prefix and service hosts are filled in by get_entities.
_BASE = [
    {
        "name": "Household", "folder": "household", "gap": "A",
        "db_table_suffix": "household", "db_id_col": "clientreferenceid",
        "es_index_suffix": "household-index-v1",
        "es_id_field": "Data.household.clientReferenceId",
        "es_date_field": "Data.household.auditDetails.createdTime", "es_date_mode": "ms",
        "es_tenant_field": "Data.household.tenantId.keyword",
        "es_deleted_field": "Data.household.isDeleted",
        "push_id_col_es": "Data.household.clientReferenceId",
        "search_path": "/household/v1/_search", "search_key": "Household", "resp_key": "Households",
        "create_path": "/household/v1/_create", "create_key": "Household",
        "update_path": "/household/v1/_update", "update_key": "Household",
        "bulk_create_path": "/household/v1/bulk/_create",
        "tracer_body_key": "Households",
        "build": build_household,
    },
    {
        "name": "HouseholdMember", "folder": "household_member", "gap": "B",
        "db_table_suffix": "household_member", "db_id_col": "clientreferenceid",
        "es_index_suffix": "household-member-index-v1",
        "es_id_field": "Data.householdMember.clientReferenceId",
        "es_date_field": "Data.householdMember.auditDetails.createdTime", "es_date_mode": "ms",
        "es_tenant_field": "Data.householdMember.tenantId.keyword",
        "es_deleted_field": "Data.householdMember.isDeleted",
        "push_id_col_es": "Data.householdMember.clientReferenceId",
        "search_path": "/household/member/v1/_search", "search_key": "HouseholdMember", "resp_key": "HouseholdMembers",
        "create_path": "/household/member/v1/_create", "create_key": "HouseholdMember",
        "update_path": "/household/member/v1/_update", "update_key": "HouseholdMember",
        "bulk_create_path": "/household/member/v1/bulk/_create",
        "tracer_body_key": "HouseholdMembers",
        "build": build_hh_member,
    },
    {
        "name": "Individual", "folder": "individual", "gap": "C",
        "db_table_suffix": "individual", "db_id_col": "clientreferenceid",
        "es_index_suffix": "individual-index-v1",
        "es_id_field": "clientReferenceId",
        "es_date_field": "@timestamp", "es_date_mode": "iso",
        "es_tenant_field": "tenantId.keyword",
        "es_deleted_field": "isDeleted",
        "push_id_col_es": "clientReferenceId",
        "search_path": "/individual/v1/_search", "search_key": "Individual", "resp_key": "Individual",
        "create_path": "/individual/v1/_create", "create_key": "Individual",
        "update_path": "/individual/v1/_update", "update_key": "Individual",
        "bulk_create_path": "/individual/v1/bulk/_create",
        "tracer_body_key": "Individuals",
        "build": build_individual,
    },
    {
        "name": "ProjectBeneficiary", "folder": "project_beneficiary", "gap": "D",
        "db_table_suffix": "project_beneficiary", "db_id_col": "clientreferenceid",
        "es_index_suffix": "project-beneficiary-index-v1",
        "es_id_field": "clientReferenceId",
        "es_date_field": "@timestamp", "es_date_mode": "iso",
        "es_tenant_field": "tenantId.keyword",
        "es_deleted_field": "isDeleted",
        "push_id_col_es": "clientReferenceId",
        "search_path": "/project/beneficiary/v1/_search", "search_key": "ProjectBeneficiary", "resp_key": "ProjectBeneficiaries",
        "create_path": "/project/beneficiary/v1/_create", "create_key": "ProjectBeneficiary",
        "update_path": "/project/beneficiary/v1/_update", "update_key": "ProjectBeneficiary",
        "bulk_create_path": "/project/beneficiary/v1/bulk/_create",
        "tracer_body_key": "ProjectBeneficiaries",
        "build": build_project_beneficiary,
    },
    {
        "name": "ProjectTask", "folder": "project_task", "gap": "E",
        "db_table_suffix": "project_task", "db_id_col": "clientreferenceid",
        # ES windows on Data.taskDates (device date), so the DB uses the device clock too.
        "db_date_col": "COALESCE(clientcreatedtime, createdtime)",
        "es_index_suffix": "project-task-index-v1",
        "es_id_field": "Data.taskClientReferenceId",
        "es_date_field": "Data.taskDates", "es_date_mode": "date",
        "es_tenant_field": "Data.tenantId.keyword",
        "es_deleted_field": None,   # task docs have no Data.isDeleted
        "push_id_col_es": "Data.taskClientReferenceId",
        "search_path": "/project/task/v1/_search", "search_key": "Task", "resp_key": "Tasks",
        "create_path": "/project/task/v1/_create", "create_key": "Task",
        "update_path": "/project/task/v1/_update", "update_key": "Task",
        "bulk_create_path": "/project/task/v1/bulk/_create",
        "tracer_body_key": "Tasks",
        "is_task": True,
        "build": build_project_task,
    },
    # HFReferral is SMC-only and disabled: ITN tenants have no hf-referral-index-v1,
    # so reconciliation would 404. Uncomment for SMC tenants.
    # {
    #     "name": "HFReferral", "folder": "hf_referral", "gap": "F",
    #     "db_table_suffix": "hf_referral", "db_id_col": "clientreferenceid",
    #     "es_index_suffix": "hf-referral-index-v1",
    #     "es_id_field": "Data.hfReferral.clientReferenceId",
    #     "es_date_field": "Data.hfReferral.auditDetails.createdTime", "es_date_mode": "ms",
    #     "es_tenant_field": "Data.hfReferral.tenantId.keyword",
    #     "es_deleted_field": "Data.hfReferral.isDeleted",
    #     "push_id_col_es": "Data.hfReferral.clientReferenceId",
    #     "search_path": "/referralmanagement/hf-referral/v1/_search",
    #     "search_key": "HFReferral", "resp_key": "HFReferrals",
    #     "create_path": "/referralmanagement/hf-referral/v1/_create",
    #     "create_key": "HFReferral",
    #     "update_path": "/referralmanagement/hf-referral/v1/_update",
    #     "update_key": "HFReferral",
    #     "bulk_create_path": "/referralmanagement/hf-referral/v1/bulk/_create",
    #     "tracer_body_key": "HFReferrals",
    #     "build": build_hf_referral,
    # },
]


# Services are called in-cluster, bypassing the API gateway. The service name is
# the first path segment; its host comes from overrides or the template.
DEFAULT_SERVICE_TEMPLATE = "http://{service}.egov:8080"


def _service_of(path):
    return path.strip("/").split("/", 1)[0]   # "/household/v1/_create" -> "household"


def get_entities(tenant, task_filter=None, service_template=None, service_overrides=None,
                 index_prefix=None):
    """Return entities with concrete DB tables, ES indices and in-cluster API urls.

    index_prefix: None -> "<tenant>-"; "" -> un-prefixed indices.
    """
    template = service_template or DEFAULT_SERVICE_TEMPLATE
    prefix = f"{tenant}-" if index_prefix is None else index_prefix
    overrides = {k.lower(): str(v).rstrip("/") for k, v in (service_overrides or {}).items()}

    out = []
    for base in _BASE:
        e = dict(base)
        e["tenant"] = tenant
        e["db_table"] = f"{tenant}.{base['db_table_suffix']}"
        e["es_index"] = f"{prefix}{base['es_index_suffix']}"
        # Default server createdtime; the few false ghosts this leaves are caught by
        # the push existence check, so they are never duplicated.
        e["db_date_col"] = base.get("db_date_col", "createdtime")
        for key in ("search_path", "create_path", "update_path", "bulk_create_path"):
            path = base[key]
            svc = _service_of(path)
            host = (overrides.get(svc) or template.format(service=svc)).rstrip("/")
            e[key.replace("_path", "_url")] = host + path
        e["extra_db_where"] = ""
        e["extra_es_filter"] = []
        out.append(e)
    return out
