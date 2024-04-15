package org.egov.processor.service;


import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.util.PlanConfigurationUtil;
import org.egov.processor.web.models.File;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationSearchCriteria;
import org.egov.processor.web.models.PlanConfigurationSearchRequest;
import org.egov.processor.web.models.PlanRequest;
import org.springframework.stereotype.Service;

import static org.egov.processor.web.models.File.InputFileTypeEnum.EXCEL;
import static org.egov.processor.web.models.File.InputFileTypeEnum.GEOJSON;
import static org.egov.processor.web.models.File.InputFileTypeEnum.SHAPEFILE;

@Service
@Slf4j
public class ResourceEstimationService {

    private PlanConfigurationUtil planConfigurationUtil;

    private FileParser parser;

    private ExcelParser excelParser;
    private ShapeFileParser shapeFileParser;
    private GeoJsonParser geoJsonParser;

    public ResourceEstimationService(PlanConfigurationUtil planConfigurationUtil, FileParser parser, ExcelParser excelParser, ShapeFileParser shapeFileParser, GeoJsonParser geoJsonParser) {
        this.planConfigurationUtil = planConfigurationUtil;
        this.parser = parser;
        this.excelParser = excelParser;
        this.shapeFileParser = shapeFileParser;
        this.geoJsonParser = geoJsonParser;
    }

    public void estimateResources(PlanRequest planRequest)
    {
        log.info("Plan Configuration ID - " + planRequest.getPlan().getPlanConfigurationId());
        PlanConfigurationSearchCriteria planConfigurationSearchCriteria = PlanConfigurationSearchCriteria.builder()
                .tenantId(planRequest.getPlan().getTenantId()).id(planRequest.getPlan().getPlanConfigurationId()).build();
        PlanConfigurationSearchRequest planConfigurationSearchRequest = PlanConfigurationSearchRequest.builder().planConfigurationSearchCriteria(planConfigurationSearchCriteria).requestInfo(new RequestInfo()).build();
        List<PlanConfiguration> planConfigurationls = planConfigurationUtil.search(planConfigurationSearchRequest);

        //TODO: based on Filetype call parser
        File.InputFileTypeEnum fileType = planConfigurationls.get(0).getFiles().get(0).getInputFileType();
        FileParser parser;
        if (EXCEL.equals(fileType)) {
            parser = excelParser;
        } else if (SHAPEFILE.equals(fileType)) {
            parser = shapeFileParser;
        } else if (GEOJSON.equals(fileType)) {
            parser = geoJsonParser;
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }

        parser.parseFileData(planRequest.getPlan(), planConfigurationls.get(0));

    }
}
