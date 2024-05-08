package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.egov.processor.web.models.Assumption;
import org.egov.processor.web.models.Operation;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import static org.egov.processor.config.ServiceConstants.PROPERTIES;

@Component
public class CalculationUtil {

    /**
     * Calculates the output value based on the input value, operator, and assumption value.
     *
     * @param input          The input value.
     * @param operator       The operator enum.
     * @param assumptionValue The assumption value.
     * @return The calculated output.
     */
    public BigDecimal calculateOutputValue(BigDecimal input, Operation.OperatorEnum operator, BigDecimal assumptionValue) {
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

    /**
     * Converts a list of assumptions into a map with assumption keys as keys and assumption values as values.
     *
     * @param assumptions The list of assumptions to convert.
     * @return The map of assumptions.
     */
    public Map<String, BigDecimal> convertAssumptionsToMap(List<Assumption> assumptions) {
        Map<String, BigDecimal> assumptionMap = new HashMap<>();
        for (Assumption assumption : assumptions) {
            assumptionMap.put(assumption.getKey(), assumption.getValue());
        }
        return assumptionMap;
    }

    /**
     * Calculates resources based on the provided JSON node, list of operations, and assumption values.
     *
     * @param jsonNode           The JSON node containing the data.
     * @param operations         The list of operations to perform.
     * @param resultMap          The map to store the results.
     * @param mappedValues       The mapped values for inputs.
     * @param assumptionValueMap The assumption values map.
     */
    public void calculateResources(JsonNode jsonNode, List
            <Operation> operations, Map<String, BigDecimal> resultMap,
                                Map<String, String> mappedValues, Map<String, BigDecimal> assumptionValueMap) {

        for (JsonNode feature : jsonNode.get("features")) {
            for (Operation operation : operations) {
                BigDecimal result = calculateResult(operation, feature, mappedValues, assumptionValueMap, resultMap);
                String output = operation.getOutput();
                resultMap.put(output, result);
                ((ObjectNode) feature.get("properties")).put(output, result);
            }
            //TODO: create corresponding microplan
            System.out.println("Feature ---- > " + feature.toPrettyString());
        }
    }

    /**
     * Retrieves the input value from the JSON node based on the input and input mapping.
     *
     * @param resultMap       The map containing previous results.
     * @param feature         The JSON node feature.
     * @param input           The input key.
     * @param columnName The input from mapping.
     * @return The input value.
     */
    public BigDecimal getInputValueFromJsonFeature(Map<String, BigDecimal> resultMap, JsonNode feature, String input, String columnName) {
        if (resultMap.containsKey(input)) {
            return resultMap.get(input);
        } else {
            if (feature.get(PROPERTIES).get(columnName) != null) {
                return new BigDecimal(String.valueOf(feature.get(PROPERTIES).get(columnName)));
            } else {
                throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
            }
        }
    }

    public BigDecimal calculateResult(Operation operation, JsonNode feature, Map<String, String> mappedValues, Map<String, BigDecimal> assumptionValueMap, Map<String, BigDecimal> resultMap)
    {
        String input = operation.getInput();
        String inputFromMapping = mappedValues.get(input);
        BigDecimal inputValue = getInputValueFromJsonFeature(resultMap, feature, operation.getInput(), inputFromMapping);
        BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());
        return calculateOutputValue(inputValue, operation.getOperator(), assumptionValue);
    }

}
