package org.egov.helper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * AdditionalFields
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdditionalFields {
    @JsonProperty("schema")
    private String schema = null;

    @JsonProperty("version")
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