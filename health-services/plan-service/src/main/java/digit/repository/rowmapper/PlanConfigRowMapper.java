package digit.repository.rowmapper;

import digit.util.QueryUtil;
import digit.web.models.*;
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
public class PlanConfigRowMapper implements ResultSetExtractor<List<PlanConfiguration>> {

    private QueryUtil queryUtil;

    public PlanConfigRowMapper(QueryUtil queryUtil) {
        this.queryUtil = queryUtil;
    }

    @Override
    public List<PlanConfiguration> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, PlanConfiguration> planConfigurationMap = new LinkedHashMap<>();
        Set<String> fileSet = new HashSet<>();
        Set<String> operationSet = new HashSet<>();
        Set<String> assumptionSet = new HashSet<>();
        Set<String> resourceMappingSet = new HashSet<>();


        while (rs.next()) {
            String planConfigId = rs.getString("plan_configuration_id");

            PlanConfiguration planConfigEntry = planConfigurationMap.get(planConfigId);

            if (ObjectUtils.isEmpty(planConfigEntry)) {
                planConfigEntry = new PlanConfiguration();

                // Prepare audit details
                AuditDetails auditDetails = AuditDetails.builder().createdBy(rs.getString("plan_configuration_created_by")).createdTime(rs.getLong("plan_configuration_created_time")).lastModifiedBy(rs.getString("plan_configuration_last_modified_by")).lastModifiedTime(rs.getLong("plan_configuration_last_modified_time")).build();

                // Prepare plan object
                planConfigEntry.setId(planConfigId);
                planConfigEntry.setTenantId(rs.getString("plan_configuration_tenant_id"));
                planConfigEntry.setName(rs.getString("plan_configuration_name"));
                planConfigEntry.setCampaignId(rs.getString("plan_configuration_campaign_id"));
                planConfigEntry.setStatus(rs.getString("plan_configuration_status"));
                planConfigEntry.setAdditionalDetails(queryUtil.getAdditionalDetail((PGobject) rs.getObject("plan_configuration_additional_details")));
                planConfigEntry.setAuditDetails(auditDetails);

            }
            addFiles(rs, planConfigEntry, fileSet);
            addAssumptions(rs, planConfigEntry, assumptionSet);
            addOperations(rs, planConfigEntry, operationSet);
            addResourceMappings(rs, planConfigEntry, resourceMappingSet);

            planConfigurationMap.put(planConfigId, planConfigEntry);
        }
        return new ArrayList<>(planConfigurationMap.values());
    }

    /**
     * Adds a File object to the PlanConfiguration entry based on the result set.
     *
     * @param rs              The ResultSet containing the data.
     * @param planConfigEntry The PlanConfiguration entry to which the File object will be added.
     * @param fileSet         A map to keep track of added File objects.
     * @throws SQLException If an SQL error occurs.
     */
    private void addFiles(ResultSet rs, PlanConfiguration planConfigEntry, Set<String> fileSet) throws SQLException {
        String fileId = rs.getString("plan_configuration_files_id");

        if (ObjectUtils.isEmpty(fileId) || fileSet.contains(fileId)) {
            return;
        }

        File file = new File();
        file.setId(fileId);
        file.setFilestoreId(rs.getString("plan_configuration_files_filestore_id"));
        file.setInputFileType(File.InputFileTypeEnum.valueOf(rs.getString("plan_configuration_files_input_file_type").toUpperCase()));
        file.setTemplateIdentifier(rs.getString("plan_configuration_files_template_identifier"));
        file.setActive(rs.getBoolean("plan_configuration_files_active"));
        if (CollectionUtils.isEmpty(planConfigEntry.getFiles())) {
            List<File> fileList = new ArrayList<>();
            fileList.add(file);
            planConfigEntry.setFiles(fileList);
        } else {
            planConfigEntry.getFiles().add(file);
        }

        fileSet.add(fileId);
    }


    /**
     * Adds an Assumption object to the PlanConfiguration entry based on the result set.
     *
     * @param rs              The ResultSet containing the data.
     * @param planConfigEntry The PlanConfiguration entry to which the Assumption object will be added.
     * @param assumptionSet   A map to keep track of added Assumption objects.
     * @throws SQLException If an SQL error occurs.
     */
    private void addAssumptions(ResultSet rs, PlanConfiguration planConfigEntry, Set<String> assumptionSet) throws SQLException {
        String assumptionId = rs.getString("plan_configuration_assumptions_id");

        if (ObjectUtils.isEmpty(assumptionId) || assumptionSet.contains(assumptionId)) {
            return;
        }

        Assumption assumption = new Assumption();
        assumption.setId(assumptionId);
        assumption.setKey(rs.getString("plan_configuration_assumptions_key"));
        assumption.setValue(rs.getBigDecimal("plan_configuration_assumptions_value"));
        assumption.setActive(rs.getBoolean("plan_configuration_assumptions_active"));
        assumption.setSource(Source.valueOf(rs.getString("plan_configuration_assumptions_source")));
        assumption.setCategory(rs.getString("plan_configuration_assumptions_category"));

        if (CollectionUtils.isEmpty(planConfigEntry.getAssumptions())) {
            List<Assumption> assumptionList = new ArrayList<>();
            assumptionList.add(assumption);
            planConfigEntry.setAssumptions(assumptionList);
        } else {
            planConfigEntry.getAssumptions().add(assumption);
        }

        assumptionSet.add(assumptionId);
    }

    /**
     * Adds an Operation object to the PlanConfiguration entry based on the result set.
     *
     * @param rs              The ResultSet containing the data.
     * @param planConfigEntry The PlanConfiguration entry to which the Operation object will be added.
     * @param operationMap    A map to keep track of added Operation objects.
     * @throws SQLException If an SQL error occurs.
     */
    private void addOperations(ResultSet rs, PlanConfiguration planConfigEntry, Set<String> operationMap) throws SQLException {
        String operationId = rs.getString("plan_configuration_operations_id");

        if (ObjectUtils.isEmpty(operationId) || operationMap.contains(operationId)) {
            return;
        }

        Operation operation = new Operation();
        operation.setId(operationId);
        operation.setInput(rs.getString("plan_configuration_operations_input"));
        operation.setOperator(Operation.OperatorEnum.fromValue(rs.getString("plan_configuration_operations_operator")));
        operation.setAssumptionValue(rs.getString("plan_configuration_operations_assumption_value"));
        operation.setOutput(rs.getString("plan_configuration_operations_output"));
        operation.setActive(rs.getBoolean("plan_configuration_operations_active"));
        operation.setShowOnEstimationDashboard(rs.getBoolean("plan_configuration_operations_show_on_estimation_dashboard"));
        operation.setSource(Source.valueOf(rs.getString("plan_configuration_operations_source")));
        operation.setCategory(rs.getString("plan_configuration_operations_category"));
        operation.setExecutionOrder(rs.getInt("plan_configuration_execution_order"));

        if (CollectionUtils.isEmpty(planConfigEntry.getOperations())) {
            List<Operation> operationList = new ArrayList<>();
            operationList.add(operation);
            planConfigEntry.setOperations(operationList);
        } else {
            planConfigEntry.getOperations().add(operation);
        }

        operationMap.add(operationId);
    }

    /**
     * Adds a ResourceMapping object to the PlanConfiguration entry based on the result set.
     *
     * @param rs              The ResultSet containing the data.
     * @param planConfigEntry The PlanConfiguration entry to which the ResourceMapping object will be added.
     * @param resourceMappingSet      A map to keep track of added ResourceMapping objects.
     * @throws SQLException If an SQL error occurs.
     */
    private void addResourceMappings(ResultSet rs, PlanConfiguration planConfigEntry, Set<String> resourceMappingSet) throws SQLException {
        String mappingId = rs.getString("plan_configuration_mapping_id");

        if (ObjectUtils.isEmpty(mappingId) || resourceMappingSet.contains(mappingId)) {
            return;
        }

        ResourceMapping mapping = new ResourceMapping();
        mapping.setId(mappingId);
        mapping.setFilestoreId(rs.getString("plan_configuration_mapping_filestore_id"));
        mapping.setMappedFrom(rs.getString("plan_configuration_mapping_mapped_from"));
        mapping.setMappedTo(rs.getString("plan_configuration_mapping_mapped_to"));
        mapping.setActive(rs.getBoolean("plan_configuration_mapping_active"));

        if (CollectionUtils.isEmpty(planConfigEntry.getResourceMapping())) {
            List<ResourceMapping> mappingList = new ArrayList<>();
            mappingList.add(mapping);
            planConfigEntry.setResourceMapping(mappingList);
        } else {
            planConfigEntry.getResourceMapping().add(mapping);
        }

        resourceMappingSet.add(mappingId);
    }

}
