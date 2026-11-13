package org.egov.processor.service;


import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.util.CampaignIntegrationUtil;
import org.egov.processor.util.PlanUtil;
import org.egov.processor.web.models.File;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static org.egov.processor.config.ServiceConstants.FILE_TEMPLATE_IDENTIFIER_ESTIMATIONS_IN_PROGRESS;

@Service
@Slf4j
public class ResourceEstimationService {

    private final FileParser excelParser;
    private final FileParser geoJsonParser;
    private final FileParser shapeFileParser;
    private CampaignIntegrationUtil campaignIntegrationUtil;
	private Configuration config;
	private PlanUtil planUtil;

	public ResourceEstimationService(FileParser excelParser, FileParser geoJsonParser, FileParser shapeFileParser, CampaignIntegrationUtil campaignIntegrationUtil
    		, Configuration config, PlanUtil planUtil) {
        this.excelParser = excelParser;
        this.geoJsonParser = geoJsonParser;
        this.shapeFileParser = shapeFileParser;
        this.campaignIntegrationUtil= campaignIntegrationUtil;
    	this.config=config;
        this.planUtil = planUtil;
    }

	/**
	 * Estimates resources required for the plan configuration by parsing files and fetching campaign search results.
	 *
	 * @param planConfigurationRequest The plan configuration request containing necessary information for estimating resources.
	 */
    public void estimateResources(PlanConfigurationRequest planConfigurationRequest) {
        PlanConfiguration planConfiguration = planConfigurationRequest.getPlanConfiguration();

		if(planConfiguration.getStatus().equals(config.getPlanConfigTriggerCensusRecordsStatus())) {
			planUtil.addEstimationsFile(planConfigurationRequest);
			planUtil.update(planConfigurationRequest);
		}
        Map<File.InputFileTypeEnum, FileParser> parserMap = getInputFileTypeMap();
        CampaignResponse campaignSearchResponse = campaignIntegrationUtil.performCampaignSearch(planConfigurationRequest);
		processFacilityFile(planConfigurationRequest, campaignSearchResponse);
		processFiles(planConfigurationRequest, planConfiguration, parserMap, campaignSearchResponse);
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
			Map<File.InputFileTypeEnum, FileParser> parserMap, CampaignResponse campaignSearchResponse) {
		for (File file : planConfiguration.getFiles()) {
		    if (!file.getActive()) {
		        continue; 
		    }
		    File.InputFileTypeEnum fileType = file.getInputFileType();
		    FileParser parser = parserMap.computeIfAbsent(fileType, ft -> {
                throw new IllegalArgumentException("Unsupported file type: " + ft);
            });
		    if (file.getTemplateIdentifier().equalsIgnoreCase(FILE_TEMPLATE_IDENTIFIER_ESTIMATIONS_IN_PROGRESS)) {
		        parser.parseFileData(planConfigurationRequest, file.getFilestoreId(), campaignSearchResponse);
				break;
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

	/**
	 * Processes the facility file by parsing the campaign response and initiating
	 * a data creation call to the Project Factory service.
	 *
	 * @param planConfigurationRequest the request containing plan configuration details
	 * @param campaignResponse the campaign response
	 */
	public void processFacilityFile(PlanConfigurationRequest planConfigurationRequest, CampaignResponse campaignResponse) {
		if (planConfigurationRequest.getPlanConfiguration().getStatus().equals(config.getPlanConfigTriggerPlanFacilityMappingsStatus())) {
			campaignIntegrationUtil.createProjectFactoryDataCall(planConfigurationRequest, campaignResponse);
			log.info("Facility Data creation successful.");
		}
	}

}

