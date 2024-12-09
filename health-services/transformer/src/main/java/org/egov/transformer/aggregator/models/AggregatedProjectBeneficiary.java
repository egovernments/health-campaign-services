package org.egov.transformer.aggregator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
public class AggregatedProjectBeneficiary extends ProjectBeneficiary {

    @JsonProperty("tasks")
    List<Task> tasks = new ArrayList<>();

    @JsonProperty("sideEffects")
    List<SideEffect> sideEffects = new ArrayList<>();

    @JsonProperty("referrals")
    List<Referral> referrals = new ArrayList<>();

}
