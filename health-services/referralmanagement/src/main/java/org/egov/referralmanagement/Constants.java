package org.egov.referralmanagement;

public interface Constants {

    String SET_SIDE_EFFECTS = "setSideEffects";
    String GET_SIDE_EFFECTS = "getSideEffects";
    String SET_REFERRALS = "setReferrals";
    String GET_REFERRALS = "getReferrals";
    String SET_HF_REFERRALS = "setHfReferrals";
    String GET_HF_REFERRALS = "getHfReferrals";
    String VALIDATION_ERROR = "VALIDATION_ERROR";
    String PROJECT_TYPES = "projectTypes";
    String MDMS_RESPONSE = "MdmsRes";
    String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    String GET_ID = "getId";
    String STAFF = "STAFF";
    String FACILITY = "FACILITY";
    String FACILITYE="FACILITE";

    public static final String HCM_MASTER_PROJECTTYPE = "projectTypes";
    public static final String HCM_MDMS_PROJECT_MODULE_NAME = "HCM-PROJECT-TYPES";
    public static final String HCM_PROJECT_TYPE_FILTER_CODE = "$.[?(@.code=='%s')]";
    public static final String HCM_MDMS_PROJECTTYPE_RES_PATH = "$.MdmsRes." + HCM_MDMS_PROJECT_MODULE_NAME + "." + HCM_MASTER_PROJECTTYPE + ".*";

    String INVALID_RECIPIENT_TYPE = "Invalid Recipient Type";
}
