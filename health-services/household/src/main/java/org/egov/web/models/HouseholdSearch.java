package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

/**
* A representation of Household.
*/
    @ApiModel(description = "A representation of Household.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "household h")
public class HouseholdSearch {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId = null;

//    @JsonProperty("memberCount")
//    private Integer memberCount = null;

    @JsonProperty("boundaryCode")
    private String localityCode = null;
}

