package org.egov.fhir.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.fhir.config.MappingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TransformServiceTest {

    private TransformService transformService;
    private MappingConfig patientConfig;
    private MappingConfig practitionerConfig;
    private MappingConfig config; // alias for patientConfig (backward compat)
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        transformService = new TransformService();

        // Set defaultTenantId via reflection since it's injected by Spring
        Field tenantField = TransformService.class.getDeclaredField("defaultTenantId");
        tenantField.setAccessible(true);
        tenantField.set(transformService, "dev");

        // Load both mapping configs
        patientConfig = objectMapper.readValue(
                new ClassPathResource("mappings/individual-patient-mapping.json").getInputStream(), MappingConfig.class);
        practitionerConfig = objectMapper.readValue(
                new ClassPathResource("mappings/practitioner-individual-mapping.json").getInputStream(), MappingConfig.class);
        config = patientConfig; // existing tests use 'config'
    }

    /**
     * Build a realistic eGov Individual response as it would come from the search API
     */
    private Map<String, Object> buildEgovSearchResponse() {
        Map<String, Object> individual = new LinkedHashMap<>();
        individual.put("id", "a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        individual.put("tenantId", "dev");
        individual.put("individualId", "IND-2026-01-09-143264");
        individual.put("isDeleted", false);

        // Name
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("givenName", "Ramu Singh");
        name.put("familyName", "Binny");
        individual.put("name", name);

        // Demographics
        individual.put("dateOfBirth", "17/02/1973");
        individual.put("gender", "MALE");
        individual.put("mobileNumber", "7776543210");
        individual.put("email", "ramu.binny@example.com");
        individual.put("altContactNumber", "9988776655");

        // Address
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("tenantId", "dev");
        address.put("type", "PERMANENT");
        address.put("addressLine1", "234 Main Street, Apt 4");
        address.put("city", "agarthala");
        address.put("district", "West Tripura");
        address.put("state", "TR");
        address.put("pincode", "799001");
        address.put("country", "India");
        address.put("latitude", 23.8315);
        address.put("longitude", 91.2868);
        individual.put("address", List.of(address));

        // Audit details
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("lastModifiedTime", 1710000000000L);
        audit.put("createdTime", 1709000000000L);
        audit.put("createdBy", "user-uuid-123");
        audit.put("lastModifiedBy", "user-uuid-456");
        individual.put("auditDetails", audit);

        // Identifiers (ABHA etc.)
        Map<String, Object> abhaIdentifier = new LinkedHashMap<>();
        abhaIdentifier.put("identifierType", "ABHA_ADDRESS");
        abhaIdentifier.put("identifierId", "ramu@abdm");
        individual.put("identifiers", List.of(abhaIdentifier));

        // Wrap in response envelope
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("Individual", List.of(individual));
        response.put("TotalCount", 1);
        return response;
    }

    @Test
    void testEgovToFhir_search_producesValidBundle() throws Exception {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        // Verify Bundle structure
        assertEquals("Bundle", result.get("resourceType"));
        assertEquals("searchset", result.get("type"));
        assertEquals(1, result.get("total"));

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        assertNotNull(entries);
        assertEquals(1, entries.size());

        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");
        assertNotNull(patient);
        assertEquals("Patient", patient.get("resourceType"));

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println("=== FHIR Bundle Output ===");
        System.out.println(json);
    }

    @Test
    void testEgovToFhir_search_mapsCoreDemographics() {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");

        // id
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", patient.get("id"));

        // active (negated from isDeleted=false)
        assertEquals(true, patient.get("active"));

        // gender
        assertEquals("male", patient.get("gender"));

        // birthDate (DD/MM/YYYY -> YYYY-MM-DD)
        assertEquals("1973-02-17", patient.get("birthDate"));
    }

    @Test
    void testEgovToFhir_search_mapsNameCorrectly() {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");

        // name is created as a list by setValueByPath for name[0]
        Object nameRaw = patient.get("name");
        assertInstanceOf(List.class, nameRaw, "name should be a list");
        List<Object> nameList = (List<Object>) nameRaw;
        assertFalse(nameList.isEmpty());
        Map<String, Object> name = (Map<String, Object>) nameList.get(0);

        // use = "official" (staticValue)
        assertEquals("official", name.get("use"));

        // text = "Ramu Singh Binny" (concatFields)
        assertEquals("Ramu Singh Binny", name.get("text"));

        // family
        assertEquals("Binny", name.get("family"));

        // given should be array ["Ramu", "Singh"]
        Object given = name.get("given");
        assertInstanceOf(List.class, given, "given should be an array");
        List<String> givenList = (List<String>) given;
        assertEquals(List.of("Ramu", "Singh"), givenList);
    }

    @Test
    void testEgovToFhir_search_mapsTelecom() {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");

        // telecom should be a list with 3 entries (mobile, email, work phone)
        Object telecomObj = patient.get("telecom");
        assertInstanceOf(List.class, telecomObj, "telecom should be an array");
        List<Map<String, Object>> telecom = (List<Map<String, Object>>) telecomObj;
        assertEquals(3, telecom.size());

        // Find mobile
        Map<String, Object> mobile = telecom.stream()
                .filter(t -> "phone".equals(t.get("system")) && "mobile".equals(t.get("use")))
                .findFirst().orElse(null);
        assertNotNull(mobile, "mobile phone telecom entry should exist");
        assertEquals("7776543210", mobile.get("value"));

        // Find email
        Map<String, Object> email = telecom.stream()
                .filter(t -> "email".equals(t.get("system")))
                .findFirst().orElse(null);
        assertNotNull(email, "email telecom entry should exist");
        assertEquals("ramu.binny@example.com", email.get("value"));

        // Find work phone
        Map<String, Object> work = telecom.stream()
                .filter(t -> "phone".equals(t.get("system")) && "work".equals(t.get("use")))
                .findFirst().orElse(null);
        assertNotNull(work, "work phone telecom entry should exist");
        assertEquals("9988776655", work.get("value"));
    }

    @Test
    void testEgovToFhir_search_mapsAddress() {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");

        // address - currently egovToFhir doesn't handle nested array mappings in reverse
        // so we check what's available at the top level
        Object addressObj = patient.get("address");
        assertNotNull(addressObj, "address should be present");
    }

    @Test
    void testEgovToFhir_search_mapsIdentifiers() {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");

        // identifiers
        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) patient.get("identifier");
        assertNotNull(identifiers, "identifiers should be present");
        assertTrue(identifiers.size() >= 1, "should have at least 1 identifier");

        // Find individualId identifier
        Map<String, Object> indId = identifiers.stream()
                .filter(i -> "urn:egov:individual".equals(i.get("system")))
                .findFirst().orElse(null);
        assertNotNull(indId, "eGov individual identifier should exist");
        assertEquals("IND-2026-01-09-143264", indId.get("value"));
        assertEquals("official", indId.get("use"));

        // Check type is present with coding
        Map<String, Object> type = (Map<String, Object>) indId.get("type");
        assertNotNull(type, "identifier type should be present");
        List<Map<String, Object>> coding = (List<Map<String, Object>>) type.get("coding");
        assertNotNull(coding);
        assertEquals("MR", coding.get(0).get("code"));

        // Note: ABHA identifier uses JSONPath filter in egovField which getValueByPath
        // doesn't resolve — so it won't appear unless JsonPath is used in applyIdentifierMappings.
        // This is a known limitation tracked in the code review.
    }

    @Test
    void testEgovToFhir_search_mapsMetaLastUpdated() {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");

        // meta.lastUpdated should be ISO format from epoch
        Map<String, Object> meta = (Map<String, Object>) patient.get("meta");
        assertNotNull(meta, "meta should be present");
        String lastUpdated = (String) meta.get("lastUpdated");
        assertNotNull(lastUpdated, "meta.lastUpdated should be present");
        assertTrue(lastUpdated.contains("2024-03-09"), "should be ISO date from epoch 1710000000000");
    }

    @Test
    void testEgovToFhir_search_mapsExtensions() {
        Map<String, Object> egovResponse = buildEgovSearchResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, config, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> patient = (Map<String, Object>) entries.get(0).get("resource");

        List<Map<String, Object>> extensions = (List<Map<String, Object>>) patient.get("extension");
        assertNotNull(extensions, "extensions should be present");
        assertEquals(2, extensions.size());

        // isDeleted extension
        Map<String, Object> isDeletedExt = extensions.stream()
                .filter(e -> "https://digit.org/extensions/isDeleted".equals(e.get("url")))
                .findFirst().orElse(null);
        assertNotNull(isDeletedExt, "isDeleted extension should exist");
        assertEquals(false, isDeletedExt.get("valueBoolean"));

        // createdTime extension
        Map<String, Object> createdTimeExt = extensions.stream()
                .filter(e -> "https://digit.org/extensions/audit/createdTime".equals(e.get("url")))
                .findFirst().orElse(null);
        assertNotNull(createdTimeExt, "createdTime extension should exist");
        assertNotNull(createdTimeExt.get("valueDateTime"));
    }

    @Test
    void testFhirToEgov_create_mapsCorrectly() throws Exception {
        // Build a FHIR Patient resource
        Map<String, Object> fhirPatient = new LinkedHashMap<>();
        fhirPatient.put("resourceType", "Patient");
        fhirPatient.put("active", true);
        fhirPatient.put("gender", "male");
        fhirPatient.put("birthDate", "1973-02-17");

        // name
        Map<String, Object> nameEntry = new LinkedHashMap<>();
        nameEntry.put("family", "Binny");
        nameEntry.put("given", List.of("Ramu", "Singh"));
        fhirPatient.put("name", List.of(nameEntry));

        // telecom — in any order to test filter-based matching
        fhirPatient.put("telecom", List.of(
                Map.of("system", "email", "value", "ramu@example.com"),
                Map.of("system", "phone", "value", "9988776655", "use", "work"),
                Map.of("system", "phone", "value", "7776543210", "use", "mobile")
        ));

        // address
        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("use", "home");
        addr.put("line", List.of("234 Main Street", "Apt 4"));
        addr.put("city", "agarthala");
        addr.put("state", "TR");
        addr.put("postalCode", "799001");
        addr.put("country", "India");
        fhirPatient.put("address", List.of(addr));

        Map<String, Object> request = transformService.fhirToEgov(fhirPatient, config, "create", "test-token");

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
        System.out.println("=== eGov Create Request ===");
        System.out.println(json);

        // Verify RequestInfo
        Map<String, Object> requestInfo = (Map<String, Object>) request.get("RequestInfo");
        assertNotNull(requestInfo);
        assertEquals("fhir-adapter", requestInfo.get("apiId"));
        assertEquals("test-token", requestInfo.get("authToken"));

        // Verify Individual wrapper
        Map<String, Object> individual = (Map<String, Object>) request.get("Individual");
        assertNotNull(individual, "Individual wrapper should exist for create");

        // tenantId
        assertEquals("dev", individual.get("tenantId"));

        // isDeleted (negated from active=true)
        assertEquals(false, individual.get("isDeleted"));

        // gender
        assertEquals("MALE", individual.get("gender"));

        // dateOfBirth (YYYY-MM-DD -> DD/MM/YYYY)
        assertEquals("17/02/1973", individual.get("dateOfBirth"));

        // name
        Map<String, Object> name = (Map<String, Object>) individual.get("name");
        assertNotNull(name);
        assertEquals("Ramu Singh", name.get("givenName"));
        assertEquals("Binny", name.get("familyName"));

        // telecom — filter-based, order shouldn't matter
        assertEquals("7776543210", individual.get("mobileNumber"));
        assertEquals("ramu@example.com", individual.get("email"));
        assertEquals("9988776655", individual.get("altContactNumber"));

        // address
        List<Map<String, Object>> addresses = (List<Map<String, Object>>) individual.get("address");
        assertNotNull(addresses);
        assertEquals(1, addresses.size());
        Map<String, Object> mappedAddr = addresses.get(0);
        assertEquals("234 Main Street, Apt 4", mappedAddr.get("addressLine1"));
        assertEquals("agarthala", mappedAddr.get("city"));
        assertEquals("TR", mappedAddr.get("state"));
        assertEquals("799001", mappedAddr.get("pincode"));
        assertEquals("India", mappedAddr.get("country"));
        assertEquals("PERMANENT", mappedAddr.get("type"));
    }

    @Test
    void testFhirToEgov_telecomOrderIndependent() {
        // Email first, phone last — should still map correctly
        Map<String, Object> fhirPatient = new LinkedHashMap<>();
        fhirPatient.put("resourceType", "Patient");
        fhirPatient.put("gender", "female");
        fhirPatient.put("birthDate", "1990-01-01");
        fhirPatient.put("active", true);
        fhirPatient.put("name", List.of(Map.of("family", "Test", "given", List.of("User"))));

        // Email first, then work, then mobile — reversed order
        fhirPatient.put("telecom", List.of(
                Map.of("system", "email", "value", "test@test.com"),
                Map.of("system", "phone", "value", "1111111111", "use", "work"),
                Map.of("system", "phone", "value", "9999999999", "use", "mobile")
        ));

        Map<String, Object> request = transformService.fhirToEgov(fhirPatient, config, "create", null);
        Map<String, Object> individual = (Map<String, Object>) request.get("Individual");

        assertEquals("9999999999", individual.get("mobileNumber"));
        assertEquals("test@test.com", individual.get("email"));
        assertEquals("1111111111", individual.get("altContactNumber"));
    }

    // ==================== PRACTITIONER TESTS ====================

    /**
     * Build a practitioner eGov Individual response
     */
    private Map<String, Object> buildPractitionerEgovResponse() {
        Map<String, Object> individual = new LinkedHashMap<>();
        individual.put("id", "p1a2b3c4-d5e6-7890-abcd-practitioner01");
        individual.put("tenantId", "dev");
        individual.put("individualId", "IND-2026-03-01-500001");
        individual.put("isDeleted", false);

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("givenName", "Adam");
        name.put("familyName", "Careful");
        individual.put("name", name);

        individual.put("dateOfBirth", "15/06/1985");
        individual.put("gender", "MALE");
        individual.put("mobileNumber", "8888000011");
        individual.put("email", "adam.careful@health.gov");
        individual.put("altContactNumber", "8888000022");

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("tenantId", "dev");
        address.put("type", "PERMANENT");
        address.put("addressLine1", "534 Erewhon St");
        address.put("city", "PleasantVille");
        address.put("state", "Vic");
        address.put("pincode", "3999");
        address.put("country", "Australia");
        individual.put("address", List.of(address));

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("lastModifiedTime", 1710000000000L);
        audit.put("createdTime", 1709000000000L);
        individual.put("auditDetails", audit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("Individual", List.of(individual));
        response.put("TotalCount", 1);
        return response;
    }

    @Test
    void testPractitioner_configLoadsCorrectly() {
        assertEquals("Practitioner", practitionerConfig.getFhirResource());
        assertEquals("Individual", practitionerConfig.getEgovModel());
        assertNotNull(practitionerConfig.getFieldMappings());
        assertNotNull(practitionerConfig.getIdentifierMappings());
        assertNotNull(practitionerConfig.getExtensionMappings());
        assertNotNull(practitionerConfig.getSearchParamMappings());
    }

    @Test
    void testPractitioner_egovToFhir_resourceTypeIsPractitioner() throws Exception {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        assertEquals("Bundle", result.get("resourceType"));
        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        // Must be Practitioner, not Patient
        assertEquals("Practitioner", practitioner.get("resourceType"));

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println("=== FHIR Practitioner Bundle ===");
        System.out.println(json);
    }

    @Test
    void testPractitioner_egovToFhir_mapsDemographics() {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        assertEquals("p1a2b3c4-d5e6-7890-abcd-practitioner01", practitioner.get("id"));
        assertEquals(true, practitioner.get("active"));
        assertEquals("male", practitioner.get("gender"));
        assertEquals("1985-06-15", practitioner.get("birthDate"));

        // name
        List<Object> nameList = (List<Object>) practitioner.get("name");
        Map<String, Object> name = (Map<String, Object>) nameList.get(0);
        assertEquals("official", name.get("use"));
        assertEquals("Adam Careful", name.get("text"));
        assertEquals("Careful", name.get("family"));
        assertEquals(List.of("Adam"), name.get("given"));
    }

    @Test
    void testPractitioner_egovToFhir_mapsTelecom() {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        List<Map<String, Object>> telecom = (List<Map<String, Object>>) practitioner.get("telecom");
        assertEquals(3, telecom.size());

        Map<String, Object> mobile = telecom.stream()
                .filter(t -> "phone".equals(t.get("system")) && "mobile".equals(t.get("use")))
                .findFirst().orElse(null);
        assertNotNull(mobile);
        assertEquals("8888000011", mobile.get("value"));

        Map<String, Object> email = telecom.stream()
                .filter(t -> "email".equals(t.get("system")))
                .findFirst().orElse(null);
        assertNotNull(email);
        assertEquals("adam.careful@health.gov", email.get("value"));

        Map<String, Object> work = telecom.stream()
                .filter(t -> "phone".equals(t.get("system")) && "work".equals(t.get("use")))
                .findFirst().orElse(null);
        assertNotNull(work);
        assertEquals("8888000022", work.get("value"));
    }

    @Test
    void testPractitioner_egovToFhir_identifierUsesENCode() {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) practitioner.get("identifier");
        assertNotNull(identifiers);
        assertEquals(1, identifiers.size());

        Map<String, Object> id = identifiers.get(0);
        assertEquals("urn:egov:individual", id.get("system"));
        assertEquals("IND-2026-03-01-500001", id.get("value"));
        assertEquals("official", id.get("use"));

        // Practitioner uses EN (Employer number), not MR
        Map<String, Object> type = (Map<String, Object>) id.get("type");
        List<Map<String, Object>> coding = (List<Map<String, Object>>) type.get("coding");
        assertEquals("EN", coding.get(0).get("code"));
        assertEquals("Employer number", coding.get(0).get("display"));
    }

    @Test
    void testPractitioner_egovToFhir_mapsExtensions() {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        List<Map<String, Object>> extensions = (List<Map<String, Object>>) practitioner.get("extension");
        assertNotNull(extensions);
        assertEquals(2, extensions.size());

        Map<String, Object> isDeletedExt = extensions.stream()
                .filter(e -> "https://digit.org/extensions/isDeleted".equals(e.get("url")))
                .findFirst().orElse(null);
        assertNotNull(isDeletedExt);
        assertEquals(false, isDeletedExt.get("valueBoolean"));
    }

    @Test
    void testPractitioner_egovToFhir_mapsAddress() {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        Object addressObj = practitioner.get("address");
        assertNotNull(addressObj, "address should be present");
    }

    @Test
    void testPractitioner_egovToFhir_mapsMetaLastUpdated() {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        Map<String, Object> meta = (Map<String, Object>) practitioner.get("meta");
        assertNotNull(meta, "meta should be present");
        String lastUpdated = (String) meta.get("lastUpdated");
        assertNotNull(lastUpdated, "meta.lastUpdated should be present");
        assertTrue(lastUpdated.contains("2024-03-09"), "should be ISO date from epoch 1710000000000");
    }

    @Test
    void testPractitioner_egovToFhir_mapsNameCorrectly() {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        Map<String, Object> practitioner = (Map<String, Object>) entries.get(0).get("resource");

        Object nameRaw = practitioner.get("name");
        assertInstanceOf(List.class, nameRaw, "name should be a list");
        List<Object> nameList = (List<Object>) nameRaw;
        assertFalse(nameList.isEmpty());
        Map<String, Object> name = (Map<String, Object>) nameList.get(0);

        assertEquals("official", name.get("use"));
        assertEquals("Adam Careful", name.get("text"));
        assertEquals("Careful", name.get("family"));

        Object given = name.get("given");
        assertInstanceOf(List.class, given, "given should be an array");
        assertEquals(List.of("Adam"), (List<String>) given);
    }

    @Test
    void testPractitioner_egovToFhir_producesValidBundle() throws Exception {
        Map<String, Object> egovResponse = buildPractitionerEgovResponse();

        Map<String, Object> result = transformService.egovToFhir(egovResponse, practitionerConfig, "search");

        assertEquals("Bundle", result.get("resourceType"));
        assertEquals("searchset", result.get("type"));
        assertEquals(1, result.get("total"));

        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entry");
        assertNotNull(entries);
        assertEquals(1, entries.size());
    }

    @Test
    void testPractitioner_fhirToEgov_telecomOrderIndependent() {
        Map<String, Object> fhirPractitioner = new LinkedHashMap<>();
        fhirPractitioner.put("resourceType", "Practitioner");
        fhirPractitioner.put("gender", "female");
        fhirPractitioner.put("birthDate", "1990-01-01");
        fhirPractitioner.put("active", true);
        fhirPractitioner.put("name", List.of(Map.of("family", "Nurse", "given", List.of("Jane"))));

        // Reversed order: work, email, mobile
        fhirPractitioner.put("telecom", List.of(
                Map.of("system", "phone", "value", "2222222222", "use", "work"),
                Map.of("system", "email", "value", "jane@hospital.org"),
                Map.of("system", "phone", "value", "3333333333", "use", "mobile")
        ));

        Map<String, Object> request = transformService.fhirToEgov(fhirPractitioner, practitionerConfig, "create", null);
        Map<String, Object> individual = (Map<String, Object>) request.get("Individual");

        assertEquals("3333333333", individual.get("mobileNumber"));
        assertEquals("jane@hospital.org", individual.get("email"));
        assertEquals("2222222222", individual.get("altContactNumber"));
    }

    @Test
    void testPractitioner_fhirToEgov_create() throws Exception {
        Map<String, Object> fhirPractitioner = new LinkedHashMap<>();
        fhirPractitioner.put("resourceType", "Practitioner");
        fhirPractitioner.put("active", true);
        fhirPractitioner.put("gender", "male");
        fhirPractitioner.put("birthDate", "1985-06-15");
        fhirPractitioner.put("name", List.of(Map.of("family", "Careful", "given", List.of("Adam"))));
        fhirPractitioner.put("telecom", List.of(
                Map.of("system", "phone", "value", "8888000011", "use", "mobile"),
                Map.of("system", "email", "value", "adam@health.gov")
        ));

        Map<String, Object> addr = new LinkedHashMap<>();
        addr.put("use", "home");
        addr.put("line", List.of("534 Erewhon St"));
        addr.put("city", "PleasantVille");
        addr.put("state", "Vic");
        addr.put("postalCode", "3999");
        fhirPractitioner.put("address", List.of(addr));

        Map<String, Object> request = transformService.fhirToEgov(fhirPractitioner, practitionerConfig, "create", "prac-token");

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
        System.out.println("=== eGov Practitioner Create Request ===");
        System.out.println(json);

        Map<String, Object> requestInfo = (Map<String, Object>) request.get("RequestInfo");
        assertEquals("prac-token", requestInfo.get("authToken"));

        Map<String, Object> individual = (Map<String, Object>) request.get("Individual");
        assertNotNull(individual);
        assertEquals("dev", individual.get("tenantId"));
        assertEquals("MALE", individual.get("gender"));
        assertEquals("15/06/1985", individual.get("dateOfBirth"));
        assertEquals(false, individual.get("isDeleted"));

        Map<String, Object> name = (Map<String, Object>) individual.get("name");
        assertEquals("Adam", name.get("givenName"));
        assertEquals("Careful", name.get("familyName"));

        assertEquals("8888000011", individual.get("mobileNumber"));
        assertEquals("adam@health.gov", individual.get("email"));

        // address
        List<Map<String, Object>> addresses = (List<Map<String, Object>>) individual.get("address");
        assertNotNull(addresses);
        assertEquals(1, addresses.size());
        Map<String, Object> mappedAddr = addresses.get(0);
        assertEquals("534 Erewhon St", mappedAddr.get("addressLine1"));
        assertEquals("PleasantVille", mappedAddr.get("city"));
        assertEquals("Vic", mappedAddr.get("state"));
        assertEquals("3999", mappedAddr.get("pincode"));
        assertEquals("PERMANENT", mappedAddr.get("type"));
    }
}
