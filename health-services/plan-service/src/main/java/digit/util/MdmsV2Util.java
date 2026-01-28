package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;
import digit.web.models.mdmsV2.*;

import java.util.*;

import static digit.config.ErrorConstants.*;

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

    public List<Mdms> fetchMdmsV2Data(MdmsCriteriaReqV2 mdmsCriteriaReqV2)
    {
        StringBuilder uri = getMdmsV2Uri();
        MdmsResponseV2 mdmsResponseV2 = null;
        try {
            mdmsResponseV2 = restTemplate.postForObject(uri.toString(), mdmsCriteriaReqV2, MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
        }

        if(ObjectUtils.isEmpty(mdmsResponseV2.getMdms()))
        {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE + " - " + mdmsCriteriaReqV2.getMdmsCriteriaV2().getTenantId());
            throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE, NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE);
        }

        return mdmsResponseV2.getMdms();
    }

    private StringBuilder getMdmsV2Uri() {
        StringBuilder uri = new StringBuilder();
        return uri.append(configs.getMdmsHost()).append(configs.getMdmsV2EndPoint());
    }

    public MdmsCriteriaV2 getMdmsCriteriaV2(String tenantId, String schemaCode) {
        return MdmsCriteriaV2.builder()
                .tenantId(tenantId)
                .schemaCode(schemaCode)
                .limit(configs.getDefaultLimit())
                .offset(configs.getDefaultOffset()).build();
    }

}
