package org.egov.processor.service;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.util.CampaignIntegrationUtil;
import org.egov.processor.util.PlanConfigurationUtil;
import org.egov.processor.web.models.File;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.PlanConfigurationSearchCriteria;
import org.egov.processor.web.models.PlanConfigurationSearchRequest;
import org.egov.processor.web.models.PlanRequest;
import org.egov.processor.web.models.campaignManager.CampaignSearchRequest;
import org.springframework.stereotype.Service;

import static org.egov.processor.web.models.File.InputFileTypeEnum.EXCEL;
import static org.egov.processor.web.models.File.InputFileTypeEnum.GEOJSON;
import static org.egov.processor.web.models.File.InputFileTypeEnum.SHAPEFILE;

@Service
@Slf4j
public class ResourceEstimationService {

    private final FileParser excelParser;
    private final FileParser geoJsonParser;
    private final FileParser shapeFileParser;
    private CampaignIntegrationUtil campaignIntegrationUtil;
	private ServiceRequestRepository serviceRequestRepository;
	private Configuration config;

	public ResourceEstimationService(FileParser excelParser, FileParser geoJsonParser, FileParser shapeFileParser,CampaignIntegrationUtil campaignIntegrationUtil
    		,ServiceRequestRepository serviceRequestRepository,
    		Configuration config) {
        this.excelParser = excelParser;
        this.geoJsonParser = geoJsonParser;
        this.shapeFileParser = shapeFileParser;
        this.campaignIntegrationUtil= campaignIntegrationUtil;
    	this.serviceRequestRepository=serviceRequestRepository;
    	this.config=config;
    }

	/**
	 * Estimates resources required for the plan configuration by parsing files and fetching campaign search results.
	 *
	 * @param planConfigurationRequest The plan configuration request containing necessary information for estimating resources.
	 */
    public void estimateResources(PlanConfigurationRequest planConfigurationRequest) {
        PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

        Map<File.InputFileTypeEnum, FileParser> parserMap = getInputFileTypeMap();
        CampaignSearchRequest campaignRequest = campaignIntegrationUtil.buildCampaignRequestForSearch(planConfigurationRequest);
        Object campaignSearchResponse = serviceRequestRepository.fetchResult(new StringBuilder(config.getProjectFactoryHostEndPoint()+config.getCampaignIntegrationSearchEndPoint()),
				campaignRequest);
        for(File file:planConfiguration.getFiles())
        {
            if(file.getActive())
            {
                File.InputFileTypeEnum fileType = file.getInputFileType();
                FileParser parser = parserMap.computeIfAbsent(fileType, ft -> {
                    throw new IllegalArgumentException("Unsupported file type: " + ft);
                });
                parser.parseFileData(planConfigurationRequest, file.getFilestoreId(), campaignSearchResponse);
            }
        }
    }

    /**
     * Retrieves a map of input file types to their respective parsers.
     *
     * @return A map containing input file types as keys and their corresponding parsers as values.
     */
    public Map<File.InputFileTypeEnum, FileParser> getInputFileTypeMap()
    {
        Map<File.InputFileTypeEnum, FileParser> parserMap = new HashMap<>();
        parserMap.put(File.InputFileTypeEnum.EXCEL, excelParser);
        parserMap.put(File.InputFileTypeEnum.SHAPEFILE, shapeFileParser);
        parserMap.put(File.InputFileTypeEnum.GEOJSON, geoJsonParser);

        return parserMap;
    }
}

