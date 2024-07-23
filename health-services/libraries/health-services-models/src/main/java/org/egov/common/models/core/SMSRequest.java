package org.egov.common.models.core;


import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SMSRequest {
    private String mobileNumber;
    private String message;
}