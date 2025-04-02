package org.egov.processor.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.egov.processor.util.*;
import org.egov.processor.web.models.*;
import org.egov.processor.web.models.campaignManager.CampaignResponse;
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
        Object campaignSearchResponse = campaignIntegrationUtil.performCampaignSearch(request);
        CampaignResponse campaign = campaignIntegrationUtil.parseCampaignResponse(campaignSearchResponse);
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

    private String getFilestoreIdForPopulationTemplate(PlanConfiguration planConfiguration) {
        return planConfiguration.getFiles().stream()
                .filter(File::getActive)
                .filter(file -> file.getTemplateIdentifier().equalsIgnoreCase(FILE_TEMPLATE_IDENTIFIER_POPULATION))
                .map(File::getFilestoreId)
                .findFirst()
                .orElse(null);
    }


}
