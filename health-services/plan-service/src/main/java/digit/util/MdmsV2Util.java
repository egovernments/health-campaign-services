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

    public List<Mdms> fetchMdmsV2Data(RequestInfo requestInfo, String tenantId, String schemaCode)
    {
        StringBuilder uri = getMdmsV2Uri();
        MdmsV2CriteriaReq mdmsV2CriteriaReq = getMdmsV2Request(requestInfo, tenantId, schemaCode);
        MdmsV2CriteriaResponse mdmsV2CriteriaResponse = null;
        try{
            mdmsV2CriteriaResponse = restTemplate.postForObject(uri.toString(), mdmsV2CriteriaReq, MdmsV2CriteriaResponse.class);
        } catch (Exception e)
        {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
        }

        if(ObjectUtils.isEmpty(mdmsV2CriteriaResponse.getMdms()))
        {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE + " - " + tenantId);
            throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE, NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE);
        }

        return mdmsV2CriteriaResponse.getMdms();
    }

    private StringBuilder getMdmsV2Uri()
    {
        StringBuilder uri = new StringBuilder();
        return uri.append(configs.getMdmsHost()).append(configs.getMdmsV2EndPoint());
    }

    private MdmsV2CriteriaReq getMdmsV2Request(RequestInfo requestInfo, String tenantId, String schemaCode)
    {
        MdmsV2Criteria mdmsV2Criteria = MdmsV2Criteria.builder()
                .tenantId(tenantId)
                .schemaCode(schemaCode)
                .limit(configs.getDefaultLimit())
                .offset(configs.getDefaultOffset()).build();

        return MdmsV2CriteriaReq.builder()
                .requestInfo(requestInfo)
                .mdmsV2Criteria(mdmsV2Criteria).build();
    }

}
