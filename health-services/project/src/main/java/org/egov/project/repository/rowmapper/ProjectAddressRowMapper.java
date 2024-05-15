package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.Address;
import org.egov.common.models.project.AddressType;
import org.egov.common.models.project.Project;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ProjectAddressRowMapper implements ResultSetExtractor<List<Project>> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<Project> extractData(ResultSet rs) throws SQLException, DataAccessException {

        Map<String, Project> projectMap = new LinkedHashMap<>();
        while (rs.next()) {
            String project_id = rs.getString("projectId");

            if (!projectMap.containsKey(project_id)) {
                projectMap.put(project_id, createProjectObj(rs));
            }
        }

        return new ArrayList<>(projectMap.values());
    }

    private Project createProjectObj(ResultSet rs) throws SQLException, DataAccessException {
        Address address = getAddressObjFromResultSet(rs);
        Project project = getProjectObjFromResultSet(rs, address);
        return project;
    }

    /* Builds Address Object from Result Set */
    private Address getAddressObjFromResultSet(ResultSet rs) throws SQLException {
        String address_id = rs.getString("addressId");
        String address_tenantId = rs.getString("address_tenantId");
        String address_projectId = rs.getString("address_projectId");
        String address_doorNo = rs.getString("address_doorNo");
        Double address_latitude = rs.getDouble("address_latitude");
        Double address_longitude = rs.getDouble("address_longitude");
        Double address_locationAccuracy = rs.getDouble("address_locationAccuracy");
        String address_type = rs.getString("address_type");
        String address_addressLine1 = rs.getString("address_addressLine1");
        String address_addressLine2 = rs.getString("address_addressLine2");
        String address_landmark = rs.getString("address_landmark");
        String address_city = rs.getString("address_city");
        String address_pinCode = rs.getString("address_pinCode");
        String address_buildingName = rs.getString("address_buildingName");
        String address_street = rs.getString("address_street");
        String address_boundaryType = rs.getString("address_boundaryType");
        String address_boundary = rs.getString("address_boundary");

        Address address = Address.builder()
                .id(address_id)
                .tenantId(address_tenantId)
                .doorNo(address_doorNo)
                .latitude(address_latitude)
                .longitude(address_longitude)
                .locationAccuracy(address_locationAccuracy)
                .type(AddressType.fromValue(address_type))
                .addressLine1(address_addressLine1)
                .addressLine2(address_addressLine2)
                .landmark(address_landmark)
                .city(address_city)
                .pincode(address_pinCode)
                .buildingName(address_buildingName)
                .street(address_street)
                .boundaryType(address_boundaryType)
                .boundary(address_boundary)
                .build();

        if (address_id == null) {
            return null;
        }

        return address;
    }

    /* Builds Project Object from Result Set and address */
    private Project getProjectObjFromResultSet(ResultSet rs, Address address) throws SQLException {
        String project_id = rs.getString("projectId");
        String project_tenantId = rs.getString("project_tenantId");
        String project_projectNumber = rs.getString("project_projectNumber");
        String project_name = rs.getString("project_name");
        String project_projectType = rs.getString("project_projectType");
        String project_projectTypeId = rs.getString("project_projectTypeId");
        String project_projectSubtype = rs.getString("project_projectSubtype");
        String project_department = rs.getString("project_department");
        String project_description = rs.getString("project_description");
        String project_referenceId = rs.getString("project_referenceId");
        Long project_startDate = rs.getLong("project_startDate");
        Long project_endDate = rs.getLong("project_endDate");
        Boolean project_isTaskEnabled = rs.getBoolean("project_isTaskEnabled");
        String project_projectHierarchy = rs.getString("project_projectHierarchy");
        String project_parent = rs.getString("project_parent");
        JsonNode project_additionalDetails = getAdditionalDetail("project_additionalDetails", rs);
        String project_natureOfWork = rs.getString("project_natureOfWork");
        Boolean project_isDeleted = rs.getBoolean("project_isDeleted");
        Integer project_rowVersion = rs.getInt("project_rowVersion");
        String project_createdBy = rs.getString("project_createdBy");
        String project_lastModifiedBy = rs.getString("project_lastModifiedBy");
        Long project_createdTime = rs.getLong("project_createdTime");
        Long project_lastModifiedTime = rs.getLong("project_lastModifiedTime");

        AuditDetails projectAuditDetails = AuditDetails.builder().createdBy(project_createdBy).createdTime(project_createdTime)
                .lastModifiedBy(project_lastModifiedBy).lastModifiedTime(project_lastModifiedTime)
                .build();

        Project project = Project.builder()
                .id(project_id)
                .tenantId(project_tenantId)
                .projectNumber(project_projectNumber)
                .name(project_name)
                .projectType(project_projectType)
                .projectTypeId(project_projectTypeId)
                .projectSubType(project_projectSubtype)
                .department(project_department)
                .description(project_description)
                .referenceID(project_referenceId)
                .startDate(project_startDate)
                .endDate(project_endDate)
                .isTaskEnabled(project_isTaskEnabled)
                .parent(project_parent)
                .projectHierarchy(project_projectHierarchy)
                .additionalDetails(project_additionalDetails)
                .natureOfWork(project_natureOfWork)
                .isDeleted(project_isDeleted)
                .rowVersion(project_rowVersion)
                .address(address)
                .auditDetails(projectAuditDetails)
                .build();

        return project;
    }

    private JsonNode getAdditionalDetail(String columnName, ResultSet rs) throws SQLException {
        JsonNode additionalDetails = null;
        try {
            PGobject obj = (PGobject) rs.getObject(columnName);
            if (obj != null) {
                additionalDetails = objectMapper.readTree(obj.getValue());
            }
        } catch (IOException e) {
            throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object");
        }
        if (additionalDetails == null || additionalDetails.isEmpty())
            additionalDetails = null;
        return additionalDetails;
    }
}
