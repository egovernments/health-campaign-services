package digit.repository;

import digit.web.models.*;

import java.util.List;

public interface CensusRepository {

    public void create(CensusRequest censusRequest);

    public List<Census> search(CensusSearchCriteria censusSearchCriteria);

    public void update(CensusRequest censusRequest);

    public Integer count(CensusSearchCriteria censusSearchCriteria);
}
