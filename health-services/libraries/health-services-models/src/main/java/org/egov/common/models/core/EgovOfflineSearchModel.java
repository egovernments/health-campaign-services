package org.egov.common.models.core;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EgovOfflineSearchModel extends EgovSearchModel {
    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;
}
