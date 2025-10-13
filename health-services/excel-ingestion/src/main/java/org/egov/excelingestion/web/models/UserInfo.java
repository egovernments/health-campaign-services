package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInfo {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("uuid")
    private String uuid;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("name")
    private String name;

    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @JsonProperty("emailId")
    private String emailId;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("type")
    private String type;

    @JsonProperty("tenantId")
    private String tenantId;
}
