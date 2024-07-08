package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.common.models.project.Project;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AncestorProjects {

  @JsonProperty("Projects")
  private List<Project> Projects = null;
}
