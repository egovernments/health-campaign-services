package org.egov.transformer.models.Project;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectInfo {
    private String projectId;
    private String projectTypeId;
    private String name;
    private String referenceId;
}
