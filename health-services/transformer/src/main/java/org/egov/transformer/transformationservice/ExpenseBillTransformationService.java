package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.ExpenseBillIndexV1;
import org.egov.transformer.models.expense.Bill;
import org.egov.transformer.models.expense.BillDetail;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.egov.transformer.Constants.*;

@Component
@Slf4j
public class ExpenseBillTransformationService {
    private final BoundaryService boundaryService;
    private final ProjectService projectService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;

    public ExpenseBillTransformationService(BoundaryService boundaryService, ProjectService projectService, TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, ObjectMapper objectMapper) {
        this.boundaryService = boundaryService;
        this.projectService = projectService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(Bill bill) {
        String topic = transformerProperties.getTransformerProducerExpenseBillIndexV1Topic();
        log.info("transforming bill for billNumber {}", bill.getBillNumber());
        ExpenseBillIndexV1 expenseBillIndexV1 = transformBill(bill);
        log.info("transformation success for billNumber {}", expenseBillIndexV1.getBill().getBillNumber());
        producer.push(topic, expenseBillIndexV1);
    }

    private ExpenseBillIndexV1 transformBill(Bill bill) {
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        String boundaryCode = bill.getLocalityCode();
        if (StringUtils.isNotBlank(boundaryCode)) {
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(boundaryCode, bill.getTenantId());
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        }
        Integer uniqueUsersCount = 0;
        if (!CollectionUtils.isEmpty(bill.getBillDetails())) {
            uniqueUsersCount = getUniqueUserCountInBill(bill.getBillDetails());
        }
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (StringUtils.isNotBlank(bill.getReferenceId())) {
            addProjectDetailsInAdditionalDetails(bill.getReferenceId(), bill.getTenantId(), additionalDetails);
        }
        return ExpenseBillIndexV1.builder()
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .totalUsersCount(bill.getBillDetails().size())
                .uniqueUsersCount(uniqueUsersCount)
                .taskDates(commonUtils.getDateFromEpoch(bill.getAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(bill.getAuditDetails().getLastModifiedTime()))
                .build();
    }

    private Integer getUniqueUserCountInBill(List<BillDetail> billDetails){
        Set<String> uniqueUsers = new HashSet<>();
        for (BillDetail billDetail : billDetails) {
            if (ObjectUtils.isNotEmpty(billDetail) && ObjectUtils.isNotEmpty(billDetail.getPayee())) {
                String identifier = billDetail.getPayee().getIdentifier();
                if (StringUtils.isNotBlank(identifier)) {
                    uniqueUsers.add(billDetail.getPayee().getIdentifier());
                }
            }
        }
        return uniqueUsers.size();
    }

    private void addProjectDetailsInAdditionalDetails(String referenceId, String tenantId, ObjectNode additionalDetails) {
        String[] parts = referenceId.split("\\.");
        //Extracting the last level of project from the project hierarchy
        String projectId = parts[parts.length - 1];
        String projectTypeId = projectService.getProjectTypeIdFromProjectId(projectId, tenantId);
        additionalDetails.put(PROJECT_ID, projectId);
        additionalDetails.put(PROJECT_TYPE_ID, projectTypeId);
    }


}
