package org.egov.individual.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.core.Field;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.BloodGroup;
import org.egov.common.models.individual.Gender;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Name;
import org.egov.individual.config.IndividualProperties;
import org.egov.common.pii.CipherText;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.common.pii.PiiHasher;
import org.egov.individual.web.models.StoredIdentifier;
import org.egov.individual.web.models.StoredIndividual;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Field-level encryption of an Individual's PII (JSON-based helper; replaces the typed
 * enc-client encryptJson/decryptJson calls).
 *
 * <p>Encrypt: every PII value of a whole batch goes to enc-service in ONE call (type Normal,
 * state-level key). Search hashes are computed from the plain values first. The result is
 * the stored form ({@link StoredIndividual}), which is what Kafka, the persister and Redis see.</p>
 *
 * <p>Decrypt: only values that really are cipher text are collected, in ONE enc-client call
 * (policy check + audit). Plain values from rows written before the backfill are left as they
 * are, so mixed old and new data can be read. The result is a plain {@link Individual}.</p>
 */
@Slf4j
@Service
public class IndividualPiiCryptoService {

    /**
     * Text form of a date of birth in the database: ISO (yyyy-MM-dd). The migration converts the old
     * date column to exactly this text, and this is the text that is encrypted and hashed.
     */
    public static final String DOB_PATTERN = "yyyy-MM-dd";

    /** Purpose enc-client writes to the audit topic for every decrypt of an individual's PII. */
    private static final String DECRYPT_PURPOSE = "searchIndividual";

    private final EncryptionDecryptionUtil encUtil;
    private final PiiHasher hasher;
    private final ObjectMapper objectMapper;
    private final IndividualProperties properties;
    private final Set<String> additionalFieldKeys;

    public IndividualPiiCryptoService(EncryptionDecryptionUtil encUtil, PiiHasher hasher,
                                      @Qualifier("objectMapper") ObjectMapper objectMapper,
                                      IndividualProperties properties) {
        this.encUtil = encUtil;
        this.hasher = hasher;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.additionalFieldKeys = new HashSet<>();
        if (properties.getPiiAdditionalFieldKeys() != null) {
            properties.getPiiAdditionalFieldKeys().stream().map(String::trim).filter(StringUtils::isNotBlank)
                    .forEach(additionalFieldKeys::add);
        }
    }

    // ------------------------------------------------------------------ encrypt

    /** Returns stored-form copies; the input objects are not changed. */
    public List<Individual> encrypt(List<Individual> individuals) {
        if (individuals == null || individuals.isEmpty()) {
            return individuals == null ? Collections.emptyList() : individuals;
        }
        List<String> plains = new ArrayList<>();
        List<Consumer<String>> setters = new ArrayList<>();
        List<Individual> stored = new ArrayList<>(individuals.size());
        for (Individual source : individuals) {
            StoredIndividual s = toStored(source);
            collectForEncryption(s, plains, setters);
            stored.add(s);
        }
        List<String> ciphers = encUtil.encryptValues(plains, properties.getPiiEncryptModel());
        for (int i = 0; i < ciphers.size(); i++) {
            setters.get(i).accept(ciphers.get(i));
        }
        return stored;
    }

    private StoredIndividual toStored(Individual source) {
        StoredIndividual s = objectMapper.convertValue(source, StoredIndividual.class);
        // nested lists are converted from the SOURCE so stored sub-objects keep their extra fields
        if (source.getIdentifiers() != null) {
            s.setIdentifiers(source.getIdentifiers().stream()
                    .map(i -> (Identifier) objectMapper.convertValue(i, StoredIdentifier.class))
                    .collect(Collectors.toList()));
        }
        if (s.getUserDetails() != null) {
            // The password is only needed by the user-service call, which runs before this.
            // The stored form never carries it.
            s.getUserDetails().setPassword(null);
        }
        return s;
    }

