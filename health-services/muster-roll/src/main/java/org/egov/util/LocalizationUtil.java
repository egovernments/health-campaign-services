package org.egov.util;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.RequestInfoWrapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.repository.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.util.MusterRollServiceConstants.MUSTER_ROLL_MODULE_CODE;

@Component
@Slf4j
public class LocalizationUtil {

    private final MusterRollServiceConfiguration config;

    private final ServiceRequestRepository restRepo;

    @Autowired
    public LocalizationUtil(MusterRollServiceConfiguration config, ServiceRequestRepository restRepo) {
        this.config = config;
        this.restRepo = restRepo;
    }


    /**
     * Creates a cache for localization that gets refreshed at every call.
     *
     * @param requestInfo
     * @param rootTenantId
     * @param locale
     * @param module
     * @return
     */
    public Map<String, Map<String, String>> getLocalisedMessages(RequestInfo requestInfo, String rootTenantId, String locale, String module) {
        Map<String, Map<String, String>> localizedMessageMap = new HashMap<>();
        Map<String, String> mapOfCodesAndMessages = new HashMap<>();
        StringBuilder uri = new StringBuilder();
        RequestInfoWrapper requestInfoWrapper = new RequestInfoWrapper();
        requestInfoWrapper.setRequestInfo(requestInfo);
        uri.append(config.getLocalizationServiceHost())
                .append(config.getLocalizationServiceEndpoint()).append("?tenantId=" + rootTenantId)
                .append("&module=" + module).append("&locale=" + locale);
        List<String> codes = null;
        List<String> messages = null;
        Object result = null;
        try {
            result = restRepo.fetchResult(uri, requestInfoWrapper);
            codes = JsonPath.read(result, MusterRollServiceConstants.MUSTER_ROLL_LOCALIZATION_CODE_JSONPATH);
            messages = JsonPath.read(result, MusterRollServiceConstants.MUSTER_ROLL_LOCALIZATION_MESSAGE_JSONPATH);
        } catch (Exception e) {
            log.error("Exception while fetching from localization: " + e);
        }
        if (null != result) {
            for (int i = 0; i < codes.size(); i++) {
                mapOfCodesAndMessages.put(codes.get(i), messages.get(i));
            }
            localizedMessageMap.put(locale + "|" + rootTenantId, mapOfCodesAndMessages);
        }

        return localizedMessageMap;
    }

}
