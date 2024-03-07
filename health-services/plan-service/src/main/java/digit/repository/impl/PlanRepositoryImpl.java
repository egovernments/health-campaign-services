package digit.repository.impl;

import digit.kafka.Producer;
import digit.repository.PlanRepository;
import digit.web.models.PlanRequest;
import digit.web.models.PlanSearchCriteria;
import org.springframework.stereotype.Repository;

@Repository
public class PlanRepositoryImpl implements PlanRepository {

    private Producer producer;

    public PlanRepositoryImpl(Producer producer) {
        this.producer = producer;
    }

    @Override
    public void create(PlanRequest planRequest) {
        producer.push("save-plan", planRequest);
    }

    @Override
    public void search(PlanSearchCriteria planSearchCriteria) {

    }

    @Override
    public void update(PlanRequest planRequest) {
        producer.push("update-plan", planRequest);
    }
}
