package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.processor.config.ServiceConstants;
import org.egov.processor.web.models.Assumption;
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import static org.egov.processor.config.ServiceConstants.PROPERTIES;

@Component
public class CalculationUtil {
	
	private PlanUtil planUtil;
	
	

    public CalculationUtil(PlanUtil planUtil) {
		this.planUtil = planUtil;
	}

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
            case SLASH -> input.divide(assumptionValue,ServiceConstants.DEFAULT_SCALE,RoundingMode.DOWN).setScale(ServiceConstants.DEFAULT_SCALE);
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
        return assumptions.stream().collect(Collectors.toMap(Assumption::getKey, Assumption::getValue));
    }

    /**
     * Calculates resources based on the provided JSON node, list of operations, and assumption values.
     *
     * @param jsonNode           The JSON node containing the data.
     * @param resultMap          The map to store the results.
     * @param mappedValues       The mapped values for inputs.
     * @param assumptionValueMap The assumption values map.
     */
    public void calculateResources(JsonNode jsonNode, PlanConfigurationRequest planConfigurationRequest, Map<String, BigDecimal> resultMap,
                                Map<String, String> mappedValues, Map<String, BigDecimal> assumptionValueMap) {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        for (JsonNode feature : jsonNode.get("features")) {
            for (Operation operation : planConfig.getOperations()) {
                BigDecimal result = calculateResult(operation, feature, mappedValues, assumptionValueMap, resultMap);
                String output = operation.getOutput();
                resultMap.put(output, result);
                ((ObjectNode) feature.get("properties")).put(output, result);
            }
            planUtil.create(planConfigurationRequest,feature,resultMap,mappedValues);
            
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
                try {
                    String cellValue = String.valueOf(feature.get(PROPERTIES).get(columnName));
                    BigDecimal value;
                    // Handle scientific notation
                    if (cellValue.contains(ServiceConstants.SCIENTIFIC_NOTATION_INDICATOR)) {
                        value = new BigDecimal(cellValue);
                    } else {
                        String cleanedValue = cellValue.replaceAll("[^\\d.\\-E]", "");
                        value = new BigDecimal(cleanedValue);
                    }
                    return value;
                } catch (NumberFormatException | NullPointerException e) {
                    return BigDecimal.ZERO;
                }
            } else {
                throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
            }
        }
    }

    /**
     * Calculates a result based on the provided operation and inputs.
     *
     * @param operation The operation object containing details like input, operator, and assumption value.
     * @param feature   The JSON node representing additional features or parameters for calculation.
     * @param mappedValues A map containing mappings for input keys to their corresponding values.
     * @param assumptionValueMap A map containing assumption values referenced by keys.
     * @param resultMap A map to store and update the calculated results.
     * @return The calculated result as a BigDecimal.
     */
    public BigDecimal calculateResult(Operation operation, JsonNode feature, Map<String, String> mappedValues, Map<String, BigDecimal> assumptionValueMap, Map<String, BigDecimal> resultMap)
    {
        String input = operation.getInput();
        String inputFromMapping = mappedValues.get(input);
        BigDecimal inputValue = getInputValueFromJsonFeature(resultMap, feature, operation.getInput(), inputFromMapping);
        BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());
        return calculateOutputValue(inputValue, operation.getOperator(), assumptionValue);
    }

}
