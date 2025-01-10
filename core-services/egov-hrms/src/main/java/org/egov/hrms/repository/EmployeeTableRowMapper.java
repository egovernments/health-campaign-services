package org.egov.hrms.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.hrms.model.AuditDetails;
import org.egov.hrms.model.Employee;
import org.egov.hrms.web.contract.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class EmployeeTableRowMapper implements ResultSetExtractor<List<Employee>> {

    @Autowired
    private ObjectMapper mapper;

    @Override
    public List<Employee> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, Employee> employeeMap = new HashMap<>();
        while (rs.next()) {
            String currentId = rs.getString("employee_uuid");
            Employee currentEmployee = employeeMap.get(currentId);

            // Create Employee object if not already in the map
            if (currentEmployee == null) {
                AuditDetails auditDetails = AuditDetails.builder()
                        .createdBy(rs.getString("employee_createdby"))
                        .createdDate(rs.getLong("employee_createddate"))
                        .lastModifiedBy(rs.getString("employee_lastmodifiedby"))
                        .lastModifiedDate(rs.getLong("employee_lastmodifieddate"))
                        .build();

                currentEmployee = Employee.builder()
                        .id(rs.getLong("employee_id"))
                        .uuid(rs.getString("employee_uuid"))
                        .tenantId(rs.getString("employee_tenantid"))
                        .code(rs.getString("employee_code"))
                        .dateOfAppointment(rs.getObject("employee_doa") != null ? rs.getLong("employee_doa") : null)
                        .IsActive(rs.getBoolean("employee_active"))
                        .employeeStatus(rs.getString("employee_status"))
                        .employeeType(rs.getString("employee_type"))
                        .reActivateEmployee(rs.getBoolean("employee_reactive"))
                        .user(new User()) // Empty User object; populate later if needed
                        .auditDetails(auditDetails)
                        .build();

                employeeMap.put(currentId, currentEmployee);
            }
        }
        return new ArrayList<>(employeeMap.values());
    }

}
