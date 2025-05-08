package org.egov.individual.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.service.IdGenService;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class IdGenUtil {

    private final IdGenService idGenService;

    public IdGenUtil(IdGenService idGenService) {
        this.idGenService = idGenService;
    }

    public static List<IdRecord> convertIdsToIdRecords (List<String> ids, String tenantId , RequestInfo requestInfo, String status) {

        return ids.stream().map(id ->
                {
                    String updateStatus = "";
                    if (StringUtils.isNotBlank(status)) updateStatus = status;

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
                }

        ).collect(Collectors.toList());
    }

    public void updateBeneficiaryIds(List<String> beneficiaryIds , String tenantId, RequestInfo requestInfo) {
        if (!ObjectUtils.isEmpty(beneficiaryIds)) {

            List<IdRecord> idRecordsToUpdate = convertIdsToIdRecords(beneficiaryIds, tenantId, requestInfo, IdStatus.ASSIGNED.name());
            idGenService.updateIdRecord(idRecordsToUpdate,requestInfo);
        }
    }
}
