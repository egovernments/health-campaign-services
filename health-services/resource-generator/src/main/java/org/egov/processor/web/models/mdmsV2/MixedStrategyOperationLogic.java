package org.egov.processor.web.models.mdmsV2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MixedStrategyOperationLogic {

    @JsonProperty("isFixedPost")
    private boolean isFixedPost;

    @JsonProperty("RegistrationProcess")
    private String registrationProcess;

    @JsonProperty("DistributionProcess")
    private String distributionProcess;

    @JsonProperty("CategoriesNotAllowed")
    private List<String> categoriesNotAllowed;
}