    private void collectForEncryption(StoredIndividual s, List<String> plains, List<Consumer<String>> setters) {
        Name name = s.getName();
        if (name != null) {
            setHashIfMissing(s::getGivenNameHash, s::setGivenNameHash, PiiHasher.GIVEN_NAME, name.getGivenName());
            addText(name.getGivenName(), name::setGivenName, plains, setters);
            addText(name.getFamilyName(), name::setFamilyName, plains, setters);
            addText(name.getOtherNames(), name::setOtherNames, plains, setters);
        }
        setHashIfMissing(s::getMobileNumberHash, s::setMobileNumberHash, PiiHasher.MOBILE, s.getMobileNumber());
        addText(s.getMobileNumber(), s::setMobileNumber, plains, setters);
        addText(s.getAltContactNumber(), s::setAltContactNumber, plains, setters);
        addText(s.getEmail(), s::setEmail, plains, setters);
        addText(s.getFatherName(), s::setFatherName, plains, setters);
        addText(s.getHusbandName(), s::setHusbandName, plains, setters);

        if (s.getUserDetails() != null) {
            setHashIfMissing(s::getUsernameHash, s::setUsernameHash, PiiHasher.USERNAME, s.getUserDetails().getUsername());
            addText(s.getUserDetails().getUsername(), s.getUserDetails()::setUsername, plains, setters);
        }

        if (s.getDateOfBirth() != null) {
            String dob = formatDob(s.getDateOfBirth());
            s.setDateOfBirth(null);
            plains.add(dob);
            setters.add(s::setDateOfBirthCipher);
        }
        if (s.getGender() != null) {
            String gender = jsonText(s.getGender());
            s.setGender(null);
            plains.add(gender);
            setters.add(s::setGenderCipher);
        }
        if (s.getBloodGroup() != null) {
            String bloodGroup = jsonText(s.getBloodGroup());
            s.setBloodGroup(null);
            plains.add(bloodGroup);
            setters.add(s::setBloodGroupCipher);
        }

        if (s.getIdentifiers() != null) {
            for (Identifier identifier : s.getIdentifiers()) {
                StoredIdentifier si = (StoredIdentifier) identifier;
                setHashIfMissing(si::getIdentifierIdHash, si::setIdentifierIdHash, PiiHasher.IDENTIFIER_ID, si.getIdentifierId());
                addText(si.getIdentifierId(), si::setIdentifierId, plains, setters);
            }
        }

        if (s.getAddress() != null) {
            for (Address a : s.getAddress()) {
                // latitude/longitude stay plain numbers (not encrypted)
                addText(a.getDoorNo(), a::setDoorNo, plains, setters);
                addText(a.getAddressLine1(), a::setAddressLine1, plains, setters);
                addText(a.getAddressLine2(), a::setAddressLine2, plains, setters);
                addText(a.getLandmark(), a::setLandmark, plains, setters);
                addText(a.getCity(), a::setCity, plains, setters);
                addText(a.getPincode(), a::setPincode, plains, setters);
                addText(a.getBuildingName(), a::setBuildingName, plains, setters);
                addText(a.getStreet(), a::setStreet, plains, setters);
            }
        }

        if (s.getAdditionalFields() != null && s.getAdditionalFields().getFields() != null) {
            for (Field field : s.getAdditionalFields().getFields()) {
                if (field != null && additionalFieldKeys.contains(field.getKey())) {
                    addText(field.getValue(), field::setValue, plains, setters);
                }
            }
        }
    }

    /** Blank values stay as they are (nulls stay null); values that are already cipher are not encrypted twice. */
    private static void addText(String value, Consumer<String> setter, List<String> plains, List<Consumer<String>> setters) {
        if (StringUtils.isBlank(value) || CipherText.isCipher(value)) {
            return;
        }
        plains.add(value);
        setters.add(setter);
    }

    private void setHashIfMissing(Supplier<String> getter, Consumer<String> setter, String field, String plainValue) {
        if (getter.get() == null && plainValue != null && !CipherText.isCipher(plainValue)) {
            setter.accept(hasher.hash(field, plainValue));
        }
    }

    // ------------------------------------------------------------------ decrypt

    /** Decrypts in place and returns plain {@link Individual} copies (no stored-form fields). */
    public List<Individual> decrypt(List<Individual> individuals, RequestInfo requestInfo) {
        if (individuals == null || individuals.isEmpty()) {
            return individuals == null ? Collections.emptyList() : individuals;
        }
        ArrayNode nodes = objectMapper.createArrayNode();
        List<Consumer<String>> setters = new ArrayList<>();
        for (Individual individual : individuals) {
            collectForDecryption(individual, nodes, setters);
        }
        if (!nodes.isEmpty()) {
            JsonNode result = encUtil.decryptValueNodes(nodes, properties.getPiiDecryptModel(), requestInfo, DECRYPT_PURPOSE);
            for (int i = 0; i < setters.size(); i++) {
                JsonNode value = result.get(i).get("value");
                setters.get(i).accept(value == null || value.isNull() ? null : value.asText());
            }
        }
        return individuals.stream().map(this::toPlain).collect(Collectors.toList());
    }

