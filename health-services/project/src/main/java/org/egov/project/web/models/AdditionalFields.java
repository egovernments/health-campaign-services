package org.egov.project.web.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import org.egov.project.web.models.Field;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* AdditionalFields
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdditionalFields   {
        @JsonProperty("schema")
    

    @Size(min=2,max=64) 

    private String schema = null;

        @JsonProperty("version")
    

    @Min(1)

    private Integer version = null;

        @JsonProperty("fields")
    
  @Valid


    private List<Field> fields = null;


        public AdditionalFields addFieldsItem(Field fieldsItem) {
            if (this.fields == null) {
            this.fields = new ArrayList<>();
            }
        this.fields.add(fieldsItem);
        return this;
        }

}

