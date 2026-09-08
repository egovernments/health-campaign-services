package org.egov.processor.service;

import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.campaignManager.CampaignResponse;

public interface FileParser {

    Object parseFileData(PlanConfigurationRequest planConfigurationRequest , String fileStoreId, CampaignResponse campaignResponse);

   }
