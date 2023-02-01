package org.egov.common.models;

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
    private String id;
    private String url;
    private Object requestBody;
    private String methodType;
    private String contentType;
    private Map<String, Object> requestHeaders;
    private String additionalDetails;
}
