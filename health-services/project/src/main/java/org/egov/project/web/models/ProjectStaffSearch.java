package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Size;
import java.util.List;

/**
* This object defines the mapping of a system staff user to a project for a certain period.
*/
    @ApiModel(description = "This object defines the mapping of a system staff user to a project for a certain period.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
@Table(name="project_staff")
public class ProjectStaffSearch   {


    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("tenantId")
    @Size(min=2,max=1000)
    private String tenantId = null;

    @JsonProperty("staffId")
    private List<String> staffId = null;

    @JsonProperty("projectId")
    @Size(min=2,max=64)
    private String projectId = null;

    @JsonProperty("startDate")
    private Long startDate = null;

    @JsonProperty("endDate")
    private Long endDate = null;


}

