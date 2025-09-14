package org.egov.hrms.utils;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.hrms.config.PropertiesManager;
import org.egov.hrms.model.Employee;
import org.egov.hrms.producer.HRMSProducer;
import org.egov.hrms.repository.RestCallRepository;
import org.egov.hrms.web.contract.Email;
import org.egov.hrms.web.contract.EmailRequest;
import org.egov.hrms.web.contract.EmployeeRequest;
import org.egov.hrms.web.contract.RequestInfoWrapper;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import static org.egov.hrms.utils.HRMSConstants.*;

@Slf4j
@Component
public class NotificationUtil {

    private RestCallRepository restCallRepository;

    private PropertiesManager propertiesManager;

    private HRMSProducer producer;

    public NotificationUtil(RestCallRepository restCallRepository, PropertiesManager propertiesManager, HRMSProducer producer) {
        this.restCallRepository = restCallRepository;
        this.propertiesManager = propertiesManager;
        this.producer = producer;
    }

    /**
     * Extracts message for the specific code from the localization messages.
     *
     * @param notificationCode    The code for which message is required.
     * @param localizationMessage The localization messages.
     * @return message for the specific code.
     */
    public String getMessageTemplate(String notificationCode, String localizationMessage) {
        // Create the path to get the message for the provided notification code from localization messages.
        String path = "$..messages[?(@.code==\"{}\")].message";
        path = path.replace("{}", notificationCode);
        String message = null;
        try {
            // Tries to get the message for the provided notificationCode.
            List data = JsonPath.parse(localizationMessage).read(path);
            if (!CollectionUtils.isEmpty(data))
                message = data.get(0).toString();
            else
                log.error("Fetching from localization failed with code " + notificationCode);
        } catch (Exception e) {
            log.warn("Fetching from localization failed", e);
        }
        return message;
    }

    /**
     * Fetches all the localization messages from localization service.
     *
     * @param employeeRequest The employee request
     * @return Localization messages for the module
     */
    public String getLocalizationMessages(EmployeeRequest employeeRequest) {

        RequestInfoWrapper requestInfoWrapper = new RequestInfoWrapper();
        requestInfoWrapper.setRequestInfo(employeeRequest.getRequestInfo());

        LinkedHashMap responseMap = (LinkedHashMap) restCallRepository.fetchResult(getUri(employeeRequest), requestInfoWrapper);
        return new JSONObject(responseMap).toString();
    }

    /**
     * Returns the search uri for the localization search to get localization messages from "rainmaker-hr" module.
     *
     * @param employeeRequest The employee request with locale code.
     * @return The uri for localization search call.
     */
    public StringBuilder getUri(EmployeeRequest employeeRequest) {
        String tenantId = employeeRequest.getEmployees().get(0).getTenantId().split("\\.")[0];

        String locale = HRMS_LOCALIZATION_ENG_LOCALE_CODE;
        if (!StringUtils.isEmpty(employeeRequest.getRequestInfo().getMsgId()) && employeeRequest.getRequestInfo().getMsgId().split("|").length >= 2)
            locale = employeeRequest.getRequestInfo().getMsgId().split("\\|")[1];

        StringBuilder uri = new StringBuilder();
        uri.append(propertiesManager.getLocalizationHost()).append(propertiesManager.getLocalizationSearchEndpoint())
                .append("?").append("locale=").append(locale).append("&tenantId=").append(tenantId).append("&module=").append(HEALTH_HRMS_LOCALIZATION_MODULE_CODE);

        return uri;
    }

    /**
     * Replaces the placeholders from the email template and creates an email request from the given employee request.
     *
     *
     * @param employeeRequest The employee request
     * @param emailTemplate   the email template
     * @return The list of email requests
     */
    public List<EmailRequest> createEmailRequest(EmployeeRequest employeeRequest, String emailTemplate) {
        RequestInfo requestInfo = employeeRequest.getRequestInfo();

        List<EmailRequest> emailRequest = new LinkedList<>();

        // Iterate over each employee details and create email request for each employee.
        for (Employee employee : employeeRequest.getEmployees()) {
            String customizedMsg = emailTemplate.replace("{User's name}", employee.getUser().getName());
            customizedMsg = customizedMsg.replace("{Username}", employee.getCode());
            customizedMsg = customizedMsg.replace("{Password}", employee.getUser().getPassword());
            customizedMsg = customizedMsg.replace("{website URL}", propertiesManager.getEmailNotificationWebsiteLink());
            customizedMsg = customizedMsg.replace("{Implementation partner}", propertiesManager.getEmailNotificationImplementationPartner());

            // Get email subject and email body from the provided email template.
            String subject = customizedMsg.substring(customizedMsg.indexOf("<h2>")+4, customizedMsg.indexOf("</h2>"));
            String body = customizedMsg.substring(customizedMsg.indexOf("</h2>")+5);

            // Create the email object with the employee's email id, subject and customized email body created.
            Email emailObj = Email.builder().emailTo(Collections.singleton(employee.getUser().getEmailId())).isHTML(true).body(body).subject(subject).build();
            EmailRequest email = new EmailRequest(requestInfo, emailObj);
            emailRequest.add(email);
        }
        return emailRequest;
    }

    /**
     * Pushes email request list into email notification topic if email notification is enabled.
     *
     * @param emailRequestList The list of emailRequests.
     */
    public void sendEmail(List<EmailRequest> emailRequestList) {
        if (propertiesManager.getIsEmailNotificationEnabled()) {
            if (CollectionUtils.isEmpty(emailRequestList)) {
                log.error("No Emails Found!");
            } else {
                // Iterate over each email and push them into emailNotifTopic to send emails.
                for (EmailRequest emailRequest : emailRequestList) {
                    producer.push(propertiesManager.getEmailNotifTopic(), emailRequest);
                    log.info("Email Request -> " + emailRequest.toString());
                    log.info("EMAIL notification sent!");
                }
            }
        }
    }
}
