package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.web.models.GeopodeBoundaryRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;
import digit.web.models.mdmsV2.*;

import java.util.*;

import static digit.config.ServiceConstants.*;


@Slf4j
@Component
public class MdmsV2Util {

    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    private Configuration configs;

    public MdmsV2Util(RestTemplate restTemplate, ObjectMapper objectMapper, Configuration configs)
    {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.configs = configs;
    }

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
            log.error(ERROR_FETCHING_FROM_MDMS+ " - " + tenantId);
            throw new CustomException(
                    Collections.singletonMap("MDMS", NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ISO_CODE)
            );

        }

        return mdmsResponseV2.getMdms();
    }

    private StringBuilder getMdmsV2Uri()
    {
        StringBuilder uri = new StringBuilder();
        return uri.append(configs.getMdmsHost()).append(configs.getMdmsV2EndPoint());
    }

    private MdmsCriteriaReqV2 getMdmsV2Request(RequestInfo requestInfo, String tenantId, String schemaCode, String uniqueIdentifier)
    {
        MdmsCriteriaV2 mdmsCriteriaV2 = MdmsCriteriaV2.builder()
                .tenantId(tenantId)
                .schemaCode(schemaCode)
                .limit(Integer.parseInt(configs.getDefaultLimit()))
                .offset(Integer.parseInt(configs.getDefaultOffset())).build();

        if(!ObjectUtils.isEmpty(uniqueIdentifier))
            mdmsCriteriaV2.setUniqueIdentifiers(Collections.singletonList(uniqueIdentifier));

        return MdmsCriteriaReqV2.builder()
                .requestInfo(requestInfo)
                .mdmsCriteriaV2(mdmsCriteriaV2).build();
    }

    /**
     * This method makes request to mdms
     *
     * @param request
     * @return mapping of isocode-countryName
     */
    public MdmsResponseV2 fetchMdmsDataForIsoCode(GeopodeBoundaryRequest request) {
        MdmsCriteriaReqV2 mdmsCriteriaReqV2 = buildMdmsV2RequestForRoot(request);
        String url = configs.getMdmsHost() + configs.getMdmsV2EndPoint();

        try {
            return restTemplate.postForObject(url, mdmsCriteriaReqV2, MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ISO_CODE, e);
            throw new CustomException("MDMS_FETCH_FAILED", "Failed to fetch MDMS data");
        }
    }

    /**
     * This method creates request for mdms request for country-name
     *
     * @param request
     * @return
     */
    private MdmsCriteriaReqV2 buildMdmsV2RequestForRoot(GeopodeBoundaryRequest request) {
        return MdmsCriteriaReqV2.builder()
                .requestInfo(request.getRequestInfo())
                .mdmsCriteriaV2(MdmsCriteriaV2.builder()
                        .tenantId(configs.getTenantId())
                        .schemaCode(configs.getSchemaCode())
                        .offset(Integer.parseInt(configs.getDefaultOffset()))
                        .limit(Integer.parseInt(configs.getDefaultLimit()))
                        .build())
                .build();
    }

    /**
     * This method is for getting rootName from mdms response
     *
     * @param mdmsResponse
     * @param targetIsoCode
     * @return
     */
    public String extractCountryNameFromMdms(MdmsResponseV2 mdmsResponse, String targetIsoCode) {
        // targetIsoCode provided in BoundarySetup.isoCode initially by user
        // MdmsResponseV2 is for module.master hcm-microplanning.CountryConfig
        try {
            return mdmsResponse.getMdms().stream()
                    .map(mdms -> mdms.getData()) // Get data node from each MDMS entry
                    .filter(data -> data != null
                            && data.has(MDMS_ISO_CODE)
                            && targetIsoCode.equalsIgnoreCase(data.get(MDMS_ISO_CODE).asText())) // Match ISO code
                    .map(data -> data.has(MDMS_NAME) ? data.get(MDMS_NAME).asText() : null) // Extract name if present
                    .filter(Objects::nonNull) // Filter out nulls
                    .findFirst() // Take the first match
                    .orElseThrow(() -> new CustomException(
                            COUNTRY_NAME_NOT_FOUND,
                            "No country found for ISO code: " + targetIsoCode
                    ));
        } catch (Exception e) {
            // Log technical error and rethrow a clean custom exception
            log.error("Error while extracting country name from MDMS for ISO code: {}", targetIsoCode, e);
            throw new CustomException(
                    "MDMS_EXTRACTION_ERROR",
                    "Failed to extract country name from MDMS for ISO code: " + targetIsoCode
            );
        }
    }

}
