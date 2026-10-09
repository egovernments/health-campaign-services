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

/**
 * This object holds list of documents attached during the transaciton for a property
 */
@ApiModel(description = "This object holds list of documents attached during the transaciton for a property")
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2020-07-15T11:35:33.568+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Document   {
        @CustomSafeHtml
        @JsonProperty("id")
        private String id = null;

        @CustomSafeHtml
        @JsonProperty("documentType")
        private String documentType = null;

        @CustomSafeHtml
        @JsonProperty("fileStoreId")
        private String fileStoreId = null;

        @CustomSafeHtml
        @JsonProperty("documentUid")
        private String documentUid = null;

        @JsonProperty("additionalDetails")
        private Object additionalDetails = null;


}