    private void collectForDecryption(Individual ind, ArrayNode nodes, List<Consumer<String>> setters) {
        String owner = ind.getId() != null ? ind.getId() : ind.getClientReferenceId();
        Name name = ind.getName();
        if (name != null) {
            addCipher(owner, name.getGivenName(), name::setGivenName, nodes, setters);
            addCipher(owner, name.getFamilyName(), name::setFamilyName, nodes, setters);
            addCipher(owner, name.getOtherNames(), name::setOtherNames, nodes, setters);
        }
        addCipher(owner, ind.getMobileNumber(), ind::setMobileNumber, nodes, setters);
        addCipher(owner, ind.getAltContactNumber(), ind::setAltContactNumber, nodes, setters);
        addCipher(owner, ind.getEmail(), ind::setEmail, nodes, setters);
        addCipher(owner, ind.getFatherName(), ind::setFatherName, nodes, setters);
        addCipher(owner, ind.getHusbandName(), ind::setHusbandName, nodes, setters);
        if (ind.getUserDetails() != null) {
            addCipher(owner, ind.getUserDetails().getUsername(), ind.getUserDetails()::setUsername, nodes, setters);
        }
        if (ind instanceof StoredIndividual) {
            StoredIndividual s = (StoredIndividual) ind;
            addCipher(owner, s.getDateOfBirthCipher(),
                    plain -> s.setDateOfBirth(readDob(plain, objectMapper.getSerializationConfig().getTimeZone(), owner)),
                    nodes, setters);
            addCipher(owner, s.getGenderCipher(),
                    plain -> s.setGender(objectMapper.convertValue(new TextNode(plain), Gender.class)), nodes, setters);
            addCipher(owner, s.getBloodGroupCipher(),
                    plain -> s.setBloodGroup(objectMapper.convertValue(new TextNode(plain), BloodGroup.class)), nodes, setters);
        }
        if (ind.getIdentifiers() != null) {
            for (Identifier identifier : ind.getIdentifiers()) {
                addCipher(owner, identifier.getIdentifierId(), identifier::setIdentifierId, nodes, setters);
            }
        }
        if (ind.getAddress() != null) {
            for (Address a : ind.getAddress()) {
                addCipher(owner, a.getDoorNo(), a::setDoorNo, nodes, setters);
                addCipher(owner, a.getAddressLine1(), a::setAddressLine1, nodes, setters);
                addCipher(owner, a.getAddressLine2(), a::setAddressLine2, nodes, setters);
                addCipher(owner, a.getLandmark(), a::setLandmark, nodes, setters);
                addCipher(owner, a.getCity(), a::setCity, nodes, setters);
                addCipher(owner, a.getPincode(), a::setPincode, nodes, setters);
                addCipher(owner, a.getBuildingName(), a::setBuildingName, nodes, setters);
                addCipher(owner, a.getStreet(), a::setStreet, nodes, setters);
            }
        }
        if (ind.getAdditionalFields() != null && ind.getAdditionalFields().getFields() != null) {
            for (Field field : ind.getAdditionalFields().getFields()) {
                if (field != null) {
                    addCipher(owner, field.getValue(), field::setValue, nodes, setters);
                }
            }
        }
    }

    private void addCipher(String owner, String value, Consumer<String> setter, ArrayNode nodes,
                           List<Consumer<String>> setters) {
        if (!CipherText.isCipher(value)) {
            return; // plain (not yet backfilled) or empty: leave as it is
        }
        ObjectNode node = nodes.addObject();
        node.put("ownerId", owner);
        node.put("value", value);
        setters.add(setter);
    }

    private Individual toPlain(Individual individual) {
        // converting through JSON drops every stored-form field (cipher holders and hashes)
        return objectMapper.convertValue(individual, Individual.class);
    }

    // ------------------------------------------------------------------ helpers

    /** Canonical text of a date of birth; the same text is encrypted and hashed. */
    public String formatDob(Date dateOfBirth) {
        SimpleDateFormat format = new SimpleDateFormat(DOB_PATTERN);
        format.setTimeZone(objectMapper.getSerializationConfig().getTimeZone());
        return format.format(dateOfBirth);
    }

    /** The year may have more than 4 digits: the database changes a date such as 10000-01-01 to that text. */
    private static final java.util.regex.Pattern DOB_PREFIX = java.util.regex.Pattern.compile("^(\\d{4,}-\\d{2}-\\d{2})");

    /**
     * A date of birth that cannot be read is returned empty (with a warning naming the individual, never the value),
     * so one bad row does not fail a whole search page. Before the date column became text this could not happen.
     */
    public static Date readDob(String text, java.util.TimeZone timeZone, String individualId) {
        try {
            return parseDobText(text, timeZone);
        } catch (CustomException e) {
            log.warn("date of birth of individual {} is not a {} date; returned empty", individualId, DOB_PATTERN);
            return null;
        }
    }

    /**
     * Reads a date of birth text: yyyy-MM-dd, or a text that starts with it (a timestamp text written by the old
     * persister config into the TEXT column). Only the date part at the start is used.
     */
    public static Date parseDobText(String text, java.util.TimeZone timeZone) {
        java.util.regex.Matcher m = DOB_PREFIX.matcher(text == null ? "" : text.trim());
        if (!m.find()) {
            throw new CustomException("INDIVIDUAL_DOB_INVALID", "date of birth is not a " + DOB_PATTERN + " date");
        }
        SimpleDateFormat format = new SimpleDateFormat(DOB_PATTERN);
        format.setLenient(false);
        format.setTimeZone(timeZone);
        try {
            return format.parse(m.group(1));
        } catch (ParseException e) {
            throw new CustomException("INDIVIDUAL_DOB_INVALID", "date of birth is not a " + DOB_PATTERN + " date");
        }
    }

    /** JSON text of an enum (its @JsonValue), e.g. "FEMALE" or "B+". */
    public String jsonText(Object value) {
        return objectMapper.valueToTree(value).asText();
    }
}
