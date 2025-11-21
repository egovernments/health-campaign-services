package org.egov.pgr.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.pgr.annotation.CharacterConstraint;
import org.egov.tracer.annotations.CustomSafeHtml;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Instance of Service request raised for a particular service. As per extension propsed in the Service definition \&quot;attributes\&quot; carry the input values requried by metadata definition in the structure as described by the corresponding schema.  * Any one of &#39;address&#39; or &#39;(lat and lang)&#39; or &#39;addressid&#39; is mandatory 
 */
@ApiModel(description = "Instance of Service request raised for a particular service. As per extension propsed in the Service definition \"attributes\" carry the input values requried by metadata definition in the structure as described by the corresponding schema.  * Any one of 'address' or '(lat and lang)' or 'addressid' is mandatory ")
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2020-07-15T11:35:33.568+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Service   {

        @JsonProperty("active")
        private boolean active = true;

        @JsonProperty("user")
        private User user = null;

        @CustomSafeHtml
        @JsonProperty("id")
        private String id = null;

        @NotNull
        @CustomSafeHtml
        @JsonProperty("tenantId")
        private String tenantId = null;

        @NotNull
        @CustomSafeHtml
        @JsonProperty("serviceCode")
        private String serviceCode = null;

        @CustomSafeHtml
        @JsonProperty("serviceRequestId")
        private String serviceRequestId = null;

        @CustomSafeHtml
        @JsonProperty("description")
        private String description = null;

        @CustomSafeHtml
        @JsonProperty("accountId")
        private String accountId = null;

        @Max(5)
        @Min(0)
        @JsonProperty("rating")
        private Integer rating ;

        @CharacterConstraint(size = 600)
        @JsonProperty("additionalDetail")
        private String additionalDetail = null;

        @CustomSafeHtml
        @JsonProperty("applicationStatus")
        private String applicationStatus = null;

        @NotNull
        @CustomSafeHtml
        @JsonProperty("source")
        private String source = null;

        @Valid
        @NotNull
        @JsonProperty("address")
        private Address address = null;

        @JsonProperty("auditDetails")
        private AuditDetails auditDetails = null;

        @JsonProperty("selfComplaint")
        private Boolean selfComplaint;
}

