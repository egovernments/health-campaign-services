package org.egov.fhirtransformer.utils;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.stock.StockSearch;
import org.springframework.validation.annotation.Validated;

@Validated
@JsonIgnoreProperties(
        ignoreUnknown = true
)
public class FhirRequestBuilder {
    @JsonProperty("RequestInfo")
    private @NotNull @Valid RequestInfo requestInfo;
    @JsonProperty("fhir")
    private @NotNull JsonNode fhir;

    public FhirRequestBuilder(@NotNull @Valid RequestInfo requestInfo, @NotNull JsonNode fhir) {
        this.requestInfo = requestInfo;
        this.fhir = fhir;
    }

    public RequestInfo getRequestInfo() {
        return requestInfo;
    }

    public JsonNode getFhir() {
        return fhir;
    }

    public void setRequestInfo(RequestInfo requestInfo) {
        this.requestInfo = requestInfo;
    }

    public void setFhir(JsonNode fhir) {
        this.fhir = fhir;
    }
}
