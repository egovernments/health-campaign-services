package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.bill.Bill;
import org.egov.transformer.models.bill.BillDetail;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.BillDetailIndexV1;
import org.egov.transformer.models.downstream.BillIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class BillTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final BoundaryService boundaryService;
    private final BillService billService;

    private final CommonUtils commonUtils;


    public BillTransformationService(TransformerProperties transformerProperties, Producer producer, UserService userService, BoundaryService boundaryService, BillService billService, CommonUtils commonUtils) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.billService = billService;
        this.commonUtils = commonUtils;
    }

    public void transform(Bill bill) {

        String billTopic = transformerProperties.getTransformerProducerBillIndexV1Topic();
        String billDetailTopic = transformerProperties.getTransformerProducerBillDetailIndexV1Topic();
        log.info("transforming BILL for id {}", bill.getId());

        Map<String, Object> wfStatusInfo =  billService.getWorkflowSummary(bill.getBillNumber(), bill.getTenantId());

        BillIndexV1 billIndexV1 = transformBill(bill, wfStatusInfo);
        producer.push(billTopic, billIndexV1);

        List<BillDetailIndexV1> billDetailIndexV1List = transformBillDetails(bill.getBillDetails(), wfStatusInfo, bill.getLocalityCode());
        producer.push(billDetailTopic, billDetailIndexV1List);
        log.info("transformation successful for BILL for id {}", bill.getId());


    }

    public BillIndexV1 transformBill(Bill bill, Map<String, Object> wfStatusInfo) {
        String localityCode = bill.getLocalityCode();

        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, bill.getTenantId());
        Map<String, String> boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
        Map<String, String> boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();

        Map<String, String> userInfoMap = userService.getUserInfo(bill.getTenantId(), bill.getAuditDetails().getLastModifiedBy());

        BillIndexV1 billIndexV1 = BillIndexV1.builder()
                .bill(bill)
                .wfStatusInfo(wfStatusInfo)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .build();
        commonUtils.addProjectDetailsForUserIdAndTenantId(billIndexV1,
                bill.getAuditDetails().getLastModifiedBy(),
                bill.getTenantId());
        return billIndexV1;
    }

    public List<BillDetailIndexV1> transformBillDetails(List<BillDetail> billDetails, Map<String, Object> wfStatusInfo, String localityCode) {

        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, billDetails.get(0).getTenantId());
        Map<String, String> boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
        Map<String, String> boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();

        List<BillDetailIndexV1> billDetailIndexV1List = new ArrayList<>();
        for  (BillDetail billDetail : billDetails) {
            Map<String, Object> billDetailsWfStatusInfo =  billService.getWorkflowSummary(billDetail.getId(), billDetail.getTenantId());

            Map<String, String> userInfoMap = userService.getUserInfo(billDetail.getTenantId(), billDetail.getAuditDetails().getLastModifiedBy());

            BillDetailIndexV1 billDetailIndexV1 = BillDetailIndexV1.builder()
                    .id(billDetail.getId())
                    .billDetail(billDetail)
                    .wfStatusInfo(billDetailsWfStatusInfo)
                    .billWfStatusInfo(wfStatusInfo)
                    .userName(userInfoMap.get(USERNAME))
                    .nameOfUser(userInfoMap.get(NAME))
                    .role(userInfoMap.get(ROLE))
                    .boundaryHierarchy(boundaryHierarchy)
                    .boundaryHierarchyCode(boundaryHierarchyCode)
                    .build();
            commonUtils.addProjectDetailsForUserIdAndTenantId(billDetailIndexV1,
                    billDetail.getAuditDetails().getLastModifiedBy(),
                    billDetail.getTenantId());

            Boolean billDetailEdited = getEditTimestamp(billDetail.getAdditionalDetails()) != null;
            billDetailIndexV1.setBillDetailEdited(billDetailEdited);

            if (billDetailEdited) {
                billDetailIndexV1.setId(billDetail.getId() + HYPHEN + getEditTimestamp(billDetail.getAdditionalDetails()));
            }

            billDetailIndexV1List.add(billDetailIndexV1);
        }
        return billDetailIndexV1List;
    }

    public String getEditTimestamp(Object additionalDetails) {

        if (additionalDetails == null) return null;
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode editInfo = objectMapper
                .convertValue(additionalDetails, JsonNode.class)
                .path("editInfo");

        if (editInfo.isMissingNode() || editInfo.isNull()) return null;

        JsonNode payables = editInfo.get("payablesUpdatedAtEpochMs");
        if (payables != null && !payables.isNull()) {
            return payables.asText();
        }

        JsonNode payee = editInfo.get("payeeUpdatedAtEpochMs");
        if (payee != null && !payee.isNull()) {
            return payee.asText();
        }

        return null;
    }
}

