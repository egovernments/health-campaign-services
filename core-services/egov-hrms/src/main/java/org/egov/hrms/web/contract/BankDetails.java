package org.egov.hrms.web.contract;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Validated
@AllArgsConstructor
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankDetails {

    @Size(max=300)
    @JsonProperty("bankName")
    private String bankName;

    @Pattern(regexp = "^\\d{3}$", message = "CBN code should be 3 digit number")
    @JsonProperty("cbnCode")
    private String cbnCode;

    @Pattern(regexp = "^\\d{10}$", message = "account number should be 10 digit number")
    @JsonProperty("accountNumber")
    private String accountNumber;
}
