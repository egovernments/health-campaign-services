package org.egov.processor.service;


import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.util.PlanConfigurationUtil;
import org.egov.processor.web.models.File;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
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

    private final PlanConfigurationUtil planConfigurationUtil;
    private final FileParser excelParser;
    private final FileParser geoJsonParser;
    private final FileParser shapeFileParser;

    public ResourceEstimationService(PlanConfigurationUtil planConfigurationUtil, FileParser excelParser, FileParser geoJsonParser, FileParser shapeFileParser) {
        this.planConfigurationUtil = planConfigurationUtil;
        this.excelParser = excelParser;
        this.geoJsonParser = geoJsonParser;
        this.shapeFileParser = shapeFileParser;
    }

    public void estimateResources(PlanConfigurationRequest planConfigurationRequest) {
//        log.info("Plan Configuration ID - " + planRequest.getPlan().getPlanConfigurationId());
//        PlanConfigurationSearchCriteria planConfigurationSearchCriteria = PlanConfigurationSearchCriteria.builder()
//                .tenantId(planRequest.getPlan().getTenantId()).id(planRequest.getPlan().getPlanConfigurationId()).build();
//        PlanConfigurationSearchRequest planConfigurationSearchRequest = PlanConfigurationSearchRequest.builder().planConfigurationSearchCriteria(planConfigurationSearchCriteria).requestInfo(new RequestInfo()).build();
//        List<PlanConfiguration> planConfigurationls = planConfigurationUtil.search(planConfigurationSearchRequest);
//
        PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();
        // filter by templateIdentifier as pop
        File.InputFileTypeEnum fileType = planConfiguration.getFiles().get(0).getInputFileType();
        FileParser parser;
        if (File.InputFileTypeEnum.EXCEL.equals(fileType)) {
            parser = excelParser;
            log.info("excelParser");
        } else if (File.InputFileTypeEnum.SHAPEFILE.equals(fileType)) {
            parser = shapeFileParser;
            log.info("shapeFileParser");
        } else if (File.InputFileTypeEnum.GEOJSON.equals(fileType)) {
            parser = geoJsonParser;
            log.info("geoJsonParser");

        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }

        parser.parseFileData(planConfiguration, planConfiguration.getFiles().get(0).getFilestoreId());
    }
}

