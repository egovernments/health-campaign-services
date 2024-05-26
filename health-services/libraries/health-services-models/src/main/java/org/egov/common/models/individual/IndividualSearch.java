package org.egov.common.models.individual;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.data.query.annotations.Exclude;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.springframework.validation.annotation.Validated;

/**
* A representation of an Individual.
*/
@ApiModel(description = "A representation of an Individual.")
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualSearch extends EgovOfflineSearchModel {
    @JsonProperty("individualId")
    private List<String> individualId = null;

    @JsonProperty("name")
    @Valid
    private Name name = null;

    @JsonProperty("dateOfBirth")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    private Date dateOfBirth = null;

    @JsonProperty("gender")
    @Valid
    private Gender gender = null;

    @JsonProperty("mobileNumber")
    private List<String> mobileNumber = null;

    @JsonProperty("socialCategory")
    private String socialCategory = null;

    @JsonProperty("wardCode")
    private String wardCode = null;

    @JsonProperty("individualName")
    private String individualName = null;

    @JsonProperty("createdFrom")
    private BigDecimal createdFrom = null;

    @JsonProperty("createdTo")
    private BigDecimal createdTo = null;

    @JsonProperty("identifier")
    @Valid
    private Identifier identifier = null;

    @JsonProperty("boundaryCode")
    private String boundaryCode = null;

    @JsonProperty("roleCodes")
    private List<String> roleCodes = null;

    @JsonProperty("username")
    private List<String> username;

    @JsonProperty("userId")
    private List<Long> userId;

    @JsonProperty("userUuid")
    @Size(min = 1)
    private List<String> userUuid;

    @Exclude
    @JsonProperty("latitude")
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double latitude;

    @Exclude
    @JsonProperty("longitude")
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double longitude;

    /*
     * @value unit of measurement in Kilometer
     * */
    @Exclude
    @JsonProperty("searchRadius")
    @DecimalMin("0")
    private Double searchRadius;

}

