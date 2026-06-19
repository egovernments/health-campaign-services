package org.egov.pgr.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.tracer.annotations.CustomSafeHtml;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Boundary
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2020-07-15T11:35:33.568+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Boundary   {

        @NotNull
        @CustomSafeHtml
        @JsonProperty("code")
        private String code = null;

        @CustomSafeHtml
        @JsonProperty("name")
        private String name = null;

        @CustomSafeHtml
        @JsonProperty("label")
        private String label = null;

        @CustomSafeHtml
        @JsonProperty("latitude")
        private String latitude = null;

        @CustomSafeHtml
        @JsonProperty("longitude")
        private String longitude = null;

        @JsonProperty("children")
        @Valid
        private List<Boundary> children = null;

        @CustomSafeHtml
        @JsonProperty("materializedPath")
        private String materializedPath = null;


        public Boundary addChildrenItem(Boundary childrenItem) {
            if (this.children == null) {
            this.children = new ArrayList<>();
            }
        this.children.add(childrenItem);
        return this;
        }

}

