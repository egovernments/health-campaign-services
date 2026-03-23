package org.egov.healthnotification;

public class Constants {

    private Constants() {
        throw new IllegalStateException("Constants class");
    }

    // ========== Beneficiary Type Constants ==========
    public static final String BENEFICIARY_TYPE_HOUSEHOLD = "HOUSEHOLD";
    public static final String BENEFICIARY_TYPE_INDIVIDUAL = "INDIVIDUAL";

    // ========== Entity Type Constants ==========
    public static final String ENTITY_TYPE_TASK = "TASK";


    // ========== Recipient Type Constants ==========
    public static final String RECIPIENT_TYPE_HOUSEHOLD_HEAD = "HOUSEHOLD_HEAD";

    // ========== Field/Key Constants ==========
    // Household/Individual fields
    public static final String FIELD_GIVEN_NAME = "givenName";
    public static final String FIELD_MOBILE_NUMBER = "mobileNumber";
    public static final String FIELD_ALT_CONTACT_NUMBER = "altContactNumber";
    public static final String FIELD_EMAIL_ID = "emailId";
    public static final String FIELD_INDIVIDUAL_ID = "individualId";
    public static final String FIELD_CHILD_NAME = "childName";
    public static final String FIELD_DISTRIBUTION_DATE = "distributionDate";

    // Project/Task fields
    public static final String FIELD_PROJECT_TYPE = "projectType";
    public static final String FIELD_BENEFICIARY_TYPE = "beneficiaryType";

    // MDMS fields
    public static final String FIELD_SMS_ENABLED = "smsEnabled";
    public static final String FIELD_EVENT_NOTIFICATIONS = "eventNotifications";
    public static final String FIELD_EVENT_TYPE = "eventType";
    public static final String FIELD_ENABLED = "enabled";
    public static final String FIELD_SCHEDULED_NOTIFICATIONS = "scheduledNotifications";
    public static final String FIELD_RECIPIENT_TYPE = "recipientType";
    public static final String FIELD_PLACEHOLDERS = "placeholders";
    public static final String FIELD_TEMPLATE_CODE = "templateCode";
    public static final String FIELD_LOCALE = "locale";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_TASK_ID = "taskId";
    public static final String FIELD_PROJECT_ID = "projectId";
    public static final String FIELD_ENTITY_TYPE = "entityType";
    public static final String FIELD_DELAY_TYPE = "delayType";
    public static final String FIELD_DELAY_HOURS = "delayHours";
    public static final String FIELD_SEND_TIME = "sendTime";

    // ========== Delay Type Constants ==========
    public static final String DELAY_TYPE_BEFORE_START = "BEFORE_START";
    public static final String DELAY_TYPE_AFTER_EVENT = "AFTER_EVENT";

    // ========== Placeholder Key Constants ==========
    public static final String PLACEHOLDER_HOUSEHOLD_HEAD_NAME = "HouseholdHeadName";
    public static final String PLACEHOLDER_MOBILE_NUMBER = "MobileNumber";
    public static final String PLACEHOLDER_EMAIL_ID = "EmailId";
    public static final String PLACEHOLDER_INDIVIDUAL_ID = "IndividualId";
    public static final String PLACEHOLDER_DISTRIBUTION_DATE = "DistributionDate";
    public static final String PLACEHOLDER_DISTRIBUTED_DATE = "DistributedDate";
    public static final String PLACEHOLDER_DISTRIBUTION_TIME = "DistributionTime";
    public static final String PLACEHOLDER_DISTRIBUTED_TIME = "DistributedTime";
    public static final String PLACEHOLDER_DISTRIBUTION_POINT = "DistributionPoint";
    public static final String PLACEHOLDER_DISTRIBUTED_POINT = "DistributedPoint";
    public static final String PLACEHOLDER_DELIVERY_DATE = "DeliveryDate";
    public static final String PLACEHOLDER_TASK_ID = "TaskId";
    public static final String PLACEHOLDER_TASK_STATUS = "TaskStatus";
    public static final String PLACEHOLDER_CAMPAIGN_NAME = "CampaignName";
    public static final String PLACEHOLDER_PROJECT_TYPE = "ProjectType";
    public static final String PLACEHOLDER_CAMPAIGN_TYPE = "CampaignType";
    public static final String PLACEHOLDER_CHILD_NAME = "ChildName";
    public static final String PLACEHOLDER_ROUND_NUMBER = "RoundNumber";
    public static final String PLACEHOLDER_DOSE_NUMBER = "DoseNumber";
    public static final String PLACEHOLDER_HELPLINE_NUMBER = "HelplineNumber";

