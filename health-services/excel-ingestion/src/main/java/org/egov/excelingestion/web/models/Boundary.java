package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Boundary {

    @JsonProperty("code")
    @NotBlank(message = "Boundary code is required")
    private String code;

    @JsonProperty("name")
    @NotBlank(message = "Boundary name is required")
    private String name;

    @JsonProperty("type")
    @NotBlank(message = "Boundary type is required")
    private String type;

    @JsonProperty("isRoot")
    private Boolean isRoot;

    @JsonProperty("parent")
    private String parent;

    @JsonProperty("includeAllChildren")
    private Boolean includeAllChildren;
}