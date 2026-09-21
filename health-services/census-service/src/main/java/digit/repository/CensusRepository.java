package digit.repository;

import digit.web.models.*;

import java.util.List;
import java.util.Map;

public interface CensusRepository {

    public void create(CensusRequest censusRequest);

    public List<Census> search(CensusSearchCriteria censusSearchCriteria);

    public void update(CensusRequest censusRequest);

    public void bulkUpdate(BulkCensusRequest request);

    public Integer count(CensusSearchCriteria censusSearchCriteria);

    public Map<String, Integer> statusCount(CensusSearchRequest censusSearchRequest);
}