    // ========== Error Code Constants ==========
    public static final String ERROR_PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
    public static final String ERROR_UNKNOWN_BENEFICIARY_TYPE = "UNKNOWN_BENEFICIARY_TYPE";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INVALID_TENANT_ID = "INVALID_TENANT_ID";

    // ========== Error Message Constants ==========
    public static final String MSG_PROJECT_NOT_FOUND = "Project not found for projectId: %s, tenantId: %s";
    public static final String MSG_UNKNOWN_BENEFICIARY_TYPE = "Unsupported beneficiaryType: %s. Expected HOUSEHOLD or INDIVIDUAL.";
    public static final String MSG_INVALID_TENANT_ID = "Tenant ID is not valid";

    // ========== Validation Message Constants ==========
    public static final String MSG_TENANT_ID_REQUIRED = "tenantId is required";
    public static final String MSG_ENTITY_ID_REQUIRED = "entityId is required";
    public static final String MSG_ENTITY_TYPE_REQUIRED = "entityType is required";
    public static final String MSG_EVENT_TYPE_REQUIRED = "eventType is required";
    public static final String MSG_TEMPLATE_CODE_REQUIRED = "templateCode is required";
    public static final String MSG_RECIPIENT_ID_REQUIRED = "recipientId is required";
    public static final String MSG_RECIPIENT_TYPE_REQUIRED = "recipientType is required";

    // ========== Task Filtering Constants ==========
    public static final String DELIVERY_STRATEGY_KEY = "deliveryStrategy";
    public static final String DELIVERY_STRATEGY_DIRECT = "DIRECT";

    // ========== Event Notification Constants ==========
    public static final String EVENT_TYPE_SUFFIX_POST_DISTRIBUTION = "_POST_DISTRIBUTION";

    // ========== System User Constants ==========
    public static final String SYSTEM_USER = "SYSTEM";

    // ========== Encryption/Decryption Constants ==========
    public static final String ENCRYPTION_KEY_SCHEDULED_NOTIFICATION = "ScheduledNotification";
    public static final String ENCRYPTION_TYPE_NORMAL = "Normal";
    public static final String DEFAULT_USER_UUID = "no uuid";
    public static final String DEFAULT_USER_TYPE = "EMPLOYEE";
    public static final String CIPHER_TEXT_SEPARATOR = "|";
    public static final String JSON_QUOTE = "\"";

    // ========== Encryption Error Codes ==========
    public static final String ERROR_ENCRYPTION_FAILED = "ENCRYPTION_ERROR";
    public static final String ERROR_ENCRYPTION_NULL = "ENCRYPTION_NULL_ERROR";
    public static final String ERROR_DECRYPTION_FAILED = "DECRYPTION_SERVICE_ERROR";
    public static final String ERROR_DECRYPTION_NULL = "DECRYPTION_NULL_ERROR";

    // ========== Encryption Error Messages ==========
    public static final String MSG_ENCRYPTION_ERROR = "Error occurred in encryption process: ";
    public static final String MSG_ENCRYPTION_NULL = "Null response from encryption service";
    public static final String MSG_DECRYPTION_ERROR = "Error occurred in decryption process: ";
    public static final String MSG_DECRYPTION_NULL = "Null response from decryption service";

    // ========== Date Formatting Constants ==========
    public static final String DATE_FORMAT_YYYY_MM_DD = "yyyy-MM-dd";

    // ========== Schema Routing Constants (additionalFields.schema) ==========
    public static final String SCHEMA_STOCK = "Stock";
    public static final String SCHEMA_HF_REFERRAL = "HFReferral";

