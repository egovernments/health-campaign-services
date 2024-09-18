package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.web.models.PlanEmployeeAssignmentRequest;
import org.springframework.stereotype.Repository;

@Repository
public class PlanEmployeeAssignmentImpl implements PlanEmployeeAssignmentRepository {

    private Producer producer;

    private Configuration config;

    public PlanEmployeeAssignmentImpl(Producer producer, Configuration config)
    {
        this.producer = producer;
        this.config = config;
    }

    /**
     * Pushes a new plan employee assignment to persister kafka topic.
     * @param planEmployeeAssignmentRequest The request containing the plan employee assignment details.
     */
    @Override
    public void create(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        producer.push(config.getPlanEmployeeAssignmentCreateTopic(), planEmployeeAssignmentRequest);
    }

    /**
     * Pushes an updated existing plan employee assignment to persister kafka topic.
     * @param planEmployeeAssignmentRequest The request containing the updated plan employee assignment details.
     */
    @Override
    public void update(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        producer.push(config.getPlanEmployeeAssignmentUpdateTopic(), planEmployeeAssignmentRequest);
    }
}
