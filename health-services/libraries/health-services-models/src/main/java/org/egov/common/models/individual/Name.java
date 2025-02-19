package org.egov.common.models.individual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Size;

/**
* Name
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Name   {
        @JsonProperty("givenName")
    

    @Size(min=2,max=200) 

    private String givenName = null;

        @JsonProperty("familyName")
    

    @Size(min=2,max=200) 

    private String familyName = null;

        @JsonProperty("otherNames")
    

    @Size(min=0,max=200) 

    private String otherNames = null;


}

