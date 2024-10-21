package digit.repository;

import digit.web.models.BulkPlanRequest;
import digit.web.models.Plan;
import digit.web.models.PlanRequest;
import digit.web.models.PlanSearchCriteria;

import java.util.List;

public interface PlanRepository {
    public void create(PlanRequest planRequest);

    public List<Plan> search(PlanSearchCriteria planSearchCriteria);

    public void update(PlanRequest planRequest);

    public void bulkUpdate(BulkPlanRequest body);
}
