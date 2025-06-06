package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.egov.individual.validators.ExactlyOneField;
import org.egov.individual.validators.ValidResponseFields;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ExactlyOneField
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualMappedSearch {

    @JsonProperty("mobileNumber")
    private List<String> mobileNumber;

    @JsonProperty("username")
    private List<String> username;

    @JsonProperty("responseFields")
    @Size(min = 1, max = 5, message = "responseFields must contain between 1 and 5 fields")
    @NotNull(message = "responseFields is required")
    @ValidResponseFields()
    private List<String> responseFields;

    public List<String> getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(List<String> mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public List<String> getUsername() {
        return username;
    }

    public void setUsername(List<String> username) {
        this.username = username;
    }

    public List<String> getResponseFields() {
        return responseFields;
    }

    public void setResponseFields(List<String> responseFields) {
        this.responseFields = responseFields;
    }
}
