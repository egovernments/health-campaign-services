package digit.repository.rowmapper;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Component
public class PlanStatusCountRowMapper  implements ResultSetExtractor<Map<String, Integer>> {

    @Override
    public Map<String, Integer> extractData(ResultSet rs) throws SQLException, DataAccessException {

        Map<String, Integer> statusCountMap = new HashMap<>();

        while (rs.next()) {
            String status = rs.getString("status");
            Integer statusCount = rs.getInt("plan_status_count");
            statusCountMap.put(status, statusCount);
        }

        return statusCountMap;
    }
}
