package org.egov.workerregistry.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Worker extends EgovModel {

    @JsonProperty("name")
    private String name;

    @JsonProperty("payeePhoneNumber")
    private String payeePhoneNumber;

    @JsonProperty("paymentProvider")
    private String paymentProvider;

    @JsonProperty("payeeName")
    private String payeeName;

    @JsonProperty("bankAccount")
    private String bankAccount;

    @JsonProperty("bankCode")
    private String bankCode;

    @JsonProperty("photoId")
    private String photoId;

    @JsonProperty("signatureId")
    private String signatureId;

    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    @JsonProperty("isDeleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @JsonProperty("individualIds")
    private List<String> individualIds;
}
