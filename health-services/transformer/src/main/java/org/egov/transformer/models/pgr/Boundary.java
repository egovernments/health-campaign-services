package org.egov.transformer.models.pgr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.hibernate.validator.constraints.SafeHtml;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Boundary
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2020-07-15T11:35:33.568+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Boundary {

    @NotNull
    @SafeHtml
    @JsonProperty("code")
    private String code = null;

    @SafeHtml
    @JsonProperty("name")
    private String name = null;

    @SafeHtml
    @JsonProperty("label")
    private String label = null;

    @SafeHtml
    @JsonProperty("latitude")
    private String latitude = null;

    @SafeHtml
    @JsonProperty("longitude")
    private String longitude = null;

    @JsonProperty("children")
    @Valid
    private List<Boundary> children = null;

    @SafeHtml
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

