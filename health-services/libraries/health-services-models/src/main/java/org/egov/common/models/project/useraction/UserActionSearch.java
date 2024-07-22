package org.egov.common.models.project.irs;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionSearch extends EgovOfflineSearchModel {

    @JsonProperty("projectId")
    private List<String> projectId;

    @JsonProperty("beneficiaryTag")
    private List<String> beneficiaryTag;

    @JsonProperty("resourceTag")
    private List<String> resourceTag;

    @JsonProperty("boundaryCode")
    private List<String> boundaryCode;
}
