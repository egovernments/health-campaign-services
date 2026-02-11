package org.egov.processor.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.config.Configuration;
import org.egov.processor.web.models.mdmsV2.Mdms;
import org.egov.processor.web.models.mdmsV2.MdmsCriteriaReqV2;
import org.egov.processor.web.models.mdmsV2.MdmsCriteriaV2;
import org.egov.processor.web.models.mdmsV2.MdmsResponseV2;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.egov.processor.config.ErrorConstants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE;
import static org.egov.processor.config.ErrorConstants.NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE;
import static org.egov.processor.config.ServiceConstants.*;

@Slf4j
@Component
public class MdmsV2Util {

    private RestTemplate restTemplate;

    private Configuration config;

    public MdmsV2Util(RestTemplate restTemplate, Configuration config)
    {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Fetches MDMS V2 data based on the provided request information, tenant ID, schema code, and unique identifier.
     *
     * @param requestInfo      The request information object containing metadata about the request.
     * @param tenantId         The tenant ID for which MDMS data is to be retrieved.
     * @param schemaCode       The schema code from where mdms data is to be fetched.
     * @param uniqueIdentifier A unique identifier to filter the MDMS data (optional).
     * @return A list of {@link Mdms} objects containing the fetched MDMS data.
     * @throws CustomException If no MDMS data is found for the given tenant.
     */
    public List<Mdms> fetchMdmsV2Data(RequestInfo requestInfo, String tenantId, String schemaCode, String uniqueIdentifier)
    {
        StringBuilder uri = getMdmsV2Uri();
        MdmsCriteriaReqV2 mdmsCriteriaReqV2 = getMdmsV2Request(requestInfo, tenantId, schemaCode, uniqueIdentifier);
        MdmsResponseV2 mdmsResponseV2 = null;
        try {
            mdmsResponseV2 = restTemplate.postForObject(uri.toString(), mdmsCriteriaReqV2, MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
        }

        if(ObjectUtils.isEmpty(mdmsResponseV2.getMdms()))
        {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE + " - " + tenantId);
            throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE, NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE);
        }

        return mdmsResponseV2.getMdms();
    }

    /**
     * Constructs the MDMS V2 API URI using the configured MDMS host and endpoint.
     *
     * @return StringBuilder containing the constructed MDMS V2 API URI.
     */
    private StringBuilder getMdmsV2Uri()
    {
        StringBuilder uri = new StringBuilder();
        return uri.append(config.getMdmsHost()).append(config.getMdmsV2EndPoint());
    }

    /**
     * Creates an MDMS V2 request object with the specified request parameters.
     *
     * @param requestInfo      The request information object containing metadata about the request.
     * @param tenantId         The tenant ID for which MDMS data is to be retrieved.
     * @param schemaCode       The schema code from where mdms data is to be fetched.
     * @param uniqueIdentifier A unique identifier to filter the MDMS data (optional).
     * @return An instance of {@link MdmsCriteriaReqV2} representing the request payload.
     */
    private MdmsCriteriaReqV2 getMdmsV2Request(RequestInfo requestInfo, String tenantId, String schemaCode, String uniqueIdentifier)
    {
        MdmsCriteriaV2 mdmsCriteriaV2 = MdmsCriteriaV2.builder()
                .tenantId(tenantId)
                .schemaCode(schemaCode)
                .uniqueIdentifiers(uniqueIdentifier != null ? Collections.singletonList(uniqueIdentifier) : null)
                .limit(config.getDefaultLimitForMdms())
                .offset(config.getDefaultOffsetForMdms())
                .build();

        return MdmsCriteriaReqV2.builder()
                .requestInfo(requestInfo)
                .mdmsCriteriaV2(mdmsCriteriaV2).build();
    }

}
