package digit.repository;

import digit.web.models.*;

import java.util.List;

public interface PlanEmployeeAssignmentRepository {

    public void create(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest);

    public List<PlanEmployeeAssignment> search(PlanEmployeeAssignmentSearchCriteria planEmployeeAssignmentSearchCriteria);

    public void update(PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest);
}
