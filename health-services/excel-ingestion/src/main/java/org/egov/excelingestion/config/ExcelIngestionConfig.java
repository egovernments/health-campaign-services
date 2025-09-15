package org.egov.excelingestion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class ExcelIngestionConfig {

    @Value("${egov.boundary.host}")
    private String boundaryHost;

    @Value("${egov.hierarchy.search.url}")
    private String hierarchySearchPath;

    @Value("${egov.boundary.relationship.search.url}")
    private String relationshipSearchPath;

    @Value("${egov.filestore.host}")
    private String filestoreHost;

    @Value("${egov.filestore.upload.endpoint}")
    private String filestoreUploadEndpoint;

    @Value("${egov.filestore.url.endpoint}")
    private String filestoreUrlEndpoint;

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.path}")
    private String mdmsSearchPath;

    @Value("${egov.campaign.host}")
    private String campaignHost;

    @Value("${excel.row.limit:5000}")
    private int excelRowLimit;

    @Value("${default.locale:en_IN}")
    private String defaultLocale;

    @Value("${egov.excel.sheet.password:passwordhere}")
    private String excelSheetPassword;

    @Value("${egov.excel.sheet.zoom:60}")
    private int excelSheetZoom;

    @Value("${egov.excel.validation.error.color:#ff0000}")
    private String validationErrorColor;

    private String defaultHeaderColor = "#93c47d";

    public String getHierarchySearchUrl() {
        return boundaryHost + hierarchySearchPath;
    }

    public String getRelationshipSearchUrl() {
        return boundaryHost + relationshipSearchPath;
    }

    public String getMdmsSearchUrl() {
        return mdmsHost + mdmsSearchPath;
    }
}