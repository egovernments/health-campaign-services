package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.Project.ProjectInfo;
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
        String tenantId = service.getTenantId();
        String projectTypeId = null;
        ProjectInfo projectInfo = commonUtils.projectInfoFromUserId(service.getAuditDetails().getLastModifiedBy(), tenantId);
        if(projectInfo!=null){
            projectTypeId = projectInfo.getProjectTypeId();
        }
        String localityCode = Optional.ofNullable(service.getAddress())
                .map(Address::getLocality)
                .map(Boundary::getCode)
                .map(this::extractLeafLocalityCode)
                .orElse(null);

        BoundaryHierarchyResult boundaryHierarchyResult = resolveBoundaryHierarchy(localityCode, projectInfo, tenantId);
        Map<String, String> boundaryHierarchy = boundaryHierarchyResult == null ? null : boundaryHierarchyResult.getBoundaryHierarchy();
        Map<String, String> boundaryHierarchyCode = boundaryHierarchyResult == null ? null : boundaryHierarchyResult.getBoundaryHierarchyCode();
        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, service.getAuditDetails().getCreatedBy());

        service.setAddress(null); //explicitly setting it to null as it is not needed
//      commenting below code as it is not needed now

//        service.setApplicationStatus(mdmsService.getMDMSTransformerLocalizations(service.getApplicationStatus(), tenantId));
//        service.setServiceCode(mdmsService.getMDMSTransformerLocalizations(service.getServiceCode(), tenantId));

        ObjectNode additionalDetails = objectMapper.createObjectNode();

        String cycleIndex = commonUtils.fetchCycleIndexFromTime(service.getTenantId(), projectTypeId, service.getAuditDetails().getCreatedTime());
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        additionalDetails.put(PROJECT_TYPE_ID, projectTypeId);

        PGRIndex pgrIndex = PGRIndex.builder()
                .service(service)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .taskDates(commonUtils.getDateFromEpoch(service.getAuditDetails().getLastModifiedTime()))
                .localityCode(localityCode)
                .additionalDetails(additionalDetails)
                .build();
        return pgrIndex;
    }

    /**
     * Resolves the boundary hierarchy from the complaint's own locality when it has one, otherwise from
     * the project of the user who raised it. Returns null when neither is available - the user has no
     * project staff mapping, so there is no boundary to index against.
     */
    private BoundaryHierarchyResult resolveBoundaryHierarchy(String localityCode, ProjectInfo projectInfo, String tenantId) {
        if (localityCode != null) {
            return boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId);
        }
        if (projectInfo != null && projectInfo.getProjectId() != null) {
            return boundaryService.getBoundaryHierarchyWithProjectId(projectInfo.getProjectId(), tenantId);
        }
        log.info("No locality code on the service and no project resolved for its user. Skipping boundary hierarchy.");
        return null;
    }

    /**
     * Locality codes arrive dot-qualified (for example "TD.CITY.WARD"); only the trailing segment is a
     * boundary code.
     */
    private String extractLeafLocalityCode(String qualifiedLocalityCode) {
        String[] parts = qualifiedLocalityCode.split("\\.");
        return parts.length == 0 ? qualifiedLocalityCode : parts[parts.length - 1];
    }

}
