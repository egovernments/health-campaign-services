package org.egov.processor.util;

import java.math.BigDecimal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.egov.processor.web.models.Assumption;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

@Component
public class CalculationUtil {

    public CalculationUtil() {
    }

    public BigDecimal calculateResult(BigDecimal input, Operation.OperatorEnum operator, BigDecimal assumptionValue) {
        return switch (operator) {
            case PLUS -> input.add(assumptionValue);
            case MINUS -> input.subtract(assumptionValue);
            case SLASH -> input.divide(assumptionValue);
            case STAR -> input.multiply(assumptionValue);
            case PERCENT -> input.remainder(assumptionValue);
            case _U -> input.pow(assumptionValue.intValue());
            default -> throw new CustomException("UNSUPPORTED_OPERATOR", "Unsupported operator: " + operator);
        };
    }

    public void calculateResources(PlanConfiguration planConfiguration, BigDecimal inputAttributeValue) {
        List<Operation> operationList = planConfiguration.getOperations();
        Map<String, BigDecimal> resultMap = new HashMap<>();
        Map<String, BigDecimal> assumptionValueMap = convertAssumptionsToMap(planConfiguration.getAssumptions());

        for (Operation operation : operationList) {
            String input = operation.getInput();
            //if(input == pop)
            //fetchDateFile(attribute) //pop / target

            BigDecimal inputValue = null;
            //TODO how do we decide? - fetch input value based on filetype and templateIdentifier
            //TODO or fetch input value from resultMap

            Operation.OperatorEnum operator = operation.getOperator();
            BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());
            String output = operation.getOutput();

            // Perform calculation based on the operator using the calculateResult method
            BigDecimal result = calculateResult(inputValue, operator, assumptionValue);

            // Store the result in the map with the key as the output
            resultMap.put(output, result);

        }

        // TODO: Use resultMap and enrich resources in plan and emit for workbench
    }

    public Map<String, BigDecimal> convertAssumptionsToMap(List<Assumption> assumptions) {
        Map<String, BigDecimal> assumptionMap = new HashMap<>();
        for (Assumption assumption : assumptions) {
            assumptionMap.put(assumption.getKey(), assumption.getValue());
        }
        return assumptionMap;
    }

}
