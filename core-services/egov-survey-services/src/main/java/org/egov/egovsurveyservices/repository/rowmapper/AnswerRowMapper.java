package org.egov.egovsurveyservices.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.egovsurveyservices.web.models.AdditionalFields;
import org.egov.egovsurveyservices.web.models.Answer;
import org.egov.egovsurveyservices.web.models.AuditDetails;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnswerRowMapper implements ResultSetExtractor<List<Answer>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<Answer> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String,Answer> answerMap = new LinkedHashMap<>();

        while (rs.next()){
            String uuid = rs.getString("uuid");
            Answer answer = answerMap.get(uuid);

            if(answer == null) {

                Long lastModifiedTime = rs.getLong("lastmodifiedtime");
                if (rs.wasNull()) {
                    lastModifiedTime = null;
                }

                AuditDetails auditdetails = AuditDetails.builder()
                        .createdBy(rs.getString("createdby"))
                        .createdTime(rs.getLong("createdtime"))
                        .lastModifiedBy(rs.getString("lastmodifiedby"))
                        .lastModifiedTime(lastModifiedTime)
                        .build();

                try {
                    answer = Answer.builder()
                            .uuid(rs.getString("uuid"))
                            .questionId(rs.getString("questionid"))
                            .answer(Arrays.asList(rs.getString("answer").split(",")))
                            .citizenId(rs.getString("citizenid"))
                            .mobileNumber(rs.getString("mobilenumber"))
                            .emailId(rs.getString("emailid"))
                            .additionalComments(rs.getString("additionalComments"))
                            .entityId(rs.getString("entityId"))
                            .entityType(rs.getString("entityType"))
                            .additionalFields(rs.getString("additionalDetails") == null ? null :
                                    objectMapper.readValue(rs.getString("additionalDetails"),
                                            AdditionalFields.class))
                            .auditDetails(auditdetails)
                            .build();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

            answerMap.put(uuid, answer);
        }
        return new ArrayList<>(answerMap.values());
    }

}
