package org.egov.transformer.models.pgr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import javax.validation.Valid;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceWrapper {


    @Valid
    @NonNull
    @JsonProperty("service")
    private Service service = null;

    @Valid
    @JsonProperty("workflow")
    private Workflow workflow = null;

}
