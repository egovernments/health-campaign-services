package org.egov.transformer.models.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiDetails {
    @JsonProperty("id")
    private String id;
    @JsonProperty("url")
    private String url;
    @JsonProperty("requestBody")
    private String requestBody;
    @JsonProperty("methodType")
    private String methodType;
    @JsonProperty("contentType")
    private String contentType;
    @JsonProperty("requestHeaders")
    private Map<String, Object> requestHeaders;
    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    public ApiDetails getTracerModel() {
        return ApiDetails.builder()
                .id(id)
                .url(url)
                .requestBody(requestBody)
                .methodType(methodType)
                .contentType(contentType)
                .requestHeaders(requestHeaders)
                .additionalDetails(additionalDetails)
                .build();
    }
}
