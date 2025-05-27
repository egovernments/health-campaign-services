package org.egov.common.utils;

import lombok.extern.slf4j.Slf4j;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.tracer.model.CustomException;

import java.util.List;

@Slf4j
public class ValidatorUtils {

    private ValidatorUtils() {}

    public static Error getErrorForRowVersionMismatch() {
        return Error.builder().errorMessage("Row version mismatch").errorCode("MISMATCHED_ROW_VERSION")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("MISMATCHED_ROW_VERSION", "Row version mismatch")).build();
    }

    public static Error getErrorForNullId() {
        return Error.builder().errorMessage("Id cannot be null").errorCode("NULL_ID")
                .type(Error.ErrorType.RECOVERABLE)
                .exception(new CustomException("NULL_ID", "Id cannot be null")).build();
    }

    public static Error getErrorForAddressType() {
        return Error.builder().errorMessage("Invalid address").errorCode("INVALID_ADDRESS")
                .type(Error.ErrorType.RECOVERABLE)
                .exception(new CustomException("INVALID_ADDRESS", "Invalid address")).build();
    }

    public static Error getErrorForNonExistentEntity() {
        return Error.builder().errorMessage("Entity does not exist in db")
                .errorCode("NON_EXISTENT_ENTITY")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("NON_EXISTENT_ENTITY", "Entity does not exist in db")).build();
    }

    public static Error getErrorForNonExistentRelatedEntity(List<String> ids) {
        return Error.builder().errorMessage(String.format("Related entity does not exist in db %s", ids))
                .errorCode("NON_EXISTENT_RELATED_ENTITY")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("NON_EXISTENT_RELATED_ENTITY", "Related entity does not exist in db")).build();
    }

    public static Error getErrorForNonExistentRelatedEntity(String id) {
        return Error.builder().errorMessage(String.format("Related entity does not exist in db %s", id))
                .errorCode("NON_EXISTENT_RELATED_ENTITY")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("NON_EXISTENT_RELATED_ENTITY", "Related entity does not exist in db")).build();
    }

    public static Error getErrorForInvalidRelatedEntityID() {
        return Error.builder().errorMessage("Invalid related entity ID in request")
                .errorCode("INVALID_RELATED_ENTITY_ID")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("INVALID_RELATED_ENTITY_ID", "Invalid related entity ID in request")).build();
    }

    public static Error getErrorForEntityWithNetworkError() {
        return Error.builder().errorMessage("Network error")
                .errorCode("NETWORK_ERROR")
                .type(Error.ErrorType.RECOVERABLE)
                .exception(new CustomException("NETWORK_ERROR", "Network error")).build();
    }

    public static Error getErrorForNonExistentSubEntity(String subEntityId) {
        return Error.builder().errorMessage(String.format("Sub Entity does not exist in db for %s", subEntityId))
                .errorCode("NON_EXISTENT_SUB_ENTITY")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("NON_EXISTENT_SUB_ENTITY",
                        String.format("Sub Entity does not exist in db for %s", subEntityId))).build();
    }

    public static Error getErrorForUniqueEntity() {
        return Error.builder().errorMessage("Duplicate entity")
                .errorCode("DUPLICATE_ENTITY")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("DUPLICATE_ENTITY", "Duplicate entity")).build();
    }

    public static Error getErrorForDuplicateMapping(String entityParent, String entityChild) {
        return Error.builder().errorMessage(String.format("Duplicate entity %s, %s", entityParent, entityChild))
                .errorCode("DUPLICATE_ENTITY")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("DUPLICATE_ENTITY",
                        String.format("Duplicate entity %s, %s", entityParent, entityChild))).build();
    }

    public static Error getErrorForUniqueSubEntity() {
        return Error.builder().errorMessage("Duplicate sub entity")
                .errorCode("DUPLICATE_SUB_ENTITY")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("DUPLICATE_SUB_ENTITY", "Duplicate sub entity"))
                .build();
    }

    public static Error getErrorForIsDelete() {
        return Error.builder().errorMessage("isDeleted cannot be true")
                .errorCode("IS_DELETED_TRUE")
                .type(Error.ErrorType.RECOVERABLE)
                .exception(new CustomException("IS_DELETED_TRUE", "isDeleted cannot be true"))
                .build();
    }

    public static Error getErrorForIsDeleteSubEntity() {
        return Error.builder()
                .errorMessage("isDeleted cannot be true for sub entity")
                .errorCode("IS_DELETED_TRUE_SUB_ENTITY")
                .type(Error.ErrorType.RECOVERABLE)
                .exception(new CustomException("IS_DELETED_TRUE_SUB_ENTITY", "isDeleted cannot be true for sub entity"))
                .build();
    }

    /**
     * Builds the Error object for tenant and given exception
     *
     * @param tenantId              The id of the tenant
     * @param exception             The invalid tenant id exception object
     * @return the error object for the tenant id with invalid tenant id exception
     */
    public static Error getErrorForInvalidTenantId(String tenantId, InvalidTenantIdException exception) {
        return Error.builder()
                .errorMessage(String.format("tenantId : %s is not valid", tenantId))
                .errorCode("TENANT_ID_INVALID")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(exception)
                .build();
    }

}
