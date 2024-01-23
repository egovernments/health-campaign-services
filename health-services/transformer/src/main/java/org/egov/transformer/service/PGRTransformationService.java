package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@Component
public class PGRTransformationService {

    private final ProjectService projectService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private static final HashMap<String, String> translations = null;


    public PGRTransformationService(ProjectService projectService, TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils) {

        this.projectService = projectService;
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
            boundaryLabelToNameMap = projectService.getBoundaryLabelToNameMap(String.valueOf(localityCode), service.getTenantId());
        }

        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, null, boundaryLabelToNameMap);


        PGRIndex pgrIndex = PGRIndex.builder()
                .service(service)
                .boundaryHierarchy(boundaryHierarchy)
                .build();
        service.setAddress(null);
        service.setApplicationStatus(commonUtils.getMDMSTransformerLocalizations(service.getApplicationStatus(), tenantId));
        service.setServiceCode(commonUtils.getMDMSTransformerLocalizations(service.getServiceCode(), tenantId));
        pgrIndexList.add(pgrIndex);
    }

}
