package org.egov.transformer.transformationservice;

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

@Component
@Slf4j
public class ExpenseBillTransformationService {
    private final BoundaryService boundaryService;
    private final ProjectService projectService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;

    public ExpenseBillTransformationService(BoundaryService boundaryService, ProjectService projectService, TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils) {
        this.boundaryService = boundaryService;
        this.projectService = projectService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
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
            getUniqueUserCountInBill(bill.getBillDetails());
        }
        //TODO: Add project id and project type id in additional details
        return ExpenseBillIndexV1.builder()
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .totalUsersCount(bill.getBillDetails().size())
                .uniqueUsersCount(uniqueUsersCount)
                .taskDates(commonUtils.getDateFromEpoch(bill.getAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(bill.getAuditDetails().getLastModifiedTime()))
                .build();
    }

    private int getUniqueUserCountInBill(List<BillDetail> billDetails){
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


}
