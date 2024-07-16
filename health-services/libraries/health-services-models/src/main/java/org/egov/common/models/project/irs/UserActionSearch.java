package org.egov.common.models.project.irs;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.common.models.core.EgovOfflineSearchModel;

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
