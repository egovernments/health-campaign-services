package org.egov.processor.service;


import java.util.HashMap;
import java.util.Map;

import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.util.CampaignIntegrationUtil;
import org.egov.processor.web.models.File;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.campaignManager.CampaignSearchRequest;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

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
        Object campaignSearchResponse = performCampaignSearch(planConfigurationRequest);
        processFiles(planConfigurationRequest, planConfiguration, parserMap, campaignSearchResponse);
    }

    /**
     * Performs a campaign search based on the provided plan configuration request.
     * This method builds a campaign search request using the integration utility,
     * fetches the search result from the service request repository, and returns it.
     *
     * @param planConfigurationRequest The request object containing configuration details for the campaign search.
     * @return The response object containing the result of the campaign search.
     */
	private Object performCampaignSearch(PlanConfigurationRequest planConfigurationRequest) {
		CampaignSearchRequest campaignRequest = campaignIntegrationUtil.buildCampaignRequestForSearch(planConfigurationRequest);
        Object campaignSearchResponse = serviceRequestRepository.fetchResult(new StringBuilder(config.getProjectFactoryHostEndPoint()+config.getCampaignIntegrationSearchEndPoint()),
				campaignRequest);
		return campaignSearchResponse;
	}

	/**
	 * Processes files in the plan configuration by parsing active files and skipping inactive ones.
	 * Uses the provided parser map to parse supported file types. If a file type is not supported,
	 * throws an IllegalArgumentException. Skips files with a specific template identifier defined
	 * in ServiceConstants.
	 *
	 * @param planConfigurationRequest The request object containing configuration details.
	 * @param planConfiguration The plan configuration object containing files to process.
	 * @param parserMap A map of supported file types to their respective parsers.
	 * @param campaignSearchResponse The response object from a campaign search operation.
	 */
	private void processFiles(PlanConfigurationRequest planConfigurationRequest, PlanConfiguration planConfiguration,
			Map<File.InputFileTypeEnum, FileParser> parserMap, Object campaignSearchResponse) {
		for (File file : planConfiguration.getFiles()) {
		    if (!file.getActive()) {
		        continue; 
		    }
		    File.InputFileTypeEnum fileType = file.getInputFileType();
		    FileParser parser = parserMap.computeIfAbsent(fileType, ft -> {
                throw new IllegalArgumentException("Unsupported file type: " + ft);
            });
		    if (!ServiceConstants.FILE_TEMPLATE.equalsIgnoreCase(file.getTemplateIdentifier())) {
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

