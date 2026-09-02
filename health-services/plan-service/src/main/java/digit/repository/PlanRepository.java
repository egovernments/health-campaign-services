package digit.repository;

import digit.web.models.*;

import java.util.List;
import java.util.Map;

public interface PlanRepository {
    public void create(PlanRequest planRequest);

    public List<Plan> search(PlanSearchCriteria planSearchCriteria);

    public void update(PlanRequest planRequest);

    public Integer count(PlanSearchCriteria planSearchCriteria);

    public Map<String, Integer> statusCount(PlanSearchRequest planSearchRequest);

    public void bulkUpdate(BulkPlanRequest body);
}
