package org.egov.common.models.facility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Size;

/**
 * Representation of a address. Individual APIs may choose to extend from this using allOf if more details needed to be added in their case.
 */
@ApiModel(description = "Representation of a address. Individual APIs may choose to extend from this using allOf if more details needed to be added in their case. ")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-21T14:37:54.683+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Address {
    @JsonProperty("id")
    @Size(min = 2, max = 64)

    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    private String clientReferenceId = null;

    @JsonProperty("doorNo")
    @Size(min = 2, max = 64)
    private String doorNo = null;

    @JsonProperty("latitude")
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double latitude = null;

    @JsonProperty("longitude")
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double longitude = null;

    @JsonProperty("locationAccuracy")
    @DecimalMin("0")
    @DecimalMax("10000")
    private Double locationAccuracy = null;

    @JsonProperty("type")
    private AddressType type = null;

    @JsonProperty("addressLine1")
    @Size(min = 2, max = 256)
    private String addressLine1 = null;

    @JsonProperty("addressLine2")
    @Size(min = 2, max = 256)
    private String addressLine2 = null;

    @JsonProperty("landmark")
    @Size(min = 2, max = 256)
    private String landmark = null;

    @JsonProperty("city")
    @Size(min = 2, max = 256)
    private String city = null;

    @JsonProperty("pincode")
    @Size(min = 2, max = 64)
    private String pincode = null;

    @JsonProperty("buildingName")
    @Size(min = 2, max = 256)
    private String buildingName = null;

    @JsonProperty("street")
    @Size(min = 2, max = 256)
    private String street = null;

    @JsonProperty("locality")
    @Valid
    private Boundary locality = null;


}

