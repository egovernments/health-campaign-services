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
import org.egov.transformer.models.downstream.ProjectInfo;
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
//        String billDetailTopic = transformerProperties.getTransformerProducerBillDetailIndexV1Topic();
        log.info("transforming BILL for id {}", bill.getId());

        Map<String, Object> wfStatusInfo =  billService.getWorkflowSummary(bill.getBillNumber(), bill.getTenantId());

        BillIndexV1 billIndexV1 = transformBill(bill, wfStatusInfo);
        producer.push(billTopic, billIndexV1);

//        List<BillDetailIndexV1> billDetailIndexV1List = transformBillDetails(bill.getBillDetails(), wfStatusInfo, bill.getLocalityCode());
//        producer.push(billDetailTopic, billDetailIndexV1List);
        log.info("transformation successful for BILL for id {}", bill.getId());


    }

    public void transform(BillDetail billDetail) {
        Bill bill = billService.searchBill(billDetail.getBillId(), billDetail.getTenantId());
        String billDetailTopic = transformerProperties.getTransformerProducerBillDetailIndexV1Topic();
        log.info("transforming BILL DETAIL for id {}", billDetail.getId());

        Map<String, Object> wfStatusInfo =  billService.getWorkflowSummary(bill.getBillNumber(), billDetail.getTenantId());

        List<BillDetailIndexV1> billDetailIndexV1List = transformBillDetails(Collections.singletonList(billDetail), wfStatusInfo, bill.getLocalityCode());
        producer.push(billDetailTopic, billDetailIndexV1List);
        log.info("transformation successful for BILL DETAIL for id {}", billDetail.getId());


    }
    public BillIndexV1 transformBill(Bill bill, Map<String, Object> wfStatusInfo) {
        String localityCode = bill.getLocalityCode();
        ProjectInfo projectInfo = commonUtils.projectDetailsFromUserId(bill.getAuditDetails().getCreatedBy(),bill.getTenantId());
        String hierarchyType = projectInfo.getHierarchyType();

        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, bill.getTenantId(),hierarchyType);
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
        ProjectInfo projectInfo = commonUtils.projectDetailsFromUserId(billDetails.get(0).getAuditDetails().getCreatedBy(),billDetails.get(0).getTenantId());
        String hierarchyType = projectInfo.getHierarchyType();

        BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, billDetails.get(0).getTenantId(),hierarchyType);
        Map<String, String> boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
        Map<String, String> boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();

        List<BillDetailIndexV1> billDetailIndexV1List = new ArrayList<>();
        for  (BillDetail billDetail : billDetails) {
            Map<String, Object> billDetailsWfStatusInfo =  billService.getWorkflowSummary(billDetail.getId(), billDetail.getTenantId());

            Map<String, String> userInfoMap = userService.getUserInfo(billDetail.getTenantId(), billDetail.getAuditDetails().getLastModifiedBy());

            BillDetailIndexV1 original = buildBillDetailIndex(
                    billDetail,
                    wfStatusInfo,
                    billDetailsWfStatusInfo,
                    userInfoMap,
                    boundaryHierarchy,
                    boundaryHierarchyCode
            );

            original.setBillDetailEdited(false);
            billDetailIndexV1List.add(original);
            String editTimestamp = getEditTimestamp(billDetail.getAdditionalDetails());

            if (editTimestamp != null) {
                BillDetailIndexV1 editedCopy = buildBillDetailIndex(
                        billDetail,
                        wfStatusInfo,
                        billDetailsWfStatusInfo,
                        userInfoMap,
                        boundaryHierarchy,
                        boundaryHierarchyCode
                );
                editedCopy.setBillDetailEdited(true);
                editedCopy.setId(billDetail.getId() + HYPHEN + editTimestamp);
                billDetailIndexV1List.add(editedCopy);
            }
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

    private BillDetailIndexV1 buildBillDetailIndex(
            BillDetail billDetail,
            Map<String, Object> wfStatusInfo,
            Map<String, Object> billDetailsWfStatusInfo,
            Map<String, String> userInfoMap,
            Map<String, String> boundaryHierarchy,
            Map<String, String> boundaryHierarchyCode) {

        BillDetailIndexV1 index = BillDetailIndexV1.builder()
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

        commonUtils.addProjectDetailsForUserIdAndTenantId(
                index,
                billDetail.getAuditDetails().getLastModifiedBy(),
                billDetail.getTenantId()
        );

        return index;
    }
}

