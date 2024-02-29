package org.egov.helper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Field
 */


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Field {
    @JsonProperty("key")
    private String key = null;

    @JsonProperty("value")
    private String value = null;


}