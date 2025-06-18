package org.egov.processor.service;


import java.math.BigDecimal;
import org.egov.processor.web.models.Plan;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;

public interface FileParser {

    Object parseFileData(PlanConfigurationRequest planConfigurationRequest , String fileStoreId, Object campaignResponse);

   }
