package org.egov.workerregistry.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.workerregistry.constants.WorkerRegistryConstants;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(3)
@Slf4j
public class WPaymentProviderValidator implements Validator<WorkerBulkRequest, Worker> {

    private static final Pattern ALPHANUMERIC = Pattern.compile("^[A-Za-z0-9]{1,35}$");
    private static final Pattern NUMERIC_10 = Pattern.compile("^[0-9]{10}$");
    private static final Pattern BANK_CODE = Pattern.compile("^$|^[0-9]{3}$|^[0-9]{9}$");

    @Override
    public Map<Worker, List<Error>> validate(WorkerBulkRequest request) {
        Map<Worker, List<Error>> errorDetailsMap = new HashMap<>();
        if (CollectionUtils.isEmpty(request.getWorkers())) return errorDetailsMap;

        request.getWorkers().stream()
                .filter(notHavingErrors())
                .forEach(worker -> {
                    String provider = worker.getPaymentProvider();
                    if (!StringUtils.hasText(provider)) return;

                    if (WorkerRegistryConstants.PAYMENT_PROVIDER_BANK.equals(provider)) {
                        validateBankFields(worker, errorDetailsMap);
                    } else if (WorkerRegistryConstants.PAYMENT_PROVIDER_MTN.equals(provider)) {
                        validateMtnFields(worker, errorDetailsMap);
                    }
                });

        return errorDetailsMap;
    }

    private void validateBankFields(Worker worker, Map<Worker, List<Error>> errorDetailsMap) {
        if (!StringUtils.hasText(worker.getPayeeName())) {
            addError(worker, WorkerRegistryConstants.MISSING_PAYEE_NAME,
                    "Payee Name is required for Bank payment provider", errorDetailsMap);
        }
        if (!StringUtils.hasText(worker.getBeneficiaryCode())) {
            addError(worker, WorkerRegistryConstants.MISSING_BENEFICIARY_CODE,
                    "Beneficiary Code is required for Bank payment provider", errorDetailsMap);
        } else if (!ALPHANUMERIC.matcher(worker.getBeneficiaryCode()).matches()) {
            addError(worker, WorkerRegistryConstants.MISSING_BENEFICIARY_CODE,
                    "Beneficiary Code must be alphanumeric and max 35 characters", errorDetailsMap);
        }
        if (!StringUtils.hasText(worker.getBankAccount())) {
            addError(worker, WorkerRegistryConstants.MISSING_BANK_ACCOUNT,
                    "Bank Account is required for Bank payment provider", errorDetailsMap);
        } else if (!NUMERIC_10.matcher(worker.getBankAccount()).matches()) {
            addError(worker, WorkerRegistryConstants.INVALID_BANK_ACCOUNT,
                    "Bank Account must be exactly 10 digits", errorDetailsMap);
        }
        String bankCode = worker.getBankCode() != null ? worker.getBankCode() : "";
        if (!BANK_CODE.matcher(bankCode).matches()) {
            addError(worker, WorkerRegistryConstants.INVALID_BANK_CODE,
                    "Bank Code must be blank, 3 digits, or 9 digits", errorDetailsMap);
        }
    }

    private void validateMtnFields(Worker worker, Map<Worker, List<Error>> errorDetailsMap) {
        if (!StringUtils.hasText(worker.getPayeePhoneNumber())) {
            addError(worker, WorkerRegistryConstants.MISSING_PAYEE_PHONE_NUMBER,
                    "Payee Phone Number is required for MTN payment provider", errorDetailsMap);
        }
        if (!StringUtils.hasText(worker.getPayeeName())) {
            addError(worker, WorkerRegistryConstants.MISSING_PAYEE_NAME,
                    "Payee Name is required for MTN payment provider", errorDetailsMap);
        }
    }

    private void addError(Worker worker, String code, String message, Map<Worker, List<Error>> errorDetailsMap) {
        Error error = Error.builder()
                .errorMessage(message)
                .errorCode(code)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .build();
        populateErrorDetails(worker, error, errorDetailsMap);
    }
}
