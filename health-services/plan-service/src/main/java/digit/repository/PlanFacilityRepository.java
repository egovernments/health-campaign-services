package digit.repository;

import digit.web.models.*;

import java.util.List;

public interface PlanFacilityRepository {
    public void create(PlanFacilitySearchCriteria planFacilitySearchCriteria);

    public List<PlanFacility> search(PlanFacilitySearchCriteria planFacilitySearchCriteria);

    void update(PlanFacilityRequest planFacilityRequest);
}
