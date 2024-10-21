package digit.repository;

import digit.web.models.*;

import java.util.List;
import java.util.Map;

public interface CensusRepository {

    public void create(CensusRequest censusRequest);

    public List<Census> search(CensusSearchCriteria censusSearchCriteria);

    public void update(CensusRequest censusRequest);

    public Integer count(CensusSearchCriteria censusSearchCriteria);

    public Map<String, Integer> statusCount(CensusSearchCriteria censusSearchCriteria);
}
