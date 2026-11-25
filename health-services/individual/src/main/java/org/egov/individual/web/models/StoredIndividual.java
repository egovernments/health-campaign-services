package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.egov.common.models.individual.Individual;

/**
 * An Individual in its stored (encrypted) form.
 *
 * <p>Text PII fields of the parent hold cipher text. PII whose model type is not text
 * (date of birth, gender, blood group) cannot hold cipher text, so the parent field is null
 * and the cipher sits in the *Cipher field here. The *Hash fields are the search hashes.
 * This form goes to Kafka (persister, indexer) and Redis. It is never returned by the API:
 * decryption turns it back into a plain {@link Individual}.</p>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoredIndividual extends Individual {

    @JsonProperty("dateOfBirthCipher")
    private String dateOfBirthCipher;

    @JsonProperty("genderCipher")
    private String genderCipher;

    @JsonProperty("bloodGroupCipher")
    private String bloodGroupCipher;

    @JsonProperty("mobileNumberHash")
    private String mobileNumberHash;

    @JsonProperty("givenNameHash")
    private String givenNameHash;

    @JsonProperty("usernameHash")
    private String usernameHash;
}
