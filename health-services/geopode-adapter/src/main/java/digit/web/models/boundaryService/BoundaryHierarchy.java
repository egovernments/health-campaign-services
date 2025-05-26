package digit.web.models.boundaryService;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoundaryHierarchy {

    private String id;
    private String tenantId;
    private String hierarchyType;

    @JsonProperty("boundaryHierarchy")
    private List<SimpleBoundary> boundaryHierarchy;

    private AuditDetails auditDetails;

    private Object boundaryHierarchyJsonNode;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SimpleBoundary {
        private String boundaryType;
        private String parentBoundaryType;
        private boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuditDetails {
        private String createdBy;
        private String lastModifiedBy;
        private Long createdTime;
        private Long lastModifiedTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResponseInfo {
        private String apiId;
        private String ver;
        private String ts;
        private String resMsgId;
        private String msgId;
        private String status;
    }
}

