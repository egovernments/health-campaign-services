package org.egov.processor.service;


import java.math.BigDecimal;
import org.egov.processor.web.models.Plan;
import org.egov.processor.web.models.PlanConfiguration;

public interface FileParser {

    Object parseFileData(PlanConfiguration planConfig, String fileStoreId);

   }
