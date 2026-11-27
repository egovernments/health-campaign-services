package org.egov.common.models.referralmanagement.sideeffect;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SideEffectSearch extends EgovOfflineSearchModel {

    @JsonProperty("taskId")
    private List<String> taskId;

    @JsonProperty("taskClientReferenceId")
    private List<String> taskClientReferenceId;

}
