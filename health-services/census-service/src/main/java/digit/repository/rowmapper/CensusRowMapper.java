package digit.repository.rowmapper;

import digit.util.QueryUtil;
import digit.web.models.AdditionalField;
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
        Map<String, AdditionalField> additionalFieldMap = new LinkedHashMap<>();

        while (rs.next()) {
            String censusId = rs.getString("census_id");
            Census censusEntry = censusMap.get(censusId);

            if (ObjectUtils.isEmpty(censusEntry)) {
                censusEntry = new Census();

                // Prepare audit details
                AuditDetails auditDetails = AuditDetails.builder().createdBy(rs.getString("census_created_by")).createdTime(rs.getLong("census_created_time")).lastModifiedBy(rs.getString("census_last_modified_by")).lastModifiedTime(rs.getLong("census_last_modified_time")).build();

                // Prepare census entry
                censusEntry.setId(rs.getString("census_id"));
                censusEntry.setTenantId(rs.getString("census_tenant_id"));
                censusEntry.setHierarchyType(rs.getString("census_hierarchy_type"));
                censusEntry.setBoundaryCode(rs.getString("census_boundary_code"));
                censusEntry.setType(Census.TypeEnum.fromValue(rs.getString("census_type")));
                censusEntry.setTotalPopulation(rs.getLong("census_total_population"));
                censusEntry.setEffectiveFrom(rs.getLong("census_effective_from"));
                censusEntry.setEffectiveTo(rs.getLong("census_effective_to"));
                censusEntry.setSource(rs.getString("census_source"));
                censusEntry.setStatus(rs.getString("census_status"));
                censusEntry.setAssignee(rs.getString("census_assignee"));
                censusEntry.setBoundaryAncestralPath(Collections.singletonList(rs.getString("census_boundary_ancestral_path")));
                censusEntry.setFacilityAssigned(rs.getBoolean("census_facility_assigned"));
                censusEntry.setAdditionalDetails(queryUtil.parseJson((PGobject) rs.getObject("census_additional_details")));
                censusEntry.setAuditDetails(auditDetails);
            }
            addPopulationByDemographic(rs, populationByDemographicMap, censusEntry);
            addAdditionalField(rs, additionalFieldMap, censusEntry);

            censusMap.put(censusId, censusEntry);
        }

        return new ArrayList<>(censusMap.values());
    }

    /**
     * Adds a AdditionalField object to the census entry based on the result set.
     *
     * @param rs                The ResultSet containing the data.
     * @param additionalFieldMap A map to keep track of added AdditionalField objects.
     * @param censusEntry       The Census entry to which the AdditionalField object will be added.
     * @throws SQLException If an SQL error occurs.
     */
    private void addAdditionalField(ResultSet rs, Map<String, AdditionalField> additionalFieldMap, Census censusEntry) throws SQLException {
        String additionalFieldId = rs.getString("additional_field_id");

        if (ObjectUtils.isEmpty(additionalFieldId) || additionalFieldMap.containsKey(additionalFieldId)) {
            return;
        }

        AdditionalField additionalField = new AdditionalField();
        additionalField.setId(rs.getString("additional_field_id"));
        additionalField.setKey(rs.getString("additional_field_key"));
        additionalField.setValue(rs.getBigDecimal("additional_field_value"));
        additionalField.setShowOnUi(rs.getBoolean("additional_field_show_on_ui"));
        additionalField.setEditable(rs.getBoolean("additional_field_editable"));
        additionalField.setOrder(rs.getInt("additional_field_order"));

        if (CollectionUtils.isEmpty(censusEntry.getAdditionalFields())) {
            List<AdditionalField> additionalFields = new ArrayList<>();
            additionalFields.add(additionalField);
            censusEntry.setAdditionalFields(additionalFields);
        } else {
            censusEntry.getAdditionalFields().add(additionalField);
        }

        additionalFieldMap.put(additionalFieldId, additionalField);
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
