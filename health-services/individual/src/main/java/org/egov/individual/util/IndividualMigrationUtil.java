package org.egov.individual.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.Individual;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class IndividualMigrationUtil {
    private static final String INDIVIDUAL_ENCRYPT_KEY = "IndividualEncrypt";
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EncryptionDecryptionUtil encryptionDecryptionUtil;
    @Autowired
    private ObjectMapper objectMapper;

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
                throw new CustomException("INDIVIDUAL_MIGRATION_ERROR","Error while fetching individual encryption status with id: "+id);
            }
            if(!(encryptionRows.isEmpty() ||encryptionRows.get(0).get("isencrypted").equals(false)))
                continue;

            String addressQuery = "Select address.doorno,address.street,address.id From address INNER JOIN individual_address ON address.id = individual_address.addressid where individual_address.individualid = '"+id+"'";
            String indentifierQuery = "Select identifiertype,identifierid From individual_identifier where individual_identifier.individualid = '"+id+"'";
            List<Map<String, Object>> addressRows;
            List<Map<String, Object>> identifierRows;
            try {
                addressRows = jdbcTemplate.queryForList(addressQuery);
                identifierRows = jdbcTemplate.queryForList(indentifierQuery);
            }catch (Exception e){
                throw new CustomException("INDIVIDUAL_MIGRATION_ERROR","Error while fetching individual address and identifier with id: "+id);
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
                throw new CustomException("INDIVIDUAL_MIGRATION_ERROR","Error while migrating individual with id: "+id);
            }
        }
        log.info("Migrating individual data completed");
    }
    private Object prepareEncryptObject(Map<String,Object> individual,List<Map<String,Object>> address, List<Map<String,Object>> identifier){
        String givenName = (String)individual.get("givenname");
        String familyName = (String)individual.get("familyname");
        String otherNames = (String)individual.get("othernames");
        String username = (String)individual.get("username");
        String password = (String) individual.get("password");
        String mobileNumber = (String)individual.get("mobilenumber");
        String altContactNumber = (String)individual.get("altcontactnumber");
        String fatherName = (String)individual.get("fathername");
        String husbandName = (String)individual.get("husbandname");
        individual.put("mobileNumber",mobileNumber);
        individual.put("altContactNumber",altContactNumber);
        individual.put("fatherName",fatherName);
        individual.put("husbandName",husbandName);

        Map<String,Object> nameMap = new HashMap<>();
        nameMap.put("givenName",givenName);
        nameMap.put("familyName",familyName);
        nameMap.put("otherNames",otherNames);

        individual.put("name", nameMap);
        individual.remove("givenname");
        individual.remove("familyname");
        individual.remove("othernames");

        identifier.get(0).put("identifierId",identifier.get(0).get("identifierid"));
        address.get(0).put("doorNo",address.get(0).get("doorno"));

        individual.put("address", address);
        individual.put("identifiers", identifier);

        Map<String,Object> userDetailsMap = new HashMap<>();
        userDetailsMap.put("username",username);
        userDetailsMap.put("password",password);
        individual.remove("username");
        individual.remove("password");

        individual.put("userDetails",userDetailsMap);
        return individual;
    }
}
