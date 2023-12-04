package org.egov.individual.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.models.individual.Individual;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.repository.ServiceRequestRepository;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class IndividualMigrationUtil {
    private static final String INDIVIDUAL_ENCRYPT_KEY = "IndividualEncrypt";
    private final JdbcTemplate jdbcTemplate;
    private final EncryptionDecryptionUtil encryptionDecryptionUtil;
    private final IndividualRepository individualRepository;
    private final IndividualProperties individualProperties;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ObjectMapper objectMapper;
    private static final String INDIVIDUAL_MIGRATION_ERROR = "INDIVIDUAL_MIGRATION_ERROR";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String SECURITY_POLICY_MASTER_NAME = "SecurityPolicy";
    private static final String SECURITY_POLICY_MASTER_FILTER = "[?(@.model == '{}')]";
    private static final String DATA_SECURITY_MASTER_NAME = "DataSecurity";
    @Autowired
    public IndividualMigrationUtil(JdbcTemplate jdbcTemplate, EncryptionDecryptionUtil encryptionDecryptionUtil, IndividualRepository individualRepository, IndividualProperties individualProperties, ServiceRequestRepository serviceRequestRepository, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionDecryptionUtil = encryptionDecryptionUtil;
        this.individualRepository = individualRepository;
        this.individualProperties = individualProperties;
        this.serviceRequestRepository = serviceRequestRepository;
        this.objectMapper = objectMapper;
    }

    public void migrate(){
        log.info("Migrating individual data started");
        String query = "SELECT id,givenname,familyname,othernames,mobilenumber,altcontactnumber,email,fathername,husbandname,username,password from individual";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        for(Map<String,Object> row:rows){
            String id = (String)row.get("id");
            String encryptionQuery = "Select ismigrated from individual_migration_for_encryption where individualid = '"+id+"'";
            List<Map<String, Object>> encryptionRows;
            try {
                encryptionRows = jdbcTemplate.queryForList(encryptionQuery);
            }catch (Exception e){
                throw new CustomException(INDIVIDUAL_MIGRATION_ERROR,"Error while fetching individual encryption status with id: "+id);
            }
            if(!(encryptionRows.isEmpty() ||encryptionRows.get(0).get("ismigrated").equals(false)))
                continue;

            String addressQuery = "Select address.doorno,address.street,address.id From address INNER JOIN individual_address ON address.id = individual_address.addressid where individual_address.individualid = '"+id+"'";
            String indentifierQuery = "Select identifiertype,identifierid From individual_identifier where individual_identifier.individualid = '"+id+"'";
            List<Map<String, Object>> addressRows;
            List<Map<String, Object>> identifierRows;
            try {
                addressRows = jdbcTemplate.queryForList(addressQuery);
                identifierRows = jdbcTemplate.queryForList(indentifierQuery);
            }catch (Exception e){
                throw new CustomException(INDIVIDUAL_MIGRATION_ERROR,"Error while fetching individual address and identifier with id: "+id);
            }
            Object encryptionObject = prepareEncryptObject(row,addressRows,identifierRows);
            Individual encryptedObject = encryptionDecryptionUtil.encryptObject(encryptionObject,INDIVIDUAL_ENCRYPT_KEY,Individual.class);
            BigInteger migratedTime = BigInteger.valueOf(System.currentTimeMillis());
            String updateQuery = "UPDATE individual SET givenname = '"+encryptedObject.getName().getGivenName()+"',familyname ='"+encryptedObject.getName().getFamilyName()+"',othernames = '"+encryptedObject.getName().getOtherNames()+"',altcontactnumber ='"+encryptedObject.getAltContactNumber()+"',email ='"+encryptedObject.getEmail()+"',fathername ='"+encryptedObject.getFatherName()+"',husbandname ='"+encryptedObject.getHusbandName()+"',username ='"+encryptedObject.getUserDetails().getUsername()+"',password ='"+encryptedObject.getUserDetails().getPassword()+"' WHERE id ='"+id+"'";
            String updateAddressQuery = "UPDATE address SET doorno = '"+encryptedObject.getAddress().get(0).getDoorNo()+"',street = '"+encryptedObject.getAddress().get(0).getStreet()+"' WHERE id = '"+encryptedObject.getAddress().get(0).getId()+"'";
            String updateEncryptedQuery = "INSERT INTO individual_migration_for_encryption(individualid,ismigrated,migratedtime) VALUES('"+id+"',true,'"+migratedTime+"')";
            try {
                jdbcTemplate.update(updateQuery);
                jdbcTemplate.update(updateAddressQuery);
                jdbcTemplate.update(updateEncryptedQuery);
            }catch (Exception e){
                throw new CustomException(INDIVIDUAL_MIGRATION_ERROR,"Error while migrating individual with id: "+id);
            }
        }
        log.info("Migrating individual data completed");
    }
    private Object prepareEncryptObject(Map<String,Object> individual,List<Map<String,Object>> address, List<Map<String,Object>> identifier){
        String username = (String)individual.get(USERNAME);
        String password = (String) individual.get(PASSWORD);
        String mobileNumber = (String)individual.get("mobilenumber");
        String altContactNumber = (String)individual.get("altcontactnumber");
        String fatherName = (String)individual.get("fathername");
        String husbandName = (String)individual.get("husbandname");
        individual.put("mobileNumber",mobileNumber);
        individual.put("altContactNumber",altContactNumber);
        individual.put("fatherName",fatherName);
        individual.put("husbandName",husbandName);

        Map<String,Object> nameMap = new HashMap<>();

        identifier.get(0).put("identifierId",identifier.get(0).get("identifierid"));
        address.get(0).put("doorNo",address.get(0).get("doorno"));

        individual.put("address", address);
        individual.put("identifiers", identifier);

        Map<String,Object> userDetailsMap = new HashMap<>();
        userDetailsMap.put(USERNAME,username);
        userDetailsMap.put(PASSWORD,password);
        individual.remove(USERNAME);
        individual.remove(PASSWORD);

        individual.put("userDetails",userDetailsMap);
        return individual;
    }
    public Object mDMSCall(RequestInfo requestInfo, String tenantId) {
        log.info("MDMSUtils::mDMSCall");
        MdmsCriteriaReq mdmsCriteriaReq = getMDMSRequest(requestInfo, tenantId);
        return serviceRequestRepository.fetchResult(getMdmsSearchUrl(), mdmsCriteriaReq);
    }

    private StringBuilder getMdmsSearchUrl() {
        log.info("MDMSUtils::getMdmsSearchUrl");
        StringBuilder url = new StringBuilder();
        url.append(individualProperties.getMdmsHost()).append(individualProperties.getMdmsEndpoint());
        return url;
    }

    private MdmsCriteriaReq getMDMSRequest(RequestInfo requestInfo, String tenantId) {
        log.info("MDMSUtils::getMDMSRequest");
        MasterDetail securityPolicyMasterDetail = MasterDetail.builder().name(SECURITY_POLICY_MASTER_NAME).filter(
                SECURITY_POLICY_MASTER_FILTER.replace("{}", INDIVIDUAL_ENCRYPT_KEY)).build();
        ModuleDetail moduleDetail = ModuleDetail.builder().masterDetails(Collections.singletonList(securityPolicyMasterDetail)).moduleName(DATA_SECURITY_MASTER_NAME).build();
        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(Collections.singletonList(moduleDetail)).tenantId(tenantId).build();
        return MdmsCriteriaReq.builder().requestInfo(requestInfo).mdmsCriteria(mdmsCriteria).build();
    }

    public void migrate2(RequestInfo requestInfo) {
        log.info("Migrating individual data started");
        String idQuery = "SELECT id from individual";
        List<String> ids = jdbcTemplate.queryForList(idQuery, String.class);
        Object mdmsData = mDMSCall(requestInfo, individualProperties.getStateLevelTenantId());
        Map<String,String> nameJsonPathMap = extractJsonPathMap(mdmsData);
        for (String id : ids) {
            String encryptionQuery = "SELECT ismigrated FROM individual_migration_for_encryption WHERE individualid = ?";
            List<Map<String, Object>> encryptionRows;
            try {
                encryptionRows = jdbcTemplate.queryForList(encryptionQuery, id);
            } catch (Exception e) {
                throw new CustomException(INDIVIDUAL_MIGRATION_ERROR, "Error while fetching individual encryption status with id: " + id);
            }
            if (!(encryptionRows.isEmpty() || encryptionRows.get(0).get("ismigrated").equals(false)))
                continue;
            List<String> idsList = new ArrayList<>();
            idsList.add(id);
            IndividualSearch individualSearch = IndividualSearch.builder().id(idsList).build();
            List<Individual> individual = null;
            individual = individualRepository.find(individualSearch,100,0,"pg.citya", null,false);
            Object hiddenObject = extractHiddenObject(individual.get(0), nameJsonPathMap);
            individual.get(0).setHidden(hiddenObject);
            Individual encryptedObject = encryptionDecryptionUtil.encryptObject(individual.get(0), INDIVIDUAL_ENCRYPT_KEY, Individual.class);
            individualRepository.save(Collections.singletonList(encryptedObject),individualProperties.getUpdateIndividualTopic());
            BigInteger migratedTime = BigInteger.valueOf(System.currentTimeMillis());
            String updateEncryptedQuery = "INSERT INTO individual_migration_for_encryption(individualid,ismigrated,migratedtime) VALUES('"+id+"',true,'"+migratedTime+"')";
            try {
                jdbcTemplate.update(updateEncryptedQuery);
            }catch (Exception e){
                throw new CustomException(INDIVIDUAL_MIGRATION_ERROR,"Error while migrating individual with id: "+id);
            }
        }
    }

    public Object extractHiddenObject(Individual individual, Map<String, String> nameJsonPathMap) {
        ObjectNode hiddenObject = JsonNodeFactory.instance.objectNode();
        Object individualObject = objectMapper.convertValue(individual, Object.class);
        for (Map.Entry<String, String> entry : nameJsonPathMap.entrySet()) {
            String attributeName = entry.getKey();
            String jsonPath = "$." + entry.getValue();
            jsonPath = jsonPath.replace("/", ".");
            try {
                Object value = JsonPath.read(individualObject, jsonPath);
                hiddenObject.putPOJO(attributeName, value);
            } catch (Exception e) {
                // Handle the case where the JSONPath is not found in the Individual object
                // You may want to log a warning or handle it based on your requirements
                throw new CustomException(INDIVIDUAL_MIGRATION_ERROR,"Error while fetching json path from individual object");
            }
        }
        return objectMapper.convertValue(hiddenObject, Object.class);
    }

    private JsonNode convertValue(Object value) {
        // Optionally, you can add custom logic here to convert the value if needed
        return objectMapper.convertValue(value, JsonNode.class);
    }

    public Map<String, String> extractJsonPathMap(Object mdmsData) {
        String jsonPathForName = "$.MdmsRes.DataSecurity.SecurityPolicy[*].attributes[*].name";
        String jsonPathForJsonPath = "$.MdmsRes.DataSecurity.SecurityPolicy[*].attributes[*].jsonPath";
        List<String> names;
        List<String> jsonPaths;
        try{
            names = JsonPath.read(mdmsData, jsonPathForName);
            jsonPaths = JsonPath.read(mdmsData, jsonPathForJsonPath);
        }catch (Exception e){
            throw new CustomException(INDIVIDUAL_MIGRATION_ERROR,"Error while fetching json path from mdms data");
        }
        Map<String,String> nameJsonPathMap = new HashMap<>();
        for(int i=0;i<names.size();i++){
            nameJsonPathMap.put(names.get(i),jsonPaths.get(i));
        }
        return nameJsonPathMap;
    }
}
