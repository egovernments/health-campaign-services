package org.egov.individual.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.egov.common.models.individual.AdditionalFields;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.AddressType;
import org.egov.common.models.individual.BloodGroup;
import org.egov.common.models.individual.Boundary;
import org.egov.common.models.individual.Gender;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.Name;
import org.egov.common.models.individual.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import digit.models.coremodels.AuditDetails;
import digit.models.coremodels.user.enums.UserType;

@Component
public class IndividualResultSetExtractor implements ResultSetExtractor<List<Individual>> {

	@Autowired
    private ObjectMapper objectMapper;

    @Override
	public List<Individual> extractData(ResultSet rs) throws SQLException, DataAccessException {

		Map<String, Individual> individualIdMap = new LinkedHashMap<>();
		
		while(rs.next()) {
			
			String IndUUID = rs.getString("iid");
			Individual currentInd = individualIdMap.get(IndUUID);
			
			if(null == currentInd) {
				
				Address address = getAdress(rs, IndUUID);
				
				Individual individual = getIndividual(rs, IndUUID);
				
				Identifier identifier = getIdentifier(rs);
				
				individual.setAddress(Stream.of(address).collect(Collectors.toList()));
				
				individual.setIdentifiers(Stream.of(identifier).collect(Collectors.toList()));
				
			}
			individualIdMap.put(IndUUID, currentInd);
		}
		return new ArrayList<>(individualIdMap.values());
	}


    private Individual getIndividual (ResultSet resultSet, String indUUID) throws SQLException {
    	
    	
        String tenantId = resultSet.getString("itenantId");
        
        try {
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(resultSet.getString("icreatedBy"))
                .lastModifiedBy(resultSet.getString("ilastModifiedBy"))
                .createdTime(resultSet.getLong("icreatedTime"))
                .lastModifiedTime(resultSet.getLong("ilastModifiedTime"))
                .build();
        AuditDetails clientAuditDetails = AuditDetails.builder()
                .createdTime(resultSet.getLong("clientCreatedTime"))
                .createdBy(resultSet.getString("clientCreatedBy"))
                .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                .build();
        
        return Individual.builder().id(indUUID)
                .individualId(resultSet.getString("iindividualid"))
                .userId(resultSet.getString("userId"))
                .clientReferenceId(resultSet.getString("iclientReferenceId"))
                .tenantId(tenantId)
                .name(Name.builder().givenName(resultSet.getString("givenName"))
                        .familyName(resultSet.getString("familyName"))
                        .otherNames(resultSet.getString("otherNames")).build())
                .dateOfBirth(resultSet.getDate("dateOfBirth") != null ?
                        resultSet.getDate("dateOfBirth"): null)
                .gender(Gender.fromValue(resultSet.getString("gender")))
                .bloodGroup(BloodGroup.fromValue(resultSet.getString("bloodGroup")))
                .mobileNumber(resultSet.getString("mobileNumber"))
                .altContactNumber(resultSet.getString("altContactNumber"))
                .email(resultSet.getString("email"))
                .fatherName(resultSet.getString("fatherName"))
                .husbandName(resultSet.getString("husbandName"))
                .relationship(resultSet.getString("relationship"))
                .photo(resultSet.getString("photo"))
                .additionalFields(resultSet.getString("additionalDetails") == null ? null :
                        objectMapper.readValue(resultSet.getString("additionalDetails"),
                                AdditionalFields.class)
                )
                .auditDetails(auditDetails)
                .rowVersion(resultSet.getInt("rowVersion"))
                .isDeleted(resultSet.getBoolean("iisDeleted"))
                .isSystemUser(resultSet.getBoolean("isSystemUser"))
                .isSystemUserActive(resultSet.getBoolean("isSystemUserActive"))
                .userDetails(UserDetails.builder()
                        .username(resultSet.getString("username"))
                        .password(resultSet.getString("password"))
                        .userType(UserType.fromValue(resultSet.getString("itype")))
                        .roles(resultSet.getString("roles") == null ? null :
                                objectMapper.readValue(resultSet.getString("roles"),
                                        List.class))
                        .tenantId(tenantId)
                        .build())
                .userUuid(resultSet.getString("userUuid"))
                .clientAuditDetails(clientAuditDetails)
                .build();
    } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
    }
    }

    private Address getAdress (ResultSet resultSet, String IndUUID) throws SQLException {
    	
    	return Address.builder()
        .id(resultSet.getString("aid"))
        .individualId(IndUUID)
        .clientReferenceId(resultSet.getString("aclientReferenceId"))
        .tenantId(resultSet.getString("atenantId"))
        .doorNo(resultSet.getString("adoorNo"))
        .latitude(resultSet.getDouble("alatitude"))
        .longitude(resultSet.getDouble("alongitude"))
        .locationAccuracy(resultSet.getDouble("alocationAccuracy"))
        .type(AddressType.fromValue(resultSet.getString("atype")))
        .addressLine1(resultSet.getString("aaddressLine1"))
        .addressLine2(resultSet.getString("aaddressLine2"))
        .landmark(resultSet.getString("alandmark"))
        .city(resultSet.getString("acity"))
        .pincode(resultSet.getString("apinCode"))
        .buildingName(resultSet.getString("abuildingName"))
        .street(resultSet.getString("astreet"))
        .locality(resultSet.getString("alocalityCode") != null ? Boundary.builder().code(resultSet.getString("alocalityCode")).build() : null)
        .ward(resultSet.getString("awardCode") != null ? Boundary.builder().code(resultSet.getString("awardCode")).build() : null)
        .auditDetails(AuditDetails.builder().createdBy(resultSet.getString("icreatedBy"))
                .lastModifiedBy(resultSet.getString("ilastModifiedBy"))
                .createdTime(resultSet.getLong("icreatedTime"))
                .lastModifiedTime(resultSet.getLong("ilastModifiedTime")).build())
        .isDeleted(resultSet.getBoolean("iisDeleted"))
                .build();
    }

	private Identifier getIdentifier (ResultSet resultSet) throws SQLException {
		
		return Identifier.builder()
                .id(resultSet.getString("iiid"))
                .individualId(resultSet.getString("iiindividualId"))
                .clientReferenceId(resultSet.getString("iiclientReferenceId"))
                .identifierType(resultSet.getString("iiidentifierType"))
                .identifierId(resultSet.getString("iiidentifierId"))
                .auditDetails(AuditDetails.builder().createdBy(resultSet.getString("iicreatedBy"))
                        .lastModifiedBy(resultSet.getString("iilastModifiedBy"))
                        .createdTime(resultSet.getLong("iicreatedTime"))
                        .lastModifiedTime(resultSet.getLong("iilastModifiedTime")).build())
                .isDeleted(resultSet.getBoolean("iiisDeleted"))
                .build();
	}

}

