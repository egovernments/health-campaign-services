package org.egov.pgr.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.tracer.annotations.CustomSafeHtml;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

/**
 * Representation of a address. Indiavidual APIs may choose to extend from this using allOf if more details needed to be added in their case. 
 */
@ApiModel(description = "Representation of a address. Indiavidual APIs may choose to extend from this using allOf if more details needed to be added in their case. ")
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2020-07-15T11:35:33.568+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Address   {
        @CustomSafeHtml
        @JsonProperty("tenantId")
        private String tenantId = null;

        @CustomSafeHtml
        @JsonProperty("doorNo")
        private String doorNo = null;

        @CustomSafeHtml
        @JsonProperty("plotNo")
        private String plotNo = null;

        @CustomSafeHtml
        @JsonProperty("id")
        private String id = null;

        @CustomSafeHtml
        @JsonProperty("landmark")
        private String landmark = null;

        @CustomSafeHtml
        @JsonProperty("city")
        private String city = null;

        @CustomSafeHtml
        @JsonProperty("district")
        private String district = null;

        @CustomSafeHtml
        @JsonProperty("region")
        private String region = null;

        @CustomSafeHtml
        @JsonProperty("state")
        private String state = null;

        @CustomSafeHtml
        @JsonProperty("country")
        private String country = null;

        @CustomSafeHtml
        @JsonProperty("pincode")
        private String pincode = null;

        @JsonProperty("additionDetails")
        private Object additionDetails = null;

        @CustomSafeHtml
        @JsonProperty("buildingName")
        private String buildingName = null;

        @CustomSafeHtml
        @JsonProperty("street")
        private String street = null;

        @Valid
        @JsonProperty("locality")
        private Boundary locality = null;

        @JsonProperty("geoLocation")
        private GeoLocation geoLocation = null;


}

