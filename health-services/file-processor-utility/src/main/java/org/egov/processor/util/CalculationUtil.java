package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    public Map<String, BigDecimal> convertAssumptionsToMap(List<Assumption> assumptions) {
        Map<String, BigDecimal> assumptionMap = new HashMap<>();
        for (Assumption assumption : assumptions) {
            assumptionMap.put(assumption.getKey(), assumption.getValue());
        }
        return assumptionMap;
    }

    public void calculateResources(JsonNode jsonNode, List
            <Operation> operations, Map<String, BigDecimal> resultMap,
                                Map<String, String> mappedValues, Map<String, BigDecimal> assumptionValueMap) {

        for (JsonNode feature : jsonNode.get("features")) {
            for (Operation operation : operations) {
                String input = operation.getInput();
                String inputFromMapping = mappedValues.get(input);
                BigDecimal inputValue = getInputValue(resultMap, feature, input, inputFromMapping);

                Operation.OperatorEnum operator = operation.getOperator();
                BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());

                BigDecimal result = calculateResult(inputValue, operator, assumptionValue);

                String output = operation.getOutput();
                resultMap.put(output, result);
                ((ObjectNode) feature.get("properties")).put(output, result);
            }
            //TODO: create corresponding microplan
            System.out.println("Feature ---- > " + feature.toPrettyString());
        }
    }

    private BigDecimal getInputValue(Map<String, BigDecimal> resultMap, JsonNode feature, String input, String inputFromMapping) {
        if (resultMap.containsKey(input)) {
            return resultMap.get(input);
        } else {
            if (feature.get("properties").get(inputFromMapping) != null) {
                return new BigDecimal(String.valueOf(feature.get("properties").get(inputFromMapping)));
            } else {
                throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
            }
        }
    }

}
