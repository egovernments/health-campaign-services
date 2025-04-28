package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.PGRIndex;
import org.egov.transformer.models.pgr.Service;
import org.egov.transformer.models.pgr.Address;
import org.egov.transformer.models.pgr.Boundary;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.BoundaryService;
import org.egov.transformer.service.MdmsService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class PGRTransformationService {
    private final UserService userService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private final MdmsService mdmsService;
    private final BoundaryService boundaryService;
    private final ObjectMapper objectMapper;

    public PGRTransformationService(UserService userService, TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, MdmsService mdmsService, BoundaryService boundaryService, ObjectMapper objectMapper) {

        this.userService = userService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.mdmsService = mdmsService;
        this.boundaryService = boundaryService;
        this.objectMapper = objectMapper;
    }

    public void transform(List<Service> pgrList) {
        log.info("transforming for PGR id's {}", pgrList.stream()
                .map(Service::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerCreatePgrTopic();
        List<PGRIndex> pgrIndexList = pgrList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for PGR id's {}", pgrIndexList.stream()
                .map(PGRIndex::getService)
                .map(Service::getId)
                .collect(Collectors.toList()));
        producer.push(topic, pgrIndexList);
    }

    private PGRIndex transform(Service service) {
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        String tenantId = service.getTenantId();
        String localityCode = null;
        String dpName = null;
        String projectTypeId = null;
        Optional<String> localityCodeOptional = Optional.ofNullable(service)
                .map(Service::getAddress)
                .map(Address::getLocality)
                .map(Boundary::getCode);
        if (localityCodeOptional.isPresent()) {
            localityCode = localityCodeOptional.get();
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            String dpCode = localityCode + "_DP";
            dpName = boundaryService.getLocalizedBoundaryName(dpCode, null, tenantId);
        }
        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, service.getAuditDetails().getCreatedBy());

        service.setAddress(null); //explicitly setting it to null as it is not needed
//      commenting below code as it is not needed now

//        service.setApplicationStatus(mdmsService.getMDMSTransformerLocalizations(service.getApplicationStatus(), tenantId));
//        service.setServiceCode(mdmsService.getMDMSTransformerLocalizations(service.getServiceCode(), tenantId));

        ObjectNode additionalDetails = objectMapper.createObjectNode();
        String projectIdProjectTypeId = commonUtils.projectDetailsFromUserId(service.getAuditDetails().getLastModifiedBy(), tenantId);

        if (!StringUtils.isEmpty(projectIdProjectTypeId)) {
            projectTypeId = projectIdProjectTypeId.split(":")[1];
        }
        String cycleIndex = commonUtils.fetchCycleIndexFromTime(service.getTenantId(), projectTypeId, service.getAuditDetails().getCreatedTime());
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        additionalDetails.put(PROJECT_TYPE_ID, projectTypeId);

        PGRIndex pgrIndex = PGRIndex.builder()
                .service(service)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(dpName)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .taskDates(commonUtils.getDateFromEpoch(service.getAuditDetails().getLastModifiedTime()))
                .localityCode(localityCode)
                .additionalDetails(additionalDetails)
                .build();
        return pgrIndex;
    }

}
