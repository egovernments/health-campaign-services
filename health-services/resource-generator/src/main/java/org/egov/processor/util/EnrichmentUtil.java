package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.processor.web.models.LocaleResponse;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.processor.web.models.mdmsV2.Mdms;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class EnrichmentUtil {

    private MdmsV2Util mdmsV2Util;

    private LocaleUtil localeUtil;

    private ParsingUtil parsingUtil;

//    private MultiStateInstanceUtil centralInstanceUtil;

    public EnrichmentUtil(MdmsV2Util mdmsV2Util, LocaleUtil localeUtil, ParsingUtil parsingUtil) {
        this.mdmsV2Util = mdmsV2Util;
        this.localeUtil = localeUtil;
//        this.centralInstanceUtil = centralInstanceUtil;
        this.parsingUtil = parsingUtil;
    }

    /**
     * Enriches the `PlanConfiguration` with resource mappings based on MDMS data and locale messages.
     *
     * @param request The request containing the configuration to enrich.
     * @param localeResponse The response containing locale messages.
     * @param campaignType The campaign type identifier.
     * @param fileStoreId The associated file store ID.
     */
    public void enrichResourceMapping(PlanConfigurationRequest request, LocaleResponse localeResponse, String campaignType, String fileStoreId)
    {
//        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlanConfiguration().getTenantId());
        String rootTenantId = request.getPlanConfiguration().getTenantId().split("\\.")[0];
        String uniqueIndentifier = BOUNDARY + DOT_SEPARATOR  + MICROPLAN_PREFIX + campaignType;
        List<Mdms> mdmsV2Data = mdmsV2Util.fetchMdmsV2Data(request.getRequestInfo(), rootTenantId, MDMS_ADMIN_CONSOLE_MODULE_NAME + DOT_SEPARATOR + MDMS_SCHEMA_ADMIN_SCHEMA, uniqueIndentifier);
        List<String> columnNameList = parsingUtil.extractPropertyNamesFromAdminSchema(mdmsV2Data.get(0).getData());

        List<ResourceMapping> resourceMappingList = new ArrayList<>();
        for(String columnName : columnNameList) {
            ResourceMapping resourceMapping = ResourceMapping
                    .builder()
                    .filestoreId(fileStoreId)
                    .mappedTo(columnName)
                    .active(Boolean.TRUE)
                    .mappedFrom(localeUtil.localeSearch(localeResponse.getMessages(), columnName))
                    .build();
            UUIDEnrichmentUtil.enrichRandomUuid(resourceMapping, "id");
            resourceMappingList.add(resourceMapping);
        }

        //enrich plan configuration with enriched resource mapping list
        request.getPlanConfiguration().setResourceMapping(resourceMappingList);

    }

}
