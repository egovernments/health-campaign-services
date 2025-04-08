package org.egov.processor.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.egov.processor.util.*;
import org.egov.processor.web.models.*;
import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.processor.config.ServiceConstants.FILE_TEMPLATE_IDENTIFIER_POPULATION;
import static org.egov.processor.web.models.File.InputFileTypeEnum.EXCEL;

@Service
public class DraftService {

    private PlanConfigurationUtil planConfigurationUtil;

    private ExcelParser excelParser;

    private ParsingUtil parsingUtil;

    private PlanUtil planUtil;

    private CampaignIntegrationUtil campaignIntegrationUtil;

    private MdmsUtil mdmsUtil;

    private LocaleUtil localeUtil;

    private EnrichmentUtil enrichmentUtil;

    private OutputEstimationGenerationUtil outputEstimationGenerationUtil;


    public DraftService(PlanConfigurationUtil planConfigurationUtil, ExcelParser excelParser, ParsingUtil parsingUtil, PlanUtil planUtil, CampaignIntegrationUtil campaignIntegrationUtil, MdmsUtil mdmsUtil, LocaleUtil localeUtil, EnrichmentUtil enrichmentUtil, OutputEstimationGenerationUtil outputEstimationGenerationUtil) {
        this.planConfigurationUtil = planConfigurationUtil;
        this.excelParser = excelParser;
        this.parsingUtil = parsingUtil;
        this.planUtil = planUtil;
        this.campaignIntegrationUtil = campaignIntegrationUtil;
        this.mdmsUtil = mdmsUtil;
        this.localeUtil = localeUtil;
        this.enrichmentUtil = enrichmentUtil;
        this.outputEstimationGenerationUtil = outputEstimationGenerationUtil;
    }

    /**
     * Creates a draft plan by retrieving and processing relevant data.
     * - Searches for the plan configuration based on the draft request.
     * - Retrieves and processes the population template file.
     * - Fetches campaign, locale, and MDMS data.
     * - Performs resource mapping enrichment and calculations.
     * - Processes and localizes the workbook before generating the output file.
     * - Uploads the processed file and updates the plan configuration with the new fileStoreId.
     *
     * @param draftRequest the request containing details for draft plan creation.
     */
    @Async
    public void createDraftPlans(DraftRequest draftRequest) {
        // Retrieve Plan Configuration
        List<PlanConfiguration> planConfigurationList = planConfigurationUtil.search(planConfigurationUtil.buildPlanConfigurationSearchRequest(draftRequest));
        PlanConfigurationRequest request = PlanConfigurationRequest.builder()
                .requestInfo(draftRequest.getRequestInfo())
                .planConfiguration(planConfigurationList.get(0))
                .build();

        // Get filestoreId for population template
        String fileStoreId = getFilestoreIdForPopulationTemplate(request.getPlanConfiguration());

        // Retrieve data and perform actions
        Workbook workbook = excelParser.getWorkbookFromFilestoreId(fileStoreId, request.getPlanConfiguration().getTenantId());
        CampaignResponse campaign = campaignIntegrationUtil.performCampaignSearch(request);
        LocaleResponse localeResponse = localeUtil.searchLocale(request);
        Object mdmsData = mdmsUtil.fetchMdmsData(request.getRequestInfo(), request.getPlanConfiguration().getTenantId());

        // Perform operations and enrichment
        planConfigurationUtil.orderPlanConfigurationOperations(request);
        enrichmentUtil.enrichResourceMapping(request, localeResponse, campaign.getCampaign().get(0).getProjectType(), fileStoreId);

        // Prepare data for processing
        Map<String, Object> attributeNameVsDataTypeMap = excelParser.prepareAttributeVsIndexMap(fileStoreId, campaign, request.getPlanConfiguration(), mdmsData);
        List<String> boundaryCodeList = excelParser.getBoundaryCodeList(request, campaign);

        // Fetch common constants mdms data
        Map<String, Object> mdmsDataForCommonConstants = mdmsUtil.fetchMdmsDataForCommonConstants(
                request.getRequestInfo(),
                request.getPlanConfiguration().getTenantId()
        );

        // Process the workbook
        workbook.forEach(excelWorkbookSheet -> {
            if (outputEstimationGenerationUtil.isSheetAllowedToProcess(excelWorkbookSheet.getSheetName(), localeResponse, mdmsDataForCommonConstants)) {
                excelParser.processRows(request, excelWorkbookSheet, fileStoreId, attributeNameVsDataTypeMap, boundaryCodeList, new HashMap<>(), Boolean.TRUE);
            }});

        // Process output file - handle localisation
        outputEstimationGenerationUtil.processDraftOutputFile(workbook, request);

        // Generate output and upload the file
        java.io.File fileToUpload = parsingUtil.convertWorkbookToXls(workbook);
        String uploadedFileStoreId = excelParser.uploadConvertedFile(fileToUpload, request.getPlanConfiguration().getTenantId());

        // Update Plan Configuration request with the uploaded fileStoreId
        planConfigurationUtil.setOrAddFileForDraft(request, uploadedFileStoreId, EXCEL);
        planUtil.update(request);
    }

    /**
     * Retrieves the filestoreId of the active population template file from the given plan configuration.
     *
     * @param planConfiguration the plan configuration containing file details.
     * @return the filestoreId of the active population template, or null if not found.
     */
    private String getFilestoreIdForPopulationTemplate(PlanConfiguration planConfiguration) {
        return planConfiguration.getFiles().stream()
                .filter(File::getActive)
                .filter(file -> file.getTemplateIdentifier().equalsIgnoreCase(FILE_TEMPLATE_IDENTIFIER_POPULATION))
                .map(File::getFilestoreId)
                .findFirst()
                .orElse(null);
    }


}
