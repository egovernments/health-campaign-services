package org.egov.transformer.transformationservice;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.bill.*;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.BillReportIndexV1;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BillService;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class BillReportTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final UserService userService;
    private final BoundaryService boundaryService;
    private final BillService billService;

    private final CommonUtils commonUtils;


    public BillReportTransformationService(TransformerProperties transformerProperties, Producer producer, UserService userService, BoundaryService boundaryService, BillService billService, CommonUtils commonUtils) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.userService = userService;
        this.boundaryService = boundaryService;
        this.billService = billService;
        this.commonUtils = commonUtils;
    }

    public void transform(BillReport billReport) {
        String billReportTopic = transformerProperties.getTransformerProducerBillReportIndexV1Topic();
        log.info("transforming BILL REPORT for id {}", billReport.getId());
        BillReportIndexV1 billReportIndexV1 = transformBillReport(billReport);
        producer.push(billReportTopic, billReportIndexV1);
    }

    public BillReportIndexV1 transformBillReport(BillReport billReport) {

        String billId = billReport.getBillId();
        Long generationTime = null;

        String billNumber = billService.getBillNumber(billId, billReport.getTenantId());
        if (billNumber != null) {
            ProcessInstance processInstance = billService.getLatestProcessInstance(billNumber, billReport.getTenantId());
            if (processInstance != null
                    && processInstance.getState() != null
                    && Objects.equals(Status.REVIEWED.toString(), processInstance.getState().getState())
                    && billReport.getFileStoreId() != null
                    && ReportStatus.GENERATED == billReport.getStatus()
                    && ReportType.PAYMENT_ADVISORY_EXCEL == billReport.getType()) {
                generationTime = findTimeDifference(billReport.getAuditDetails().getLastModifiedTime(), processInstance.getAuditDetails().getCreatedTime());
            }
        }

        String createdBy = billReport.getAuditDetails() != null ? billReport.getAuditDetails().getCreatedBy() : null;
        String lastModifiedBy = billReport.getAuditDetails() != null ? billReport.getAuditDetails().getLastModifiedBy() : null;

        Map<String, String> userInfoMap = userService.getUserInfo(billReport.getTenantId(), createdBy);
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();

        ProjectInfo projectInfo = commonUtils.projectDetailsFromUserId(createdBy, billReport.getTenantId());
        if (ObjectUtils.isNotEmpty(projectInfo) && StringUtils.isNotEmpty(projectInfo.getProjectId())) {
            boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectInfo.getProjectId(), billReport.getTenantId());
        }

        BillReportIndexV1 billReportIndexV1 = BillReportIndexV1.builder()
                .billReport(billReport)
                .billReportGenerationTime(generationTime)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .build();
        commonUtils.addProjectDetailsForUserIdAndTenantId(billReportIndexV1, lastModifiedBy, billReport.getTenantId());
        return billReportIndexV1;
    }

    private Long findTimeDifference(Long billReportTime, Long billUpdatedTime) {
        return (billReportTime - billUpdatedTime) / (1000 * 60) ;
    }
}

