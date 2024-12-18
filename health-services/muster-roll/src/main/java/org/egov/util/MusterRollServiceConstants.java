package org.egov.util;

public class MusterRollServiceConstants {

    public static final String ACTION_REJECT = "REJECT";
    public static final String MASTER_TENANTS = "tenants";
    public static final String FILTER_CODE = "$.*.code";
    public static final String MDMS_TENANT_MODULE_NAME = "tenant";
    public static final String MDMS_COMMON_MASTERS_MODULE_NAME = "common-masters";
    public static final String MASTER_MUSTER_ROLL = "MusterRoll";
    public static final String MASTER_WAGER_SEEKER_SKILLS = "WageSeekerSkills";
    public static final String HALF_DAY_NUM_HOURS = "HALF_DAY_NUM_HOURS";
    public static final String FULL_DAY_NUM_HOURS = "FULL_DAY_NUM_HOURS";
    public static final String ROUND_OFF_HOURS = "ROUND_OFF_HOURS";
    public static final String ENTRY_EVENT = "ENTRY";
    public static final String EXIT_EVENT = "EXIT";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String TENANT_ID = "tenantId";
    public static final String CONTRACT_NUMBER = "contractNumber";
    public static final String REQUEST_INFO = "RequestInfo";
    public static final String ORG_ID_PATH = "$.contracts.*.orgId";
    public static final String ID = "id";
    public static final String ORG_NAME_PATH = "$.organisations.*.name";
    public static final String CONTACT_NAME_PATH = "$.organisations.*.contactDetails.*.contactName";
    public static final String CONTACT_MOBILE_NUMBER_PATH = "$.organisations.*.contactDetails.*.contactMobileNumber";
    public static final String ORG_NAME = "orgName";
    public static final String CONTACT_NAME = "contactName";
    public static final String CONTACT_MOBILE_NUMBER = "contactMobileNumber";
    public static final String MUSTER_ROLL_MODULE_CODE = "rainmaker-common-masters";
    public static final String SEARCH_CRITERIA = "SearchCriteria";
    public static final String MUSTER_ROLL_LOCALIZATION_CODE_JSONPATH = "$.messages.*.code";
    public static final String MUSTER_ROLL_LOCALIZATION_MESSAGE_JSONPATH = "$.messages.*.message";
    public static final String WF_SEND_BACK_TO_CBO_CODE = "SENDBACKTOCBO";
    public static final String WF_APPROVE_CODE = "APPROVE";
    public static final String CBO_NOTIFICATION_FOR_CORRECTION_LOCALIZATION_CODE = "MUSTER_ROLL_CBO_NOTIFICATION_FOR_CORRECTION";
    public static final String CBO_NOTIFICATION_OF_APPROVAL_LOCALIZATION_CODE = "MUSTER_ROLL_CBO_NOTIFICATION_OF_APPROVAL";
}
