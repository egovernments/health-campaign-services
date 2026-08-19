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
    @JsonProperty("campaignNumber")
    protected String campaignNumber;
    @JsonProperty("campaignId")
    protected String campaignId;
    @JsonProperty("hierarchyType")
    protected String hierarchyType;

    public void setProjectInfo(String projectId, String projectType, String projectTypeId, String projectName, String hierarchyType) {
        this.projectId = projectId;
        this.projectType = projectType;
        this.projectTypeId = projectTypeId;
        this.projectName = projectName;
        this.hierarchyType = hierarchyType;
    }

    /**
     * Copies every project field from an already resolved ProjectInfo.
     */
    public void setProjectInfo(ProjectInfo projectInfo) {
        setProjectInfo(projectInfo.getProjectId(), projectInfo);
    }

    /**
     * Copies every project field from an already resolved ProjectInfo, but keeps projectId explicit for
     * indexes keyed on the id the record referenced rather than the resolved project's own id.
     */
    public void setProjectInfo(String projectId, ProjectInfo projectInfo) {
        setProjectInfo(projectId, projectInfo.getProjectType(), projectInfo.getProjectTypeId(),
                projectInfo.getProjectName(), projectInfo.getHierarchyType());
        this.campaignNumber = projectInfo.getCampaignNumber();
        this.campaignId = projectInfo.getCampaignId();
    }
}
