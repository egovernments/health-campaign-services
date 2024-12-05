package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectInfo {
    @JsonProperty("projectId")
    protected String projectId;
    @JsonProperty("projectType")
    protected String projectType;
    @JsonProperty("projectTypeId")
    protected String projectTypeId;
    @JsonProperty("projectName")
    protected String projectName;

    public void setProjectInfo(String projectId, String projectType, String projectTypeId, String projectName) {
        this.projectId = projectId;
        this.projectType = projectType;
        this.projectTypeId = projectTypeId;
        this.projectName = projectName;
    }
}
