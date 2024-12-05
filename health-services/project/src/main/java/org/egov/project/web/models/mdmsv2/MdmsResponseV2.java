package org.egov.project.web.models.mdmsv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.List;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MdmsResponseV2 {
    @JsonProperty("ResponseInfo")
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("mdms")
    @Valid
    private List<Mdms> mdms = null;
}
