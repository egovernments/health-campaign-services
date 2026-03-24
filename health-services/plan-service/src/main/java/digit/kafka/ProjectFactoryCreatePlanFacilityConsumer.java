package digit.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.util.CommonUtil;
import digit.web.models.PlanFacilityRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Map;

import static digit.config.ServiceConstants.HIERARCHY_TYPE;

@Slf4j
@Component
public class ProjectFactoryCreatePlanFacilityConsumer {

    private ObjectMapper objectMapper;

    private PlanFacilityEnricher enrichment;

    private CommonUtil commonUtil;

    private PlanFacilityRepository repository;

    public ProjectFactoryCreatePlanFacilityConsumer(ObjectMapper objectMapper, PlanFacilityEnricher enrichment, CommonUtil commonUtil, PlanFacilityRepository repository) {
        this.objectMapper = objectMapper;
        this.enrichment = enrichment;
        this.commonUtil = commonUtil;
        this.repository = repository;
    }

    @KafkaListener(topics = {"${project.factory.save.plan.facility.consumer.topic}"})
    public void listen(Map<String, Object> consumerRecord, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            PlanFacilityRequest planFacilityRequest = objectMapper.convertValue(consumerRecord, PlanFacilityRequest.class);
            String hierarchyType = commonUtil.extractFieldsFromJsonObject(planFacilityRequest.getPlanFacility().getAdditionalDetails(), HIERARCHY_TYPE, String.class);

            if(!StringUtils.isEmpty(hierarchyType))
                enrichment.enrichJurisdictionMapping(planFacilityRequest, hierarchyType);

            if(CollectionUtils.isEmpty(planFacilityRequest.getPlanFacility().getServiceBoundaries()))
                planFacilityRequest.getPlanFacility().setServiceBoundaries(new ArrayList<>());

            repository.create(planFacilityRequest);
        } catch (Exception exception) {
            log.error("Error in census consumer", exception);
        }
    }
}