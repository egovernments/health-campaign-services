package digit.repository;

import digit.web.models.*;

import java.util.List;

public interface PlanFacilityRepository {
    public void create(PlanFacilityRequest planFacilityRequest);

    public List<PlanFacility> search(PlanFacilitySearchCriteria planFacilitySearchCriteria);

    void update(PlanFacilityRequest planFacilityRequest);

    public Integer count(PlanFacilitySearchCriteria planFacilitySearchCriteria);
}
