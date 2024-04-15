package org.egov.common.models.core;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

public class EgovSearchModel extends URLParams {
    @JsonProperty("id")
    @Valid
    private List<String> id = null;
}
