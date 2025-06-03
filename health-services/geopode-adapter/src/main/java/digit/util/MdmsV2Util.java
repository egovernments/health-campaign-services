package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
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

}
