package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanFacilityRepository;
import digit.web.models.PlanFacilityRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class PlanFacilityRepositoryImpl implements PlanFacilityRepository {

    private Producer producer;
    private Configuration config;

    public PlanFacilityRepositoryImpl(Producer producer, Configuration config) {
        this.producer = producer;
        this.config = config;
    }

    /**
     * This method emits an event to the persister for it to save the plan facility linkage in the database.
     * @param planFacilityRequest
     */
    @Override
    public void create(PlanFacilityRequest planFacilityRequest) {
        try {
            producer.push(config.getPlanFacilityCreateTopic(), planFacilityRequest);
        } catch (Exception e) {
            log.info("Pushing message to topic " + config.getPlanFacilityCreateTopic() + " failed.", e);
        }
    }


}
