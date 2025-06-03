package digit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.repository.ServiceRequestRepository;
import digit.web.models.Arcgis.ArcgisResponse;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import digit.web.models.mdmsV2.MdmsCriteriaReqV2;
import digit.web.models.mdmsV2.MdmsCriteriaV2;
import digit.web.models.mdmsV2.MdmsResponseV2;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class ArcgisUtil {

    private Configuration config;

    private RestTemplate restTemplate;

    private MdmsV2Util mdmsV2Util;

    private BoundaryUtil boundaryUtil;

    private ServiceRequestRepository serviceRequestRepository;

    private ObjectMapper mapper;

    public ArcgisUtil(Configuration config, RestTemplate restTemplate, MdmsV2Util mdmsV2Util,BoundaryUtil boundaryUtil,ServiceRequestRepository serviceRequestRepository) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.mdmsV2Util=mdmsV2Util;
        this.boundaryUtil=boundaryUtil;
        this.serviceRequestRepository=serviceRequestRepository;
    }

    /**
     * This method creates root and initializes the children
     *
     * @param request
     * @return
     */
    public BoundaryResponse createRoot(GeopodeBoundaryRequest request) {
        MdmsResponseV2 mdmsResponse = fetchMdmsData(request);
        String countryName = extractCountryNameFromMdms(mdmsResponse, request.getGeopodeBoundary().getISOCode());

        if (countryName.isEmpty()) {
            throw new CustomException("COUNTRY_NAME_NOT_FOUND", "No country found for ISO code: " + request.getGeopodeBoundary().getISOCode());
        }

        serviceRequestRepository.fetchArcGisData(countryName); //TODO: Add geometry in from response (e.g., geometry/rings)

        BoundaryRequest boundaryRequest = boundaryUtil.buildBoundaryRequest(countryName, config.getTenantId(), request);
        return boundaryUtil.sendBoundaryRequest(boundaryRequest);
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
                        .tenantId(config.getTenantId())
                        .schemaCode(config.getSchemaCode())
                        .offset(Integer.parseInt(config.getDefaultOffset()))
                        .limit(Integer.parseInt(config.getDefaultLimit()))
                        .build())
                .build();
    }

    /**
     * This method makes request to mdms
     *
     * @param request
     * @return mapping of isocode-countryName
     */
    private MdmsResponseV2 fetchMdmsData(GeopodeBoundaryRequest request) {
        MdmsCriteriaReqV2 mdmsCriteriaReqV2 = buildMdmsV2RequestForRoot(request);
        String url = config.getMdmsHost() + config.getMdmsV2EndPoint();

        try {
            return restTemplate.postForObject(url, mdmsCriteriaReqV2, MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ISO_CODE, e);
            throw new CustomException("MDMS_FETCH_FAILED", "Failed to fetch MDMS data");
        }
    }

    /**
     * This method is for getting rootName from mdms response
     *
     * @param mdmsResponse
     * @param targetIsoCode
     * @return
     */
    private String extractCountryNameFromMdms(MdmsResponseV2 mdmsResponse, String targetIsoCode) {
        return mdmsResponse.getMdms().stream()
                .map(mdms -> mdms.getData())
                .filter(data -> data != null && data.has(MDMS_ISO_CODE) && targetIsoCode.equalsIgnoreCase(data.get(MDMS_ISO_CODE).asText()))
                .map(data -> data.has(MDMS_NAME) ? data.get(MDMS_NAME).asText() : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_ISO_CODE, "No country found for ISO code: " + targetIsoCode));
    }




}
