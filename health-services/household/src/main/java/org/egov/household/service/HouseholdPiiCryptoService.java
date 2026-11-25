package org.egov.household.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.pii.CipherText;
import org.egov.common.pii.EncryptionDecryptionUtil;
import org.egov.household.config.HouseholdConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Field-level encryption of a Household's address text fields (doorNo, addressLine1, addressLine2, landmark,
 * city, pincode, buildingName, street). Latitude and longitude stay plain numbers.
 *
 * <p>Encrypt: every address value of a whole batch goes to enc-service in ONE call (type Normal, state-level
 * key). The result is the stored form, which is what Kafka, the persister, the indexer and Redis see. The input
 * objects are never changed: the stored form is a copy.</p>
 *
 * <p>Decrypt: only values that really are cipher text are collected, in ONE enc-client call (policy check and
 * audit). Plain values from rows written before the backfill are left as they are, so mixed old and new data can
 * be read.</p>
 */
@Slf4j
@Service
public class HouseholdPiiCryptoService {

    /** Purpose enc-client writes to the audit topic for every decrypt of a household's address. */
    private static final String DECRYPT_PURPOSE = "searchHousehold";

    private final EncryptionDecryptionUtil encUtil;
    private final ObjectMapper objectMapper;
    private final HouseholdConfiguration configuration;

    public HouseholdPiiCryptoService(EncryptionDecryptionUtil encUtil,
                                     @Qualifier("objectMapper") ObjectMapper objectMapper,
                                     HouseholdConfiguration configuration) {
        this.encUtil = encUtil;
        this.objectMapper = objectMapper;
        this.configuration = configuration;
    }

    // ------------------------------------------------------------------ encrypt

    /** Returns stored-form copies (same order); the input objects are not changed. */
    public List<Household> toStoredForm(List<Household> households) {
        if (households == null || households.isEmpty()) {
            return households == null ? Collections.emptyList() : households;
        }
        List<String> plains = new ArrayList<>();
        List<Consumer<String>> setters = new ArrayList<>();
        List<Household> stored = new ArrayList<>(households.size());
        for (Household source : households) {
            Household copy = objectMapper.convertValue(source, Household.class);
            collectForEncryption(copy.getAddress(), plains, setters);
            stored.add(copy);
        }
        if (!plains.isEmpty()) {
            List<String> ciphers = encUtil.encryptValues(plains, configuration.getPiiEncryptModel());
            for (int i = 0; i < ciphers.size(); i++) {
                setters.get(i).accept(ciphers.get(i));
            }
        }
        return stored;
    }

    private static void collectForEncryption(Address a, List<String> plains, List<Consumer<String>> setters) {
        if (a == null) {
            return;
        }
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

    /** Blank values stay as they are (nulls stay null); values that are already cipher are not encrypted twice. */
    private static void addText(String value, Consumer<String> setter, List<String> plains, List<Consumer<String>> setters) {
        if (StringUtils.isBlank(value) || CipherText.isCipher(value)) {
            return;
        }
        plains.add(value);
        setters.add(setter);
    }

    // ------------------------------------------------------------------ decrypt

    /** Decrypts the address values of the given households in place and returns the same list. */
    public List<Household> decrypt(List<Household> households, RequestInfo requestInfo) {
        if (households == null || households.isEmpty()) {
            return households == null ? Collections.emptyList() : households;
        }
        ArrayNode nodes = objectMapper.createArrayNode();
        List<Consumer<String>> setters = new ArrayList<>();
        for (Household household : households) {
            collectForDecryption(household, nodes, setters);
        }
        if (!nodes.isEmpty()) {
            JsonNode result = encUtil.decryptValueNodes(nodes, configuration.getPiiDecryptModel(), requestInfo, DECRYPT_PURPOSE);
            for (int i = 0; i < setters.size(); i++) {
                JsonNode value = result.get(i).get("value");
                setters.get(i).accept(value == null || value.isNull() ? null : value.asText());
            }
        }
        return households;
    }

    private static void collectForDecryption(Household household, ArrayNode nodes, List<Consumer<String>> setters) {
        Address a = household.getAddress();
        if (a == null) {
            return;
        }
        String owner = household.getId() != null ? household.getId() : household.getClientReferenceId();
        addCipher(owner, a.getDoorNo(), a::setDoorNo, nodes, setters);
        addCipher(owner, a.getAddressLine1(), a::setAddressLine1, nodes, setters);
        addCipher(owner, a.getAddressLine2(), a::setAddressLine2, nodes, setters);
        addCipher(owner, a.getLandmark(), a::setLandmark, nodes, setters);
        addCipher(owner, a.getCity(), a::setCity, nodes, setters);
        addCipher(owner, a.getPincode(), a::setPincode, nodes, setters);
        addCipher(owner, a.getBuildingName(), a::setBuildingName, nodes, setters);
        addCipher(owner, a.getStreet(), a::setStreet, nodes, setters);
    }

    private static void addCipher(String owner, String value, Consumer<String> setter, ArrayNode nodes,
                                  List<Consumer<String>> setters) {
        if (!CipherText.isCipher(value)) {
            return; // plain (not yet backfilled) or empty: leave as it is
        }
        ObjectNode node = nodes.addObject();
        node.put("ownerId", owner);
        node.put("value", value);
        setters.add(setter);
    }
}