    // ========== AdditionalFields Key Constants ==========
    public static final String ADDITIONAL_FIELD_SCHEMA = "schema";
    public static final String ADDITIONAL_FIELD_STOCK_ENTRY_TYPE = "stockEntryType";
    public static final String ADDITIONAL_FIELD_PRIMARY_ROLE = "primaryRole";
    public static final String ADDITIONAL_FIELD_SECONDARY_ROLE = "secondaryRole";
    public static final String ADDITIONAL_FIELD_SKU = "sku";
    public static final String ADDITIONAL_FIELD_MRN_NUMBER = "mrnNumber";
    public static final String ADDITIONAL_FIELD_ADMINISTRATIVE_AREA = "administrativeArea";

    // ========== Stock Entry Type Values (from additionalFields.stockEntryType) ==========
    public static final String STOCK_ENTRY_TYPE_ISSUED = "ISSUED";
    public static final String STOCK_ENTRY_TYPE_RECEIPT = "RECEIPT";
    public static final String STOCK_ENTRY_TYPE_RETURNED = "RETURNED";
    public static final String STOCK_ENTRY_TYPE_DAMAGED = "DAMAGED";
    public static final String STOCK_ENTRY_TYPE_RETURN_ACCEPTED = "RETURN_ACCEPTED";
    public static final String STOCK_ENTRY_TYPE_RETURN_REJECTED = "RETURN_REJECTED";

    // ========== Role Values (from additionalFields.primaryRole / secondaryRole) ==========
    public static final String ROLE_SENDER = "SENDER";
    public static final String ROLE_RECEIVER = "RECEIVER";

    // ========== Entity Type Constants (Push) ==========
    public static final String ENTITY_TYPE_STOCK = "STOCK";
    public static final String ENTITY_TYPE_HF_REFERRAL = "HF_REFERRAL";

    // ========== Notification Type Constants (for UI routing in data payload) ==========
    public static final String NOTIFICATION_TYPE_STOCK = "STOCK";
    public static final String NOTIFICATION_TYPE_REFERRAL = "REFERRAL";

    // ========== Stock Event Types (mapped from stockEntryType, aligned with MDMS eventType) ==========
    public static final String EVENT_TYPE_STOCK_ISSUE = "STOCK_ISSUE_PUSH_NOTIFICATION";
    public static final String EVENT_TYPE_STOCK_RECEIPT = "STOCK_RECEIVE_PUSH_NOTIFICATION";
    public static final String EVENT_TYPE_STOCK_REVERSE_ISSUE = "STOCK_REVERSE_ISSUE_PUSH_NOTIFICATION";
    public static final String EVENT_TYPE_STOCK_REVERSE_ACCEPT = "STOCK_REVERSE_ACCEPT_PUSH_NOTIFICATION";
    public static final String EVENT_TYPE_STOCK_REVERSE_REJECT = "STOCK_REVERSE_REJECT_PUSH_NOTIFICATION";

    // ========== Stock Placeholder Keys (matching MDMS template variables) ==========
    public static final String PLACEHOLDER_SENDING_FACILITY = "Sending_Facility_Name";
    public static final String PLACEHOLDER_RECEIVING_FACILITY = "Receiving_Facility_Name";
    public static final String PLACEHOLDER_TRANSACTION_ID = "Transaction_ID";
    public static final String PLACEHOLDER_QUANTITY_OF_SKU = "quantity_of_sku";

    // ========== MDMS Campaign Type for Push Notifications ==========
    public static final String CAMPAIGN_TYPE_PUSH_NOTIFICATION = "PUSH-NOTIFICATION";

    // ========== Stock Screen Navigation Constants ==========
    public static final String SCREEN_PENDING_RECEIPT = "PENDING_RECEIPT_SCREEN";
    public static final String SCREEN_TRANSACTION_DETAILS = "TRANSACTION_DETAILS";
    public static final String SCREEN_RECORD_RECEIPT = "RECORD_RECEIPT_SCREEN";

    // ========== Push Notification Error Codes ==========
    public static final String ERROR_PUSH_NOTIFICATION_FAILED = "PUSH_NOTIFICATION_FAILED";
    public static final String ERROR_STOCK_EVENT_PROCESSING_FAILED = "STOCK_EVENT_PROCESSING_FAILED";
}
