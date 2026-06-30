package org.egov.transformer.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorMessage {

    private String topic;

    private String payload;

    private String errorMessage;

    private String stackTrace;

    private Long timestamp;
}
