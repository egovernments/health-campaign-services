package org.egov.individual.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.service.BeneficiaryIdGenService;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for working with ID generation and updating beneficiary ID statuses.
 */
@Slf4j
@Component
public class BeneficiaryIdGenUtil {

    private final BeneficiaryIdGenService beneficiaryIdGenService;

    /**
     * Constructor for injecting the BeneficiaryIdGenService dependency.
     *
     * @param beneficiaryIdGenService Service used to interact with the ID generation system
     */
    public BeneficiaryIdGenUtil(BeneficiaryIdGenService beneficiaryIdGenService) {
        this.beneficiaryIdGenService = beneficiaryIdGenService;
    }

    /**
     * Converts a list of string IDs to a list of IdRecord objects with associated metadata.
     *
     * @param ids       List of raw ID strings
     * @param tenantId  Tenant ID to associate with each record
     * @param requestInfo Request information for setting audit details
     * @param status    Status to be set for each ID record (optional)
     * @return          List of IdRecord objects ready for processing or updating
     */
    public static List<IdRecord> convertIdsToIdRecords(List<String> ids, String tenantId, RequestInfo requestInfo, String status) {

        return ids.stream().map(id -> {
            String updateStatus = StringUtils.isBlank(status) ? "" : status;

            return IdRecord.builder()
                    .id(id)
                    .tenantId(tenantId)
                    .status(updateStatus)
                    .rowVersion(1)
                    .source(null)
                    .applicationId(null)
                    .hasErrors(false)
                    .additionalFields(null)
                    .auditDetails(
                            AuditDetails.builder()
                                    .createdBy(requestInfo.getUserInfo().getUuid())
                                    .createdTime(System.currentTimeMillis())
                                    .build()
                    )
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Updates the status of a list of beneficiary IDs to "ASSIGNED" in the ID generation system.
     *
     * @param beneficiaryIds List of beneficiary ID strings
     * @param tenantId       Tenant ID for the records
     * @param requestInfo    Request metadata for the update operation
     */
    public void updateBeneficiaryIds(List<String> beneficiaryIds, String tenantId, RequestInfo requestInfo) {
        if (!ObjectUtils.isEmpty(beneficiaryIds)) {
            // Convert raw IDs into IdRecord objects with ASSIGNED status
            List<IdRecord> idRecordsToUpdate = convertIdsToIdRecords(beneficiaryIds, tenantId, requestInfo, IdStatus.ASSIGNED.name());

            // Call the ID generation service to update these records
            beneficiaryIdGenService.updateIdRecord(idRecordsToUpdate, requestInfo);
        }
    }
}
