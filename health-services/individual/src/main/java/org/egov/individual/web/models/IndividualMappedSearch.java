package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualMappedSearch {

    @JsonProperty("mobileNumber")
    private List<String> mobileNumber;

    @JsonProperty("username")
    private List<String> username;

    @JsonProperty("responseFields")
    @Size(min=1,max = 3, message = "Maximum of 3 response fields allowed")
    @NotNull(message = "responseFields is required")
    private List<@Pattern(regexp = "^(useruuid|mobilenumber|username)$", message = "Allowed values are: useruuid, mobilenumber, username") String> responseFields;

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
