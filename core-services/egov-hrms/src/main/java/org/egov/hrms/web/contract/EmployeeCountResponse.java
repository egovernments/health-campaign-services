package org.egov.hrms.web.contract;

import lombok.*;
import org.egov.hrms.model.Employee;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Builder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Setter
@ToString



public class EmployeeCountResponse {
    private List<Employee> employees;
    private int count;

    public EmployeeCountResponse(List<Employee> employees, int count) {
        this.employees = employees;
        this.count = count;
    }

}
