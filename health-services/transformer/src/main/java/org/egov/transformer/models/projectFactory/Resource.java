package org.egov.transformer.models.projectFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Resource {

    @JsonProperty("filestoreId")
    private String filestoreId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("resourceId")
    private String resourceId;
}
