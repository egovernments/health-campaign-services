package org.egov.processor.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.egov.processor.web.models.Assumption;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.tracer.model.CustomException;

public class CalculationUtil {

    public CalculationUtil() {
    }

    private String calculateResult(BigDecimal input, Operation.OperatorEnum operator, BigDecimal assumptionValue) {
        return switch (operator) {
            case PLUS -> input.add(assumptionValue).toString();
            case MINUS -> input.subtract(assumptionValue).toString();
            case SLASH -> input.divide(assumptionValue, RoundingMode.HALF_UP).toString();
            case STAR -> input.multiply(assumptionValue).toString();
            case PERCENT -> input.remainder(assumptionValue).toString();
            case _U -> input.pow(assumptionValue.intValue()).toString();
            default -> throw new CustomException("UNSUPPORTED_OPERATOR", "Unsupported operator: " + operator);
        };
    }

    private void calculateResources(PlanConfiguration planConfiguration) {
        List<Operation> operationList = planConfiguration.getOperations();
        Map<String, String> resultMap = new HashMap<>();
        Map<String, BigDecimal> assumptionValueMap = convertAssumptionsToMap(planConfiguration.getAssumptions());

        for (Operation operation : operationList) {
            String input = operation.getInput();
            BigDecimal inputValue = null;             //TODO fetch input value based on filetype and templateIdentifier
            //TODO or fetch input value from resultMap

            Operation.OperatorEnum operator = operation.getOperator();
            BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());
            String output = operation.getOutput();

            // Perform calculation based on the operator using the calculateResult method
            String result = calculateResult(inputValue, operator, assumptionValue);

            // Store the result in the map with the key as the output
            resultMap.put(output, result);
        }

        // TODO: Use resultMap and enrich resources in plan and emit for workbench
    }

    private Map<String, BigDecimal> convertAssumptionsToMap(List<Assumption> assumptions) {
        Map<String, BigDecimal> assumptionMap = new HashMap<>();
        for (Assumption assumption : assumptions) {
            assumptionMap.put(assumption.getKey(), assumption.getValue());
        }
        return assumptionMap;
    }

}
