package org.egov.common.models.referralmanagement.sideeffect;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.EgovOfflineSearchModel;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SideEffectSearch extends EgovOfflineSearchModel {

    @JsonProperty("taskId")
    private List<String> taskId;

    @JsonProperty("taskClientReferenceId")
    private List<String> taskClientReferenceId;

}
