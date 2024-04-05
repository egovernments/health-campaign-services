package org.egov.transformer.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.PGRIndex;
import org.egov.transformer.models.pgr.Service;
import org.egov.transformer.models.pgr.Address;
import org.egov.transformer.models.pgr.Boundary;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class PGRTransformationService {

    private final ProjectService projectService;
    private final UserService userService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private static final HashMap<String, String> translations = null;


    public PGRTransformationService(ProjectService projectService, UserService userService, TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils) {

        this.projectService = projectService;
        this.userService = userService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
    }

    public void transform(List<Service> pgrList) {
        List<PGRIndex> pgrIndexList = new ArrayList<>();
        String topic = transformerProperties.getTransformerProducerCreatePgrTopic();
        pgrList.forEach(service -> {
            transform(service, pgrIndexList);
        });
        producer.push(topic, pgrIndexList);
    }

    private void transform(Service service, List<PGRIndex> pgrIndexList) {
        Map<String, String> boundaryLabelToNameMap = null;
        String tenantId = service.getTenantId();
        Optional<String> localityCode = Optional.ofNullable(service)
                .map(Service::getAddress)
                .map(Address::getLocality)
                .map(Boundary::getCode);
        if (localityCode.isPresent()) {
            boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMap(localityCode.get(), service.getTenantId());
        }

        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, null, boundaryLabelToNameMap);

        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, service.getAuditDetails().getCreatedBy());

        service.setAddress(null); //explicitly setting it to null as it is not needed
        service.setApplicationStatus(commonUtils.getMDMSTransformerLocalizations(service.getApplicationStatus(), tenantId));
        service.setServiceCode(commonUtils.getMDMSTransformerLocalizations(service.getServiceCode(), tenantId));

        PGRIndex pgrIndex = PGRIndex.builder()
                .service(service)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .boundaryHierarchy(boundaryHierarchy)
                .taskDates(commonUtils.getDateFromEpoch(service.getAuditDetails().getLastModifiedTime()))
                .build();

        pgrIndexList.add(pgrIndex);
    }

}
