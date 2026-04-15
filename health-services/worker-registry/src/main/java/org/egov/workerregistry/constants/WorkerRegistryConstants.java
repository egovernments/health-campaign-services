package org.egov.workerregistry.constants;

public class WorkerRegistryConstants {

    private WorkerRegistryConstants() {}

    // Encryption keys
    public static final String ENCRYPT_WORKER = "WorkerEncrypt";
    public static final String ENCRYPT_WORKER_SEARCH = "WorkerSearchEncrypt";
    public static final String DECRYPT_WORKER = "WorkerDecrypt";

    // Attendance document types
    public static final String DOCUMENT_TYPE_SIGNATURE = "SIGNATURE";
    public static final String DOCUMENT_TYPE_PHOTO = "PHOTO";

    // Error codes
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INVALID_TENANT_EXCEPTION = "INVALID_TENANT_EXCEPTION";
    public static final String INVALID_TENANT_ID_EXCEPTION = "INVALID_TENANT_ID_EXCEPTION";
    public static final String INVALID_TENANT_ID = "INVALID_TENANT_ID";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    // Error messages
    public static final String MSG_TENANT_ID_NOT_VALID = "The tenant id is not valid";
    public static final String MSG_TENANT_ID_REQUIRED = "TenantId is required for search";
    public static final String MSG_INVALID_TENANT_ID_PREFIX = "Invalid tenant id: ";
}
