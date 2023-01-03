package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
* A representation of an Individual.
*/
    @ApiModel(description = "A representation of an Individual.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-27T11:47:19.561+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndividualSearch   {
        @JsonProperty("id")


        @Size(min=2,max=64)
    private String id = null;

        @JsonProperty("tenantId")


        @Size(min=2,max=1000)
    private String tenantId = null;

        @JsonProperty("clientReferenceId")


        @Size(min=2,max=64)
    private String clientReferenceId = null;

        @JsonProperty("name")
    
  @Valid


    private Name name = null;

    @JsonProperty("dateOfBirth")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    


    private LocalDate dateOfBirth = null;

        @JsonProperty("gender")
    
  @Valid


    private Gender gender = null;

        @JsonProperty("identifier")
    
  @Valid


    private Identifier identifier = null;

        @JsonProperty("boundaryCode")
    


    private String boundaryCode = null;


}

