package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.egov.common.models.individual.Identifier;

/** An identifier in stored form: identifierId is cipher text, identifierIdHash is the search hash. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredIdentifier extends Identifier {

    @JsonProperty("identifierIdHash")
    private String identifierIdHash;
}
