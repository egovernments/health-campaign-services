package org.egov.egovsurveyservices.repository.rowmapper;

import org.egov.egovsurveyservices.web.models.AuditDetails;
import org.egov.egovsurveyservices.web.models.Question;
import org.egov.egovsurveyservices.web.models.enums.Type;
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
public class QuestionRowMapper implements ResultSetExtractor<List<Question>>{
    public List<Question> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String,Question> questionMap = new LinkedHashMap<>();

        while (rs.next()){
            String uuid = rs.getString("uuid");
            Question question = questionMap.get(uuid);

            if(question == null) {

                AuditDetails auditdetails = AuditDetails.builder()
                        .createdBy(rs.getString("createdby"))
                        .createdTime(rs.getLong("createdtime"))
                        .lastModifiedBy(rs.getString("lastmodifiedby"))
                        .lastModifiedTime(rs.getLong("lastmodifiedtime"))
                        .build();

                question =  Question.builder()
                        .uuid(rs.getString("uuid"))
                        .surveyId(rs.getString("surveyid"))
                        .questionStatement(rs.getString("questionstatement"))
                        .status(rs.getString("status"))
                        .required(rs.getBoolean("required"))
                        .options(Arrays.asList(rs.getString("options").split(",")))
                        .type(Type.fromValue(rs.getString("type")))
                        .alert(rs.getString("alert"))
                        .alertOnOption(rs.getString("alertOnOption"))
                        .reasonOnOption(rs.getString("reasonOnOption"))
                        .auditDetails(auditdetails)
                        .build();
            }
            questionMap.put(uuid, question);
        }
        return new ArrayList<>(questionMap.values());
    }


}
