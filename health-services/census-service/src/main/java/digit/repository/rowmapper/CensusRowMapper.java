package digit.repository.rowmapper;

import digit.util.QueryUtil;
import digit.web.models.Census;
import digit.web.models.PopulationByDemographic;
import org.egov.common.contract.models.AuditDetails;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class CensusRowMapper implements ResultSetExtractor<List<Census>> {

    private QueryUtil queryUtil;

    public CensusRowMapper(QueryUtil queryUtil) {
        this.queryUtil = queryUtil;
    }

    /**
     * Creates a list of Census record based on the ResultSet.
     *
     * @param rs the ResultSet containing data.
     * @return a list of Census record
     * @throws SQLException
     * @throws DataAccessException
     */
    @Override
    public List<Census> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, Census> censusMap = new LinkedHashMap<>();
        Map<String, PopulationByDemographic> populationByDemographicMap = new LinkedHashMap<>();

        while (rs.next()) {
            String censusId = rs.getString("census_id");
            Census censusEntry = censusMap.get(censusId);

            if (ObjectUtils.isEmpty(censusEntry)) {
                censusEntry = new Census();

                // Prepare audit details
                AuditDetails auditDetails = AuditDetails.builder().createdBy(rs.getString("census_created_by")).createdTime(rs.getLong("census_created_time")).lastModifiedBy(rs.getString("census_last_modified_by")).lastModifiedTime(rs.getLong("census_last_modified_time")).build();

                // Converting materialized path from comma separated string to a list of string
                String materializedPath = rs.getString("census_materialized_path");
                List<String> materializedPathList = Arrays.asList(materializedPath.split(","));

                // Prepare census entry
                censusEntry.setId(rs.getString("census_id"));
                censusEntry.setTenantId(rs.getString("census_tenant_id"));
                censusEntry.setHierarchyType(rs.getString("census_hierarchy_type"));
                censusEntry.setBoundaryCode(rs.getString("census_boundary_code"));
                censusEntry.setType(Census.TypeEnum.fromValue(rs.getString("census_type").toUpperCase()));
                censusEntry.setTotalPopulation(rs.getLong("census_total_population"));
                censusEntry.setEffectiveFrom(rs.getLong("census_effective_from"));
                censusEntry.setEffectiveTo(rs.getLong("census_effective_to"));
                censusEntry.setSource(rs.getString("census_source"));
                censusEntry.setStatus(Census.StatusEnum.valueOf(rs.getString("census_status").toUpperCase()));
                censusEntry.setAssignee(rs.getString("census_assignee"));
                censusEntry.setMaterializedPath(materializedPathList);
                censusEntry.setAdditionalDetails(queryUtil.parseJson((PGobject) rs.getObject("census_additional_details")));
                censusEntry.setAuditDetails(auditDetails);
            }
            addPopulationByDemographic(rs, populationByDemographicMap, censusEntry);

            censusMap.put(censusId, censusEntry);
        }

        return new ArrayList<>(censusMap.values());
    }


    /**
     * Adds a PopulationByDemographics object to the census entry based on the result set.
     *
     * @param rs                         The ResultSet containing the data.
     * @param populationByDemographicMap A map to keep track of added PopulationByDemographics objects.
     * @param censusEntry                The Census entry to which the PopulationByDemographics object will be added.
     * @throws SQLException If an SQL error occurs.
     */
    private void addPopulationByDemographic(ResultSet rs, Map<String, PopulationByDemographic> populationByDemographicMap, Census censusEntry) throws SQLException {
        String populationByDemographicId = rs.getString("population_by_demographics_id");

        if (ObjectUtils.isEmpty(populationByDemographicId) || populationByDemographicMap.containsKey(populationByDemographicId)) {
            return;
        }

        PopulationByDemographic populationByDemographic = new PopulationByDemographic();
        populationByDemographic.setId(rs.getString("population_by_demographics_id"));
        populationByDemographic.setDemographicVariable(PopulationByDemographic.DemographicVariableEnum.fromValue(rs.getString("population_by_demographics_demographic_variable")));
        populationByDemographic.setPopulationDistribution(queryUtil.parseJson((PGobject) rs.getObject("population_by_demographics_population_distribution")));

        if (CollectionUtils.isEmpty(censusEntry.getPopulationByDemographics())) {
            List<PopulationByDemographic> populationByDemographicList = new ArrayList<>();
            populationByDemographicList.add(populationByDemographic);
            censusEntry.setPopulationByDemographics(populationByDemographicList);
        } else {
            censusEntry.getPopulationByDemographics().add(populationByDemographic);
        }

        populationByDemographicMap.put(populationByDemographicId, populationByDemographic);
    }
}
